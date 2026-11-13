package com.claudecode.storagelocator;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.stacks.AEKey;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one key (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public record LocateReply(AEKey what, List<Component> lines, boolean highlight, List<BlockPos> positions)
        implements CustomPacketPayload {

    public static final Type<LocateReply> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StorageLocator.MOD_ID, "located"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocateReply> STREAM_CODEC = StreamCodec.composite(
            AEKey.STREAM_CODEC, LocateReply::what,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(32)), LocateReply::lines,
            ByteBufCodecs.BOOL, LocateReply::highlight,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(4096)), LocateReply::positions,
            LocateReply::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LocateReply msg, IPayloadContext ctx) {
        if (msg.what == null) return;
        ClientState.onReply(msg);
    }
}
