package com.claudecode.storagelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

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

    private static final String PROTOCOL = "1";
    public static final SimpleChannel NET = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL,
            StorageLocator::compatible,
            StorageLocator::compatible);

    /** Players and servers without the mod can still join each other; they just don't talk to it. */
    private static boolean compatible(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ABSENT.equals(version)
                || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    public StorageLocator() {
        NET.messageBuilder(LocateRequest.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(LocateRequest::write)
                .decoder(LocateRequest::read)
                .consumerMainThread(LocateRequest::handle)
                .add();
        NET.messageBuilder(LocateReply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LocateReply::write)
                .decoder(LocateReply::read)
                .consumerMainThread(LocateReply::handle)
                .add();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        // Either side alone loads fine and does nothing, so don't make the server list show it as incompatible.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientState.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
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
