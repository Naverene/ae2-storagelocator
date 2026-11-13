package com.claudecode.storagelocator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import appeng.api.storage.data.IAEStack;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one stack (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public final class LocateReply {

    public final IAEStack<?> what;
    public final List<ITextComponent> lines;
    public final boolean highlight;
    public final List<BlockPos> positions;

    public LocateReply(IAEStack<?> what, List<ITextComponent> lines, boolean highlight, List<BlockPos> positions) {
        this.what = what;
        this.lines = lines;
        this.highlight = highlight;
        this.positions = positions;
    }

    static void write(LocateReply msg, PacketBuffer buf) {
        Stacks.write(buf, msg.what);
        int n = Math.min(msg.lines.size(), 32);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeTextComponent(msg.lines.get(i));
        buf.writeBoolean(msg.highlight);
        int m = Math.min(msg.positions.size(), 4096);
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) buf.writeBlockPos(msg.positions.get(i));
    }

    static LocateReply read(PacketBuffer buf) {
        IAEStack<?> what = Stacks.read(buf);
        int n = Math.min(buf.readVarInt(), 32);
        List<ITextComponent> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readTextComponent());
        boolean highlight = buf.readBoolean();
        int m = Math.min(buf.readVarInt(), 4096);
        List<BlockPos> positions = new ArrayList<>(m);
        for (int i = 0; i < m; i++) positions.add(buf.readBlockPos());
        return new LocateReply(what, lines, highlight, positions);
    }

    static void handle(LocateReply msg, Supplier<NetworkEvent.Context> ctx) {
        if (msg.what != null) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.onReply(msg)));
        }
        ctx.get().setPacketHandled(true);
    }
}
