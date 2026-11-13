package com.claudecode.storagelocator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.parts.misc.PartStorageBus;
import cpw.mods.fml.common.Loader;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.storage.MTEDigitalChestBase;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;

/**
 * One line telling a player whether a Super/Quantum Chest or Tank is visible in AE, and why not when it isn't: which
 * storage bus sees it and how many steps away it is, or that its group has no bus, too many buses, or is bigger than
 * one bus will look. Shown in WAILA (when the client has this mod too) and by the portable scanner.
 * Groups (one bus seeing every touching chest/tank) only exist with gtstorageclusters installed; without it a block is
 * in AE only through a storage bus on itself.
 */
public final class ClusterStatus {

    private ClusterStatus() {}

    /** gtstorageclusters' per-bus group size, or 0 when that mod isn't installed (no groups). */
    private static int groupMax = -1;

    static int max() {
        if (groupMax < 0) {
            groupMax = 0;
            if (Loader.isModLoaded("gtstorageclusters")) {
                try {
                    java.lang.reflect.Field f = Class.forName("com.claudecode.storageclusters.StorageClusters")
                        .getDeclaredField("max");
                    f.setAccessible(true);
                    groupMax = f.getInt(null);
                } catch (Throwable t) {
                    groupMax = 63;
                }
            }
        }
        return groupMax;
    }

    static MTEDigitalChestBase chest(TileEntity te) {
        if (!(te instanceof IGregTechTileEntity) || te.isInvalid()) return null;
        IMetaTileEntity m = ((IGregTechTileEntity) te).getMetaTileEntity();
        return m instanceof MTEDigitalChestBase ? (MTEDigitalChestBase) m : null;
    }

    static MTEDigitalTankBase tank(TileEntity te) {
        if (!(te instanceof IGregTechTileEntity) || te.isInvalid()) return null;
        IMetaTileEntity m = ((IGregTechTileEntity) te).getMetaTileEntity();
        return m instanceof MTEDigitalTankBase ? (MTEDigitalTankBase) m : null;
    }

    /** Is an AE storage bus of the matching kind (fluid bus for tanks) attached to this tile? */
    static boolean busFacing(TileEntity te, boolean tanks) {
        World w = te.getWorldObj();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int x = te.xCoord + d.offsetX, y = te.yCoord + d.offsetY, z = te.zCoord + d.offsetZ;
            if (y < 0 || y > 255 || !w.blockExists(x, y, z)) continue;
            TileEntity n = w.getTileEntity(x, y, z);
            if (!(n instanceof IPartHost)) continue;
            IPart part = ((IPartHost) n).getPart(d.getOpposite());
            if (part instanceof PartStorageBus && part.getClass()
                .getName()
                .contains("Fluid") == tanks) return true;
        }
        return false;
    }

    /** Hard cap on how far we walk a group just to describe it. */
    private static final int WALK_LIMIT = 4096;

    private static final Map<String, Object[]> CACHE = new HashMap<>();

    /** Status for the chest/tank at x,y,z, or null if there's none. Cached for a second per block. */
    public static String describe(World w, int x, int y, int z) {
        if (w == null || w.isRemote) return null;
        String key = w.provider.dimensionId + ":" + x + ":" + y + ":" + z;
        long now = w.getTotalWorldTime();
        Object[] hit = CACHE.get(key);
        if (hit != null && now - (Long) hit[0] < 20) return (String) hit[1];
        String s = compute(w, x, y, z);
        if (CACHE.size() > 4096) CACHE.clear();
        CACHE.put(key, new Object[] { now, s });
        return s;
    }

    public static String describe(TileEntity te) {
        return te == null ? null : describe(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord);
    }

    private static String compute(World w, int x, int y, int z) {
        TileEntity self = w.getTileEntity(x, y, z);
        if (self == null) return null;
        boolean tanks;
        if (tank(self) != null) tanks = true;
        else if (chest(self) != null) tanks = false;
        else return null;
        String what = tanks ? "fluid storage bus" : "storage bus";
        boolean own = busFacing(self, tanks);
        if (max() == 0) return own ? ok("In AE: own " + what) : bad("Not in AE: no " + what + " on this block");

        // Walk the whole touching group from this block, noting which members have a bus.
        List<TileEntity> buses = new ArrayList<>();
        int size = 0;
        ArrayDeque<TileEntity> todo = new ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        todo.add(self);
        seen.add(key(x, y, z));
        while (!todo.isEmpty() && size < WALK_LIMIT) {
            TileEntity te = todo.poll();
            size++;
            if (busFacing(te, tanks)) buses.add(te);
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nx = te.xCoord + d.offsetX, ny = te.yCoord + d.offsetY, nz = te.zCoord + d.offsetZ;
                if (ny < 0 || ny > 255 || !seen.add(key(nx, ny, nz)) || !w.blockExists(nx, ny, nz)) continue;
                TileEntity n = w.getTileEntity(nx, ny, nz);
                if (n == null || (tanks ? tank(n) == null : chest(n) == null)) continue;
                todo.add(n);
            }
        }
        String group = size >= WALK_LIMIT ? WALK_LIMIT + "+" : String.valueOf(size);

        if (own && (size == 1 || buses.size() > 1))
            return ok("In AE: own " + what + (size > 1 ? " (group has " + buses.size() + " buses, each sees only its block)" : ""));
        if (buses.isEmpty()) return bad("Not in AE: no " + what + " on this " + (size == 1 ? "block" : "group of " + group));
        if (buses.size() > 1) return bad(
            "Not in AE: group of " + group + " has " + buses.size() + " " + what + "es, so each only sees its own block");

        // Exactly one bus: it sees the first `max` blocks of the group, nearest first.
        TileEntity bus = buses.get(0);
        int[] dist = new int[1];
        int rank = rank(bus, self, tanks, dist);
        String where = "bus at " + bus.xCoord + " " + bus.yCoord + " " + bus.zCoord;
        if (own) return ok("In AE: own " + what + ", sees all " + Math.min(size, max()) + " of " + group);
        if (rank >= 0 && rank < max())
            return ok("In AE: via " + where + ", " + dist[0] + " step" + (dist[0] == 1 ? "" : "s") + " away (#" + (rank + 1) + " of "
                + Math.min(size, max()) + ")");
        return bad(
            "Not in AE: " + (rank < 0 ? "too far" : (dist[0] + " steps")) + " from the " + where + "; one bus sees "
                + max() + " blocks and this group has " + group);
    }

    /** Order in which a bus on `from` would see `target` (breadth first, like StorageClusters.group), -1 if not met. */
    private static int rank(TileEntity from, TileEntity target, boolean tanks, int[] dist) {
        World w = from.getWorldObj();
        ArrayDeque<Object[]> todo = new ArrayDeque<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        todo.add(new Object[] { from, 0 });
        seen.add(key(from.xCoord, from.yCoord, from.zCoord));
        int i = 0;
        while (!todo.isEmpty() && i < WALK_LIMIT) {
            Object[] e = todo.poll();
            TileEntity te = (TileEntity) e[0];
            int steps = (Integer) e[1];
            if (te == target) {
                dist[0] = steps;
                return i;
            }
            i++;
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                int nx = te.xCoord + d.offsetX, ny = te.yCoord + d.offsetY, nz = te.zCoord + d.offsetZ;
                if (ny < 0 || ny > 255 || !seen.add(key(nx, ny, nz)) || !w.blockExists(nx, ny, nz)) continue;
                TileEntity n = w.getTileEntity(nx, ny, nz);
                if (n == null || (tanks ? tank(n) == null : chest(n) == null)) continue;
                todo.add(new Object[] { n, steps + 1 });
            }
        }
        return -1;
    }

    private static String ok(String s) {
        return EnumChatFormatting.GREEN + s;
    }

    private static String bad(String s) {
        return EnumChatFormatting.RED + s;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }
}
