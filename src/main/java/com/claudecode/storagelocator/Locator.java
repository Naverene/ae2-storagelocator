package com.claudecode.storagelocator;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;

/** Server side: finds the network of the player's open terminal and every storage on it holding the stack. */
final class Locator {

    private Locator() {}

    private static final int SHOWN = 6;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static List<String> locate(EntityPlayerMP p, LocateRequest req) {
        List<String> out = new ArrayList<>();
        IGrid grid = gridOf(p.openContainer);
        if (grid == null) return out;
        boolean fluids = req.item == null;
        IAEStack want;
        if (fluids) {
            Fluid f = FluidRegistry.getFluid(req.fluid);
            if (f == null) return out;
            want = AEApi.instance()
                .storage()
                .createFluidStack(new FluidStack(f, 1));
        } else {
            ItemStack is = req.item.copy();
            is.stackSize = 1;
            want = AEApi.instance()
                .storage()
                .createItemStack(is);
        }
        if (want == null) return out;
        StorageChannel ch = fluids ? StorageChannel.FLUIDS : StorageChannel.ITEMS;
        World w = p.worldObj;

        Map<String, Long> where = new LinkedHashMap<>();
        collect(grid, want, fluids, ch, w, 0, new java.util.HashSet<>(), "", where);
        if (where.isEmpty()) {
            out.add(EnumChatFormatting.DARK_GRAY + "Not stored on this network");
            return out;
        }
        List<Map.Entry<String, Long>> es = new ArrayList<>(where.entrySet());
        es.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        String unit = fluids ? " L" : "";
        out.add(EnumChatFormatting.GRAY + "Stored in " + es.size() + (es.size() == 1 ? " place:" : " places:"));
        long rest = 0;
        for (int i = 0; i < es.size(); i++) {
            Map.Entry<String, Long> e = es.get(i);
            long v = e.getValue();
            if (i < SHOWN) out.add(
                EnumChatFormatting.DARK_GRAY + " - " + EnumChatFormatting.WHITE
                    + e.getKey()
                    + EnumChatFormatting.GRAY
                    + ": "
                    + EnumChatFormatting.GREEN
                    + nf.format(v)
                    + unit);
            else rest += v;
        }
        if (es.size() > SHOWN) out.add(
            EnumChatFormatting.DARK_GRAY + " + " + (es.size() - SHOWN) + " more: " + nf.format(rest) + unit);
        return out;
    }

    /** Every storage on `grid` holding `want`; drives by cell slot, subnets behind storage buses walked into. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void collect(IGrid grid, IAEStack want, boolean fluids, StorageChannel ch, World w, int depth,
        java.util.Set<IGrid> seen, String suffix, Map<String, Long> where) {
        if (grid == null || depth > 3 || !seen.add(grid)) return;
        for (IGridNode n : grid.getNodes()) {
            Object m = n.getMachine();
            if (!(m instanceof ICellProvider)) continue;
            TileEntity te = tileOf(m);
            // Drives and ME chests: read each cell slot so the label can say which slot.
            if (m instanceof appeng.api.implementations.tiles.IChestOrDrive) {
                Object inv = call(m, "getInternalInventory");
                if (inv instanceof IInventory) {
                    IInventory ii = (IInventory) inv;
                    String holder = te == null ? "ME Drive" : blockName(w, te.xCoord, te.yCoord, te.zCoord);
                    String at = te == null ? "" : " @ " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
                    for (int i = 0; i < ii.getSizeInventory(); i++) {
                        ItemStack cell = ii.getStackInSlot(i);
                        if (cell == null) continue;
                        IMEInventoryHandler h;
                        try {
                            h = AEApi.instance()
                                .registries()
                                .cell()
                                .getCellInventory(cell, null, ch);
                        } catch (RuntimeException | LinkageError e) {
                            continue;
                        }
                        long c = count(h, want, fluids);
                        if (c > 0) where.merge(
                            cell.getDisplayName() + " in " + holder + at + ", slot " + (i + 1) + suffix,
                            c,
                            Long::sum);
                    }
                    continue;
                }
            }
            // A storage bus facing another AE network: look inside that network instead.
            if (m instanceof IPart && te != null) {
                ForgeDirection side = sideOf(m);
                if (side != null && side != ForgeDirection.UNKNOWN) {
                    int fx = te.xCoord + side.offsetX, fy = te.yCoord + side.offsetY, fz = te.zCoord + side.offsetZ;
                    TileEntity ft = w.getTileEntity(fx, fy, fz);
                    if (ft instanceof IGridHost) {
                        IGridNode fn = ((IGridHost) ft).getGridNode(side.getOpposite());
                        if (fn == null) fn = ((IGridHost) ft).getGridNode(ForgeDirection.UNKNOWN);
                        IGrid sub = fn == null ? null : fn.getGrid();
                        if (sub != null && sub != grid) {
                            collect(sub, want, fluids, ch, w, depth + 1, seen, " (subnet)", where);
                            continue;
                        }
                    }
                }
            }
            List<IMEInventoryHandler> hs;
            try {
                hs = ((ICellProvider) m).getCellArray(ch);
            } catch (RuntimeException | LinkageError e) {
                continue;
            }
            if (hs == null) continue;
            int idx = -1;
            for (IMEInventoryHandler h : hs) {
                idx++;
                long c = count(h, want, fluids);
                if (c > 0) where.merge(label(m, te, idx, w, grid) + suffix, c, Long::sum);
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static long count(IMEInventoryHandler h, IAEStack want, boolean fluids) {
        if (h == null) return 0;
        try {
            IItemList l = fluids ? AEApi.instance()
                .storage()
                .createFluidList()
                : AEApi.instance()
                    .storage()
                    .createItemList();
            h.getAvailableItems(l);
            IAEStack hit = l.findPrecise(want);
            return hit == null ? 0 : hit.getStackSize();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** "64k ME Storage Cell in Drive @ x y z", "Drawer Controller @ x y z", "Super Tank I @ x y z (subnet)". */
    private static String label(Object m, TileEntity te, int idx, World w, IGrid grid) {
        String at = te == null ? "" : " @ " + te.xCoord + " " + te.yCoord + " " + te.zCoord;
        if (m instanceof IPart) {
            ForgeDirection side = sideOf(m);
            if (te != null && side != null && side != ForgeDirection.UNKNOWN) {
                int fx = te.xCoord + side.offsetX, fy = te.yCoord + side.offsetY, fz = te.zCoord + side.offsetZ;
                String name = blockName(w, fx, fy, fz);
                boolean subnet = false;
                TileEntity ft = w.getTileEntity(fx, fy, fz);
                if (ft instanceof IGridHost) {
                    IGridNode fn = ((IGridHost) ft).getGridNode(side.getOpposite());
                    if (fn == null) fn = ((IGridHost) ft).getGridNode(ForgeDirection.UNKNOWN);
                    subnet = fn != null && fn.getGrid() != null && fn.getGrid() != grid;
                }
                return name + " @ " + fx + " " + fy + " " + fz + (subnet ? " (subnet)" : "");
            }
            return "Storage Bus" + at;
        }
        String holder = te == null ? m.getClass()
            .getSimpleName() : blockName(w, te.xCoord, te.yCoord, te.zCoord);
        if (te instanceof IInventory && idx >= 0 && idx < ((IInventory) te).getSizeInventory()) {
            ItemStack cell = ((IInventory) te).getStackInSlot(idx);
            if (cell != null) return cell.getDisplayName() + " in " + holder + at;
        }
        return holder + at;
    }

    private static String blockName(World w, int x, int y, int z) {
        try {
            Block b = w.getBlock(x, y, z);
            ItemStack pick = new ItemStack(b, 1, b.getDamageValue(w, x, y, z));
            if (pick.getItem() != null) return pick.getDisplayName();
            return b.getLocalizedName();
        } catch (RuntimeException e) {
            return "?";
        }
    }

    /** The network of the AE GUI the player has open, or null. */
    static IGrid gridOf(Container c) {
        if (c == null) return null;
        IGridNode node = null;
        Object n = call(c, "getNetworkNode");
        if (n instanceof IGridNode) node = (IGridNode) n;
        if (node == null) {
            Object t = call(c, "getTarget");
            if (t instanceof IActionHost) node = ((IActionHost) t).getActionableNode();
            if (node == null && t instanceof IGridHost) node = ((IGridHost) t).getGridNode(ForgeDirection.UNKNOWN);
            if (node == null && t instanceof IPart) node = ((IPart) t).getGridNode();
        }
        return node == null ? null : node.getGrid();
    }

    static TileEntity tileOf(Object m) {
        if (m instanceof IPart) {
            Object te = call(m, "getTile");
            if (te instanceof TileEntity) return (TileEntity) te;
            Object host = call(m, "getHost");
            if (host instanceof IPartHost) return ((IPartHost) host).getTile();
        }
        if (m instanceof TileEntity) return (TileEntity) m;
        return null;
    }

    static ForgeDirection sideOf(Object m) {
        Object s = call(m, "getSide");
        return s instanceof ForgeDirection ? (ForgeDirection) s : null;
    }

    static Object call(Object o, String name) {
        if (o == null) return null;
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Method mt = k.getDeclaredMethod(name);
                mt.setAccessible(true);
                return mt.invoke(o);
            } catch (NoSuchMethodException e) {
                // keep looking up the hierarchy
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
