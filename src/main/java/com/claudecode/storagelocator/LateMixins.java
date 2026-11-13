package com.claudecode.storagelocator;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

import cpw.mods.fml.relauncher.FMLLaunchHandler;

@LateMixin
public class LateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.ae2storagelocator.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        if (!loadedMods.contains("appliedenergistics2") || !FMLLaunchHandler.side()
            .isClient()) return Collections.emptyList();
        return Collections.singletonList("VirtualMESlotTooltipMixin");
    }
}
