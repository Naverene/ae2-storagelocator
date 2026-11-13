package com.claudecode.storagelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Hover an item or fluid in an AE2 terminal and its tooltip lists where the network keeps it: which drive cells, ME
 * chests and storage-bus targets hold it, and how many each. The client asks the server once per stack (cached a few
 * seconds); the server answers from the network of the terminal the player has open. Pressing the highlight key
 * (Insert) on an entry closes the terminal and outlines those blocks in the world.
 * <p>
 * Optional on either side: without it on the server the tooltip just doesn't get the extra lines.
 */
@Mod(StorageLocator.MOD_ID)
public class StorageLocator {

    public static final String MOD_ID = "ae2storagelocator";
    public static final Logger LOG = LogUtils.getLogger();

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    public StorageLocator(IEventBus modBus, ModContainer container, Dist dist) {
        modBus.addListener(StorageLocator::registerPayloads);
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        if (dist.isClient()) {
            ClientState.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent e) {
        // Optional, so players and servers without the mod can still join each other.
        PayloadRegistrar r = e.registrar("1").optional();
        r.playToServer(LocateRequest.TYPE, LocateRequest.STREAM_CODEC, LocateRequest::handle);
        r.playToClient(LocateReply.TYPE, LocateReply.STREAM_CODEC, LocateReply::handle);
    }

    /** At most 20 lookups a second per player. */
    static boolean allow(ServerPlayer p) {
        long now = System.currentTimeMillis();
        synchronized (RATE) {
            long[] r = RATE.computeIfAbsent(p.getUUID(), k -> new long[] { now, 0 });
            if (now - r[0] > 1000) {
                r[0] = now;
                r[1] = 0;
            }
            return ++r[1] <= 20;
        }
    }
}
