package com.claudecode.storagelocator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.MEStorage;
import appeng.menu.AEBaseMenu;
import appeng.parts.storagebus.StorageBusPart;

/** Server side: finds the network of the player's open terminal and every storage on it holding a key. */
final class Locator {

    private Locator() {
    }

    /** How many places the tooltip names; the rest are summed on one line. */
    private static final int SHOWN = 6;

    record Result(List<Component> lines, List<BlockPos> positions) {
    }

    private static final class Hit {
        final Component label;
        final @Nullable BlockPos pos;
        final @Nullable Level level;
        long amount;

        Hit(Component label, @Nullable BlockPos pos, @Nullable Level level) {
            this.label = label;
            this.pos = pos;
            this.level = level;
        }
    }

    static Result locate(ServerPlayer p, AEKey what) {
        List<Component> lines = new ArrayList<>();
        Set<BlockPos> positions = new LinkedHashSet<>();
        IGrid grid = gridOf(p);
        if (grid == null) return new Result(lines, new ArrayList<>(positions));

        Map<String, Hit> where = new LinkedHashMap<>();
        collect(grid, what, 0, new HashSet<>(), false, where);
        if (where.isEmpty()) {
            lines.add(Component.translatable("ae2storagelocator.not_stored").withStyle(ChatFormatting.DARK_GRAY));
            return new Result(lines, new ArrayList<>(positions));
        }

        List<Hit> hits = new ArrayList<>(where.values());
        hits.sort((a, b) -> Long.compare(b.amount, a.amount));
        lines.add((hits.size() == 1 ? Component.translatable("ae2storagelocator.stored_in_one")
                : Component.translatable("ae2storagelocator.stored_in", hits.size())).withStyle(ChatFormatting.GRAY));
        long rest = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.pos != null && h.level == p.level() && positions.size() < 4096) positions.add(h.pos);
            if (i >= SHOWN) {
                rest += h.amount;
                continue;
            }
            lines.add(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(h.label.copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(what.formatAmount(h.amount, AmountFormat.FULL))
                            .withStyle(ChatFormatting.GREEN)));
        }
        if (hits.size() > SHOWN) {
            lines.add(Component.translatable("ae2storagelocator.more", hits.size() - SHOWN,
                    what.formatAmount(rest, AmountFormat.FULL)).withStyle(ChatFormatting.DARK_GRAY));
        }
        return new Result(lines, new ArrayList<>(positions));
    }

    /** Every storage on `grid` holding `what`: drives by cell slot, subnets behind storage buses walked into. */
    private static void collect(IGrid grid, AEKey what, int depth, Set<IGrid> seen, boolean subnet,
            Map<String, Hit> where) {
        if (grid == null || depth > 3 || !seen.add(grid)) return;
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            try {
                if (owner instanceof IChestOrDrive cd) {
                    drive(cd, what, subnet, where);
                } else if (owner instanceof StorageBusPart bus) {
                    bus(bus, grid, what, depth, seen, subnet, where);
                }
            } catch (RuntimeException | LinkageError e) {
                StorageLocator.LOG.debug("ae2storagelocator: skipped {}", owner, e);
            }
        }
    }

    /** Drives and ME chests: each cell slot on its own, so the label can say which slot. */
    private static void drive(IChestOrDrive cd, AEKey what, boolean subnet, Map<String, Hit> where) {
        BlockEntity be = cd instanceof BlockEntity b ? b : null;
        Component holder = be == null ? Component.literal("ME Drive") : be.getBlockState().getBlock().getName();
        for (int slot = 0; slot < cd.getCellCount(); slot++) {
            MEStorage inv = cd.getCellInventory(slot);
            if (inv == null) continue;
            long c = inv.getAvailableStacks().get(what);
            if (c <= 0) continue;
            Item cell = cd.getCellItem(slot);
            Component cellName = cell == null ? Component.literal("?") : new ItemStack(cell).getHoverName();
            MutableComponent label = Component.translatable("ae2storagelocator.cell_in", cellName, holder);
            if (be != null) label.append(at(be.getBlockPos()));
            label.append(", ").append(Component.translatable("ae2storagelocator.slot", slot + 1));
            if (subnet) label.append(" ").append(Component.translatable("ae2storagelocator.subnet"));
            String key = "d|" + (be == null ? System.identityHashCode(cd) : be.getBlockPos().asLong()) + "|" + slot;
            add(where, key, label, c, be == null ? null : be.getBlockPos(), be == null ? null : be.getLevel());
        }
    }

    /** A storage bus: walk into the network it faces, or name the block it faces. */
    private static void bus(StorageBusPart bus, IGrid grid, AEKey what, int depth, Set<IGrid> seen, boolean subnet,
            Map<String, Hit> where) {
        BlockEntity hostBe = bus.getHost().getBlockEntity();
        Direction side = bus.getSide();
        if (hostBe == null || side == null) return;
        Level level = hostBe.getLevel();
        BlockPos target = hostBe.getBlockPos().relative(side);
        if (level == null) return;

        var nodeHost = GridHelper.getNodeHost(level, target);
        if (nodeHost != null) {
            IGridNode facing = nodeHost.getGridNode(side.getOpposite());
            if (facing != null && facing.getGrid() != null && facing.getGrid() != grid) {
                collect(facing.getGrid(), what, depth + 1, seen, true, where);
                return;
            }
        }

        MEStorage inv = bus.getInternalHandler();
        if (inv == null) return;
        long c = inv.getAvailableStacks().get(what);
        if (c <= 0) return;
        MutableComponent label = level.getBlockState(target).getBlock().getName().copy().append(at(target));
        if (subnet) label.append(" ").append(Component.translatable("ae2storagelocator.subnet"));
        add(where, "b|" + target.asLong(), label, c, target, level);
    }

    private static void add(Map<String, Hit> where, String key, Component label, long amount, @Nullable BlockPos pos,
            @Nullable Level level) {
        where.computeIfAbsent(key, k -> new Hit(label, pos, level)).amount += amount;
    }

    private static Component at(BlockPos pos) {
        return Component.literal(" @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The network of the AE screen the player has open, or null. */
    @Nullable
    static IGrid gridOf(ServerPlayer p) {
        if (!(p.containerMenu instanceof AEBaseMenu menu)) return null;
        IGridNode node = menu.getTarget() instanceof IActionHost host ? host.getActionableNode() : null;
        return node == null ? null : node.getGrid();
    }
}
