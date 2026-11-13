package com.claudecode.storagelocator;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** config/ae2storagelocator.properties. The highlight key itself is a normal key binding (Options > Controls). */
public final class Config {

    private Config() {
    }

    /** How long the in-world highlight of where an item is stored stays up. */
    public static int highlightSeconds = 30;

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("ae2storagelocator.properties");
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            } catch (IOException e) {
                StorageLocator.LOG.warn("ae2storagelocator: could not read {}", file, e);
            }
        }
        try {
            highlightSeconds = Math.max(1, Math.min(3600, Integer.parseInt(p.getProperty("highlightSeconds", "30").trim())));
        } catch (NumberFormatException e) {
            highlightSeconds = 30;
        }
        p.setProperty("highlightSeconds", Integer.toString(highlightSeconds));
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Seconds the blocks holding an item stay highlighted after you press the highlight key "
                    + "(default Insert) on it in an AE terminal (1 to 3600).");
        } catch (IOException e) {
            StorageLocator.LOG.warn("ae2storagelocator: could not write {}", file, e);
        }
    }
}
