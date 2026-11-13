package com.claudecode.storagelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hover an item or fluid in an AE2 terminal and its tooltip lists where the network keeps it: which drive cells, ME
 * chests and storage-bus targets hold it, and how many each. The client asks the server once per stack (cached a few
 * seconds); the server answers from the network of the terminal the player has open. Pressing the highlight key
 * (Insert) on an entry closes the terminal and outlines those blocks in the world.
 * <p>
 * Optional on either side: without it on the server the tooltip just doesn't get the extra lines.
 */
public class StorageLocator implements ModInitializer {

    public static final String MOD_ID = "ae2storagelocator";
    public static final Logger LOG = LogUtils.getLogger();

    /** Client to server: {@link LocateRequest}. */
    public static final ResourceLocation LOCATE = new ResourceLocation(MOD_ID, "locate");
    /** Server to client: {@link LocateReply}. */
    public static final ResourceLocation LOCATED = new ResourceLocation(MOD_ID, "located");

    private static final Map<UUID, long[]> RATE = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(LOCATE, (server, player, handler, buf, sender) -> {
            LocateRequest req = LocateRequest.read(buf);
            server.execute(() -> answer(player, req));
        });
    }

    private static void answer(ServerPlayer p, LocateRequest req) {
        if (req.what() == null || !allow(p)) return;
        try {
            var found = Locator.locate(p, req.what());
            FriendlyByteBuf out = PacketByteBufs.create();
            new LocateReply(req.what(), found.lines(), req.highlight(), found.positions()).write(out);
            ServerPlayNetworking.send(p, LOCATED, out);
            if (req.highlight() && !found.positions().isEmpty()) p.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            LOG.warn("ae2storagelocator: lookup failed", e);
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
