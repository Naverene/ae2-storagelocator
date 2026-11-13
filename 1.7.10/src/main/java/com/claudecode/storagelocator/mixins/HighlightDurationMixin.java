package com.claudecode.storagelocator.mixins;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.claudecode.storagelocator.Config;

import appeng.api.util.ItemSearchDTO;
import appeng.client.render.highlighter.BlockPosHighlighter;
import appeng.client.render.highlighter.StoragePosHighlighter;

/** AE2 picks the storage highlight's lifetime from the distance; use our configured seconds instead. */
@Mixin(value = StoragePosHighlighter.class, remap = false)
public abstract class HighlightDurationMixin {

    @Inject(method = "highlightStorage", at = @At("RETURN"))
    private static void ae2storagelocator$duration(EntityPlayer player, List<ItemSearchDTO> list, String found,
        String wrongDim, CallbackInfo ci) {
        try {
            Field inst = StoragePosHighlighter.class.getDeclaredField("INSTANCE");
            inst.setAccessible(true);
            Field exp = BlockPosHighlighter.class.getDeclaredField("expireHighlightTime");
            exp.setAccessible(true);
            exp.setLong(inst.get(null), System.currentTimeMillis() + Config.highlightSeconds * 1000L);
        } catch (Throwable ignored) {
            // AE2 changed: keep its own timing
        }
    }
}
