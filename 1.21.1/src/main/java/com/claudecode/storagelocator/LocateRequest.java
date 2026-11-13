package com.claudecode.storagelocator;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.stacks.AEKey;

/**
 * Client to server: where is this item or fluid kept in the network of my open terminal? With highlight set, the
 * server also closes the terminal and the client outlines the blocks.
 */
public record LocateRequest(AEKey what, boolean highlight) implements CustomPacketPayload {

    public static final Type<LocateRequest> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StorageLocator.MOD_ID, "locate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocateRequest> STREAM_CODEC = StreamCodec.composite(
            AEKey.STREAM_CODEC, LocateRequest::what,
            ByteBufCodecs.BOOL, LocateRequest::highlight,
            LocateRequest::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LocateRequest msg, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer p) || msg.what == null || !StorageLocator.allow(p)) return;
        try {
            var found = Locator.locate(p, msg.what);
            PacketDistributor.sendToPlayer(p,
                    new LocateReply(msg.what, found.lines(), msg.highlight, found.positions()));
            if (msg.highlight && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("ae2storagelocator: lookup failed", e);
        }
    }
}
