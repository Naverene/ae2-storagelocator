package com.claudecode.storagelocator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import appeng.api.stacks.AEKey;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one key (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public record LocateReply(AEKey what, List<Component> lines, boolean highlight, List<BlockPos> positions) {

    void write(FriendlyByteBuf buf) {
        AEKey.writeKey(buf, what);
        int n = Math.min(lines.size(), 32);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeComponent(lines.get(i));
        buf.writeBoolean(highlight);
        int m = Math.min(positions.size(), 4096);
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) buf.writeBlockPos(positions.get(i));
    }

    static LocateReply read(FriendlyByteBuf buf) {
        AEKey what = AEKey.readKey(buf);
        int n = Math.min(buf.readVarInt(), 32);
        List<Component> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) lines.add(buf.readComponent());
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
