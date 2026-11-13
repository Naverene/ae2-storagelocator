package com.claudecode.storagelocator;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/ae2storagelocator-client.toml. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {
    }

    static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue HIGHLIGHT_SECONDS;

    static {
        var b = new ModConfigSpec.Builder();
        HIGHLIGHT_SECONDS = b
                .comment("Seconds the blocks holding an item stay highlighted after you press the highlight key "
                        + "(default Insert) on it in an AE terminal.")
                .defineInRange("highlightSeconds", 30, 1, 3600);
        SPEC = b.build();
    }
}
