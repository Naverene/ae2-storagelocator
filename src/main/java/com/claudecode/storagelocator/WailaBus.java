package com.claudecode.storagelocator;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.integration.modules.waila.part.PartAccessor;
import appeng.parts.misc.PartStorageBus;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA on an AE storage bus facing GT Super/Quantum Chests or Tanks: how many of the touching group the bus sees,
 * and how many it doesn't (group over gtstorageclusters' limit, or more than one bus on the group).
 */
public final class WailaBus implements IWailaDataProvider {

    private static final String KEY = "ae2slBus";
    private static final PartAccessor PARTS = new PartAccessor();

    /** Called by Waila through the IMC message sent in init. */
    public static void register(IWailaRegistrar r) {
        WailaBus p = new WailaBus();
        try {
            Class<?> c = Class.forName("appeng.tile.networking.TileCableBus");
            r.registerBodyProvider(p, c);
            r.registerNBTProvider(p, c);
        } catch (ClassNotFoundException e) {
            StorageLocator.LOG.warn("ae2storagelocator: no AE cable bus class for WAILA");
        }
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y,
        int z) {
        if (!(te instanceof IPartHost)) return tag;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            IPart part = ((IPartHost) te).getPart(d);
            if (!(part instanceof PartStorageBus)) continue;
            boolean fluid = part.getClass()
                .getName()
                .contains("Fluid");
            String s = ClusterStatus.busLine(world, x + d.offsetX, y + d.offsetY, z + d.offsetZ, fluid);
            if (s != null) tag.setString(KEY + d.ordinal(), s);
        }
        return tag;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor acc, IWailaConfigHandler cfg) {
        NBTTagCompound tag = acc.getNBTData();
        MovingObjectPosition mop = acc.getPosition();
        if (tag == null || mop == null) return tip;
        IPart part = PARTS.getMaybePart(acc.getTileEntity(), mop)
            .orNull();
        if (!(part instanceof PartStorageBus) || !(acc.getTileEntity() instanceof IPartHost)) return tip;
        IPartHost host = (IPartHost) acc.getTileEntity();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (host.getPart(d) == part && tag.hasKey(KEY + d.ordinal())) tip.add(tag.getString(KEY + d.ordinal()));
        }
        return tip;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor acc, IWailaConfigHandler cfg) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor acc, IWailaConfigHandler cfg) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor acc, IWailaConfigHandler cfg) {
        return tip;
    }
}
