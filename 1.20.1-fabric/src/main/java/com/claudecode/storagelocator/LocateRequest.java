package com.claudecode.storagelocator;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.AEKey;

/**
 * Client to server: where is this item or fluid kept in the network of my open terminal? With highlight set, the
 * server also closes the terminal and the client outlines the blocks.
 */
public record LocateRequest(AEKey what, boolean highlight) {

    public void write(FriendlyByteBuf buf) {
        AEKey.writeKey(buf, what);
        buf.writeBoolean(highlight);
    }

    public static LocateRequest read(FriendlyByteBuf buf) {
        AEKey what = AEKey.readKey(buf);
        return new LocateRequest(what, buf.readBoolean());
    }
}
