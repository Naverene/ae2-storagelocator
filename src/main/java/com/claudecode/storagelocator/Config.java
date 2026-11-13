package com.claudecode.storagelocator;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** config/ae2storagelocator.cfg. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {}

    /** How long the in-world highlight of where an item is stored stays up. */
    public static int highlightSeconds = 30;

    static void load(File dir) {
        Configuration cfg = new Configuration(new File(dir, "ae2storagelocator.cfg"));
        highlightSeconds = cfg.getInt(
            "highlightSeconds",
            "client",
            30,
            1,
            3600,
            "Seconds the blocks holding an item stay highlighted after you press the highlight key (default Insert) "
                + "on it in an AE terminal.");
        if (cfg.hasChanged()) cfg.save();
    }
}
