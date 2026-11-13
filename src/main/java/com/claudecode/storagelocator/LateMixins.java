package com.claudecode.storagelocator;

import java.util.ArrayList;
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
        List<String> out = new ArrayList<>();
        if (!loadedMods.contains("appliedenergistics2")) return out;
        if (FMLLaunchHandler.side()
            .isClient()) {
            out.add("TerminalTooltipMixin");
            out.add("TerminalHighlightKeyMixin");
            out.add("HighlightDurationMixin");
        }
        if (loadedMods.contains("gregtech")) {
            out.add("DigitalStorageStatusMixin");
            out.add("DigitalChestScannerMixin");
        }
        return out;
    }
}
