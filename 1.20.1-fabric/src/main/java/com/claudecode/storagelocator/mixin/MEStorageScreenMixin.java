package com.claudecode.storagelocator.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.network.chat.Component;

import appeng.client.gui.me.common.MEStorageScreen;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Adds the "Stored in" lines to the tooltip of a terminal entry. Forge and NeoForge do this with their tooltip event;
 * Fabric's tooltip callback only sees items, not fluids, so the list is changed where AE2 draws it.
 */
@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class MEStorageScreenMixin {

    @ModifyArg(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V"),
            index = 1)
    private List<Component> ae2storagelocator$itemTooltip(List<Component> lines) {
        return ClientState.withLocations(lines);
    }

    @ModifyArg(method = "renderGridInventoryEntryTooltip", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            index = 1)
    private List<Component> ae2storagelocator$otherTooltip(List<Component> lines) {
        return ClientState.withLocations(lines);
    }
}
