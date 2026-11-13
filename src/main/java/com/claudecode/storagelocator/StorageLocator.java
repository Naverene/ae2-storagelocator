package com.claudecode.storagelocator;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

/**
 * Hover an item or fluid in an AE2 terminal and its tooltip lists where the network keeps it: which drive cells, ME
 * chests and storage-bus targets (drawers, chests, tanks...) hold it and how many each. The client asks the server
 * once per stack (cached a few seconds); the server answers from the network of the terminal you have open.
 * Optional on either side: without it on the server, the tooltip just doesn't get the extra lines.
 */
@Mod(
    modid = "ae2storagelocator",
    name = "AE2 Storage Locator",
    version = Tags.VERSION,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:appliedenergistics2;after:gregtech;after:gtstorageclusters")
public class StorageLocator {

    public static final Logger LOG = LogManager.getLogger("ae2storagelocator");
    public static SimpleNetworkWrapper NET;

    /** Requests read off the network thread, answered on the server thread. */
    static final ConcurrentLinkedQueue<Object[]> PENDING = new ConcurrentLinkedQueue<>();
    private static final Map<String, long[]> RATE = new HashMap<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("ae2storageloc");
        NET.registerMessage(LocateRequest.Handler.class, LocateRequest.class, 0, Side.SERVER);
        NET.registerMessage(LocateReply.Handler.class, LocateReply.class, 1, Side.CLIENT);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    /** At most 20 lookups a second per player. */
    static boolean allow(EntityPlayerMP p) {
        long now = System.currentTimeMillis();
        synchronized (RATE) {
            long[] r = RATE.computeIfAbsent(p.getCommandSenderName(), k -> new long[] { now, 0 });
            if (now - r[0] > 1000) {
                r[0] = now;
                r[1] = 0;
            }
            return ++r[1] <= 20;
        }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (int i = 0; i < 64; i++) {
            Object[] job = PENDING.poll();
            if (job == null) break;
            EntityPlayerMP p = (EntityPlayerMP) job[0];
            LocateRequest req = (LocateRequest) job[1];
            try {
                NET.sendTo(new LocateReply(req.key, Locator.locate(p, req)), p);
            } catch (Throwable t) {
                LOG.warn("ae2storagelocator: lookup failed", t);
            }
        }
    }
}
