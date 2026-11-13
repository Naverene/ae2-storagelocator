package com.claudecode.storagelocator.mixins;

import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.claudecode.storagelocator.ClientKeys;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

/**
 * Our highlight key (default Insert) in an ME terminal: does exactly what AE2's own "Search network inventories for
 * an item" key does (unbound by default): asks the server where the hovered stack is stored and closes the terminal,
 * and AE2 then outlines those blocks in the world, drive cell slots included.
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public abstract class TerminalHighlightKeyMixin extends AEBaseGui {

    @Shadow
    @Final
    private ContainerMEMonitorable monitorableContainer;

    private TerminalHighlightKeyMixin(Container c) {
        super(c);
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true, remap = true)
    private void ae2storagelocator$highlight(char c, int key, CallbackInfo ci) {
        int bound = ClientKeys.HIGHLIGHT.getKeyCode();
        if (bound == 0 || key != bound) return;
        VirtualMESlot slot = getVirtualMESlotUnderMouse();
        if (!(slot instanceof VirtualMEMonitorableSlot)) return;
        IAEStack<?> stack = slot.getAEStack();
        if (stack == null) return;
        try {
            monitorableContainer.setTargetStack(stack);
            NetworkHandler.instance
                .sendToServer(new PacketInventoryAction(InventoryAction.FIND_ITEMS, getInventorySlots().size(), 0L));
            closeGui();
            ci.cancel();
        } catch (Throwable ignored) {
            // leave the key to AE2/vanilla if anything here isn't what we expect
        }
    }
}
