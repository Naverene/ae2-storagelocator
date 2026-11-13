package com.claudecode.storagelocator;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;

/** AE2 for 1.12.2 has a storage channel per kind of stack: items and fluids. */
public final class Stacks {

    private Stacks() {
    }

    static IItemStorageChannel items() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    static IFluidStorageChannel fluids() {
        return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    static boolean isFluid(IAEStack<?> s) {
        return s instanceof IAEFluidStack;
    }

    static void write(ByteBuf buf, IAEStack<?> s) {
        buf.writeBoolean(isFluid(s));
        try {
            s.writeToPacket(buf);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nullable
    static IAEStack<?> read(ByteBuf buf) {
        boolean fluid = buf.readBoolean();
        try {
            return fluid ? fluids().readFromPacket(buf) : items().readFromPacket(buf);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
