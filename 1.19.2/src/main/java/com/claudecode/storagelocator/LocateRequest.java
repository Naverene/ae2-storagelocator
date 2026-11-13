package com.claudecode.storagelocator;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;

/**
 * Client to server: where is this item or fluid kept in the network of my open terminal? With highlight set, the
 * server also closes the terminal and the client outlines the blocks.
 */
public record LocateRequest(AEKey what, boolean highlight) {

    void write(FriendlyByteBuf buf) {
        AEKey.writeKey(buf, what);
        buf.writeBoolean(highlight);
    }

    static LocateRequest read(FriendlyByteBuf buf) {
        AEKey what = AEKey.readKey(buf);
        return new LocateRequest(what, buf.readBoolean());
    }

    static void handle(LocateRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            var found = Locator.locate(p, msg.what);
            StorageLocator.NET.send(PacketDistributor.PLAYER.with(() -> p),
                    new LocateReply(msg.what, found.lines(), msg.highlight, found.positions()));
            if (msg.highlight && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("ae2storagelocator: lookup failed", e);
        }
    }
}
