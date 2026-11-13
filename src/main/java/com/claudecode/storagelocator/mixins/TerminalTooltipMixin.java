package com.claudecode.storagelocator.mixins;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.claudecode.storagelocator.ClientCache;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.slots.VirtualMESlot;

/**
 * Adds "Stored in ..." lines under the terminal's own tooltip (after "Items Stored" / flow rate) for whatever item or
 * fluid is under the mouse in the ME grid.
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class TerminalTooltipMixin {

    @Inject(method = "handleItemTooltip", at = @At("RETURN"))
    private void ae2storagelocator$where(ItemStack stack, int x, int y, List<String> in,
        CallbackInfoReturnable<List<String>> cir) {
        try {
            List<String> out = cir.getReturnValue();
            if (out == null) return;
            VirtualMESlot slot = ((AEBaseGui) (Object) this).getVirtualMESlotUnderMouse();
            if (slot == null || slot.getAEStack() == null) return;
            out.addAll(ClientCache.linesFor(slot.getAEStack()));
        } catch (Throwable ignored) {
            // never break AE's own tooltip
        }
    }
}
