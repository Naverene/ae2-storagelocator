package com.claudecode.storagelocator.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.claudecode.storagelocator.ClientCache;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlot;

/** Adds "Stored in ..." lines to the tooltip of every item and fluid shown in an AE terminal grid. */
@Mixin(value = VirtualMESlot.class, remap = false)
public abstract class VirtualMESlotTooltipMixin {

    @Shadow
    public abstract IAEStack<?> getAEStack();

    @Inject(method = "addTooltip", at = @At("TAIL"))
    private void ae2storagelocator$where(List<String> tip, CallbackInfo ci) {
        try {
            tip.addAll(ClientCache.linesFor(getAEStack()));
        } catch (Throwable ignored) {
            // never break AE's own tooltip
        }
    }
}
