package com.claudecode.storagelocator;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.cells.ICellProvider;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.container.AEBaseContainer;
import appeng.core.Api;
import appeng.parts.AEBasePart;
import appeng.tile.storage.ChestTileEntity;
import appeng.tile.storage.DriveTileEntity;

/** Server side: finds the network of the player's open terminal and every storage on it holding a stack. */
final class Locator {

    private Locator() {
    }

    /** How many places the tooltip names; the rest are summed on one line. */
    private static final int SHOWN = 6;

    static final class Result {
        final List<ITextComponent> lines;
        final List<BlockPos> positions;

        Result(List<ITextComponent> lines, List<BlockPos> positions) {
            this.lines = lines;
            this.positions = positions;
        }
    }

    private static final class Hit {
        final ITextComponent label;
        @Nullable
        final BlockPos pos;
        @Nullable
        final World world;
        long amount;

        Hit(ITextComponent label, @Nullable BlockPos pos, @Nullable World world) {
            this.label = label;
            this.pos = pos;
            this.world = world;
        }
    }

    @SuppressWarnings({ "rawtypes" })
    static Result locate(ServerPlayerEntity p, IAEStack<?> what) {
        List<ITextComponent> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        IGrid grid = gridOf(p.openContainer);
        if (grid == null) return new Result(lines, new ArrayList<>(positions));

        boolean fluid = Stacks.isFluid(what);
        IStorageChannel channel = fluid ? Stacks.fluids() : Stacks.items();
        Map<String, Hit> where = new LinkedHashMap<>();
        collect(grid, what, channel, 0, new HashSet<>(), false, where);
        if (where.isEmpty()) {
            lines.add(new TranslationTextComponent("ae2storagelocator.not_stored").mergeStyle(TextFormatting.DARK_GRAY));
            return new Result(lines, new ArrayList<>(positions));
        }

        List<Hit> hits = new ArrayList<>(where.values());
        hits.sort((a, b) -> Long.compare(b.amount, a.amount));
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        String unit = fluid ? " mB" : "";
        lines.add((hits.size() == 1 ? new TranslationTextComponent("ae2storagelocator.stored_in_one")
                : new TranslationTextComponent("ae2storagelocator.stored_in", hits.size()))
                .mergeStyle(TextFormatting.GRAY));
        long rest = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.pos != null && h.world == p.world && positions.size() < 4096) positions.add(h.pos);
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(new StringTextComponent(" - ").mergeStyle(TextFormatting.DARK_GRAY)
                    .appendSibling(h.label.deepCopy().mergeStyle(TextFormatting.WHITE))
                    .appendSibling(new StringTextComponent(": ").mergeStyle(TextFormatting.GRAY))
                    .appendSibling(new StringTextComponent(nf.format(h.amount) + unit).mergeStyle(TextFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(new TranslationTextComponent("ae2storagelocator.more", hits.size() - SHOWN,
                    nf.format(rest) + unit).mergeStyle(TextFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    /** Every storage on `grid` holding `what`: drives by cell slot, subnets behind storage buses walked into. */
    @SuppressWarnings({ "rawtypes" })
    private static void collect(IGrid grid, IAEStack<?> what, IStorageChannel channel, int depth, Set<IGrid> seen,
            boolean subnet, Map<String, Hit> where) {
        if (grid == null || depth > 3 || !seen.add(grid)) return;
        for (IGridNode node : grid.getNodes()) {
            Object m = node.getMachine();
            try {
                if (m instanceof IChestOrDrive && m instanceof TileEntity) {
                    drive((IChestOrDrive) m, (TileEntity) m, what, channel, subnet, where);
                } else if (m instanceof IPart && m instanceof ICellProvider && m instanceof AEBasePart) {
                    bus((AEBasePart) m, grid, what, channel, depth, seen, subnet, where);
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("ae2storagelocator: skipped {}", m, e);
            }
        }
    }

    /** Drives and ME chests: each cell slot on its own, so the label can say which slot. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void drive(IChestOrDrive cd, TileEntity te, IAEStack<?> what, IStorageChannel channel,
            boolean subnet, Map<String, Hit> where) {
        ITextComponent holder = te.getBlockState().getBlock().getTranslatedName();
        for (int slot = 0; slot < cd.getCellCount(); slot++) {
            ItemStack cell = ItemStack.EMPTY;
            if (te instanceof DriveTileEntity) cell = ((DriveTileEntity) te).getInternalInventory().getStackInSlot(slot);
            else if (te instanceof ChestTileEntity && slot == 0) cell = ((ChestTileEntity) te).getCell();
            if (cell.isEmpty()) continue;
            IMEInventoryHandler inv = Api.instance().registries().cell().getCellInventory(cell, null, channel);
            long c = count(inv, what, channel);
            if (c <= 0) continue;
            IFormattableTextComponent label = new TranslationTextComponent("ae2storagelocator.cell_in",
                    cell.getDisplayName(), holder);
            label.appendSibling(at(te.getPos())).appendString(", ")
                    .appendSibling(new TranslationTextComponent("ae2storagelocator.slot", slot + 1));
            if (subnet) label.appendString(" ").appendSibling(new TranslationTextComponent("ae2storagelocator.subnet"));
            add(where, "d|" + te.getPos().toLong() + "|" + slot, label, c, te.getPos(), te.getWorld());
        }
    }

    /** A storage bus: walk into the network it faces, or name the block it faces. */
    @SuppressWarnings({ "rawtypes" })
    private static void bus(AEBasePart part, IGrid grid, IAEStack<?> what, IStorageChannel channel, int depth,
            Set<IGrid> seen, boolean subnet, Map<String, Hit> where) {
        TileEntity host = part.getTile();
        AEPartLocation loc = part.getSide();
        if (host == null || loc == null || loc == AEPartLocation.INTERNAL) return;
        World world = host.getWorld();
        if (world == null) return;
        Direction side = loc.getFacing();
        BlockPos target = host.getPos().offset(side);

        TileEntity facing = world.getTileEntity(target);
        if (facing instanceof IGridHost) {
            IGridNode fn = ((IGridHost) facing).getGridNode(AEPartLocation.fromFacing(side.getOpposite()));
            if (fn == null) fn = ((IGridHost) facing).getGridNode(AEPartLocation.INTERNAL);
            if (fn != null && fn.getGrid() != null && fn.getGrid() != grid) {
                collect(fn.getGrid(), what, channel, depth + 1, seen, true, where);
                return;
            }
        }

        long c = 0;
        for (Object h : ((ICellProvider) part).getCellArray(channel)) {
            c += count((IMEInventory) h, what, channel);
        }
        if (c <= 0) return;
        IFormattableTextComponent label = world.getBlockState(target).getBlock().getTranslatedName().deepCopy();
        label.appendSibling(at(target));
        if (subnet) label.appendString(" ").appendSibling(new TranslationTextComponent("ae2storagelocator.subnet"));
        add(where, "b|" + target.toLong(), label, c, target, world);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static long count(@Nullable IMEInventory inv, IAEStack<?> what, IStorageChannel channel) {
        if (inv == null) return 0;
        IItemList list = inv.getAvailableItems(channel.createList());
        IAEStack hit = list == null ? null : (IAEStack) list.findPrecise(what);
        return hit == null ? 0 : hit.getStackSize();
    }

    private static void add(Map<String, Hit> where, String key, ITextComponent label, long amount,
            @Nullable BlockPos pos, @Nullable World world) {
        where.computeIfAbsent(key, k -> new Hit(label, pos, world)).amount += amount;
    }

    private static ITextComponent at(BlockPos pos) {
        return new StringTextComponent(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the AE screen the player has open, or null. */
    @Nullable
    static IGrid gridOf(@Nullable Container c) {
        if (!(c instanceof AEBaseContainer)) return null;
        Object target = ((AEBaseContainer) c).getTarget();
        IGridNode node = target instanceof IActionHost ? ((IActionHost) target).getActionableNode() : null;
        return node == null ? null : node.getGrid();
    }
}
