package com.claudecode.storagelocator;

import javax.annotation.Nullable;

import net.minecraft.network.PacketBuffer;

import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.Api;

/** AE2 8 has a storage channel per kind of stack: items and fluids. */
final class Stacks {

    private Stacks() {
    }

    static IItemStorageChannel items() {
        return Api.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    static IFluidStorageChannel fluids() {
        return Api.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    static boolean isFluid(IAEStack<?> s) {
        return s instanceof IAEFluidStack;
    }

    static void write(PacketBuffer buf, IAEStack<?> s) {
        buf.writeBoolean(isFluid(s));
        s.writeToPacket(buf);
    }

    @Nullable
    static IAEStack<?> read(PacketBuffer buf) {
        boolean fluid = buf.readBoolean();
        try {
            return fluid ? fluids().readFromPacket(buf) : items().readFromPacket(buf);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
