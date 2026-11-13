package com.claudecode.storagelocator;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only: the "highlight where this is stored" key, rebindable under Options > Controls. */
@SideOnly(Side.CLIENT)
public final class ClientKeys {

    private ClientKeys() {}

    public static final KeyBinding HIGHLIGHT = new KeyBinding(
        "key.ae2storagelocator.highlight",
        Keyboard.KEY_INSERT,
        "key.categories.ae2storagelocator");

    static void register() {
        ClientRegistry.registerKeyBinding(HIGHLIGHT);
    }
}
