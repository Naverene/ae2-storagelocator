package com.claudecode.storagelocator;

import java.util.function.Supplier;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import appeng.api.storage.data.IAEStack;

/**
 * Client to server: where is this item or fluid kept in the network of my open terminal? With highlight set, the
 * server also closes the terminal and the client outlines the blocks.
 */
public final class LocateRequest {

    final IAEStack<?> what;
    final boolean highlight;

    public LocateRequest(IAEStack<?> what, boolean highlight) {
        this.what = what;
        this.highlight = highlight;
    }

    static void write(LocateRequest msg, PacketBuffer buf) {
        Stacks.write(buf, msg.what);
        buf.writeBoolean(msg.highlight);
    }

    static LocateRequest read(PacketBuffer buf) {
        IAEStack<?> what = Stacks.read(buf);
        return new LocateRequest(what, buf.readBoolean());
    }

    static void handle(LocateRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayerEntity p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> answer(msg, p));
        ctx.get().setPacketHandled(true);
    }

    private static void answer(LocateRequest msg, ServerPlayerEntity p) {
        if (p == null || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            Locator.Result found = Locator.locate(p, msg.what);
            StorageLocator.NET.send(PacketDistributor.PLAYER.with(() -> p),
                    new LocateReply(msg.what, found.lines, msg.highlight, found.positions));
            if (msg.highlight && !found.positions.isEmpty()) p.closeScreen();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("ae2storagelocator: lookup failed", e);
        }
    }
}
