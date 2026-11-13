package com.claudecode.storagelocator;

import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** config/ae2storagelocator-client.cfg. The highlight key itself is a normal key binding (Options > Controls). */
@net.minecraftforge.common.config.Config(modid = StorageLocator.MOD_ID, name = "ae2storagelocator-client")
@Mod.EventBusSubscriber(modid = StorageLocator.MOD_ID)
public final class Config {

    private Config() {
    }

    @net.minecraftforge.common.config.Config.Comment("Seconds the blocks holding an item stay highlighted after you "
            + "press the highlight key (default Insert) on it in an AE terminal.")
    @net.minecraftforge.common.config.Config.RangeInt(min = 1, max = 3600)
    public static int highlightSeconds = 30;

    @SubscribeEvent
    public static void onChanged(ConfigChangedEvent.OnConfigChangedEvent e) {
        if (StorageLocator.MOD_ID.equals(e.getModID())) {
            ConfigManager.sync(StorageLocator.MOD_ID, net.minecraftforge.common.config.Config.Type.INSTANCE);
        }
    }
}
