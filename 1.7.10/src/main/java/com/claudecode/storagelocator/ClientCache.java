package com.claudecode.storagelocator;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/** Client side: answers we already have, and which questions are still out. */
public final class ClientCache {

    private ClientCache() {}

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private static final Map<String, Object[]> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<String, Long> ASKED = new ConcurrentHashMap<>();

    static void put(String key, List<String> lines) {
        ANSWERS.put(key, new Object[] { System.currentTimeMillis(), lines });
        ASKED.remove(key);
    }

    /** Tooltip lines for a terminal stack, asking the server when we have nothing fresh. */
    @SuppressWarnings("unchecked")
    public static List<String> linesFor(IAEStack<?> s) {
        if (s == null || StorageLocator.NET == null) return Collections.emptyList();
        String key;
        ItemStack item = null;
        String fluid = null;
        if (s instanceof IAEItemStack) {
            item = ((IAEItemStack) s).getItemStack();
            if (item == null || item.getItem() == null) return Collections.emptyList();
            item.stackSize = 1;
            key = "i:" + Item.getIdFromItem(item.getItem()) + ":" + item.getItemDamage() + ":"
                + (item.hasTagCompound() ? item.getTagCompound()
                    .toString()
                    .hashCode() : 0);
        } else if (s instanceof IAEFluidStack) {
            FluidStack f = ((IAEFluidStack) s).getFluidStack();
            if (f == null || f.getFluid() == null) return Collections.emptyList();
            fluid = f.getFluid()
                .getName();
            key = "f:" + fluid;
        } else {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        Object[] a = ANSWERS.get(key);
        boolean fresh = a != null && now - (Long) a[0] < FRESH_MS;
        if (!fresh) {
            Long asked = ASKED.get(key);
            if (asked == null || now - asked > RETRY_MS) {
                ASKED.put(key, now);
                if (ANSWERS.size() > 2048) ANSWERS.clear();
                StorageLocator.NET.sendToServer(new LocateRequest(key, item, fluid));
            }
        }
        if (a != null) return (List<String>) a[1];
        return Collections.singletonList(EnumChatFormatting.DARK_GRAY + "Locating...");
    }
}
