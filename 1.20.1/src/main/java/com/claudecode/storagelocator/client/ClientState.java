package com.claudecode.storagelocator.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.hooks.RenderBlockOutlineHook;

import com.claudecode.storagelocator.Config;
import com.claudecode.storagelocator.LocateReply;
import com.claudecode.storagelocator.LocateRequest;
import com.claudecode.storagelocator.StorageLocator;

/** Client side: the tooltip lines, the highlight key, and the in-world highlight. */
public final class ClientState {

    private ClientState() {
    }

    public static final KeyMapping HIGHLIGHT = new KeyMapping("key.ae2storagelocator.highlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, "key.categories.ae2storagelocator");

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private record Answer(long at, List<Component> lines) {
    }

    private static final Map<AEKey, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<AEKey, Long> ASKED = new ConcurrentHashMap<>();

    private static volatile List<BlockPos> highlighted = List.of();
    private static volatile ResourceKey<Level> highlightedIn;
    private static volatile long highlightedUntil;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(HIGHLIGHT));
        MinecraftForge.EVENT_BUS.addListener(ClientState::onTooltip);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onKey);
        MinecraftForge.EVENT_BUS.addListener(ClientState::onRenderLevel);
    }

    public static void onReply(LocateReply r) {
        ANSWERS.put(r.what(), new Answer(System.currentTimeMillis(), r.lines()));
        ASKED.remove(r.what());
        if (!r.highlight()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int seconds = Config.HIGHLIGHT_SECONDS.get();
        if (r.positions().isEmpty()) {
            mc.player.displayClientMessage(Component.translatable("ae2storagelocator.nothing_to_highlight",
                    r.what().getDisplayName()).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        highlighted = List.copyOf(r.positions());
        highlightedIn = mc.player.level().dimension();
        highlightedUntil = System.currentTimeMillis() + seconds * 1000L;
        mc.player.displayClientMessage(Component.translatable("ae2storagelocator.highlighted",
                r.positions().size(), r.what().getDisplayName(), seconds).withStyle(ChatFormatting.GRAY), false);
    }

    /** False when the server doesn't have the mod, so there is nobody to ask. */
    private static boolean serverHasMod() {
        var conn = Minecraft.getInstance().getConnection();
        return conn != null && StorageLocator.NET.isRemotePresent(conn.getConnection());
    }

    /** Tooltip lines for a terminal entry, asking the server when we have nothing fresh. */
    private static List<Component> linesFor(AEKey what) {
        if (!serverHasMod()) return Collections.emptyList();
        long now = System.currentTimeMillis();
        Answer a = ANSWERS.get(what);
        if (a == null || now - a.at > FRESH_MS) {
            Long asked = ASKED.get(what);
            if (asked == null || now - asked > RETRY_MS) {
                ASKED.put(what, now);
                if (ANSWERS.size() > 2048) ANSWERS.clear();
                StorageLocator.NET.sendToServer(new LocateRequest(what, false));
            }
        }
        if (a != null) return a.lines;
        return List.of(Component.translatable("ae2storagelocator.locating").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The key of the terminal entry under the mouse, when the tooltip shown is that entry's. */
    private static AEKey hoveredKey() {
        if (!(Minecraft.getInstance().screen instanceof MEStorageScreen<?> screen)) return null;
        if (!(screen.getSlotUnderMouse() instanceof RepoSlot slot)) return null;
        if (!screen.getMenu().getCarried().isEmpty()) return null;
        var entry = slot.getEntry();
        return entry == null ? null : entry.getWhat();
    }

    private static void onTooltip(RenderTooltipEvent.GatherComponents e) {
        AEKey what = hoveredKey();
        if (what == null) return;
        // Item entries are drawn with their stack; make sure this tooltip is the hovered entry's.
        if (what instanceof AEItemKey item && !e.getItemStack().isEmpty() && !item.matches(e.getItemStack())) return;
        for (Component line : linesFor(what)) e.getTooltipElements().add(Either.<FormattedText, TooltipComponent>left(line));
    }

    private static void onKey(ScreenEvent.KeyPressed.Pre e) {
        if (!HIGHLIGHT.isActiveAndMatches(InputConstants.getKey(e.getKeyCode(), e.getScanCode()))) return;
        AEKey what = hoveredKey();
        if (what == null || !serverHasMod()) return;
        StorageLocator.NET.sendToServer(new LocateRequest(what, true));
        e.setCanceled(true);
    }

    private static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        List<BlockPos> blocks = highlighted;
        if (blocks.isEmpty() || mc.level == null) return;
        if (System.currentTimeMillis() > highlightedUntil || mc.level.dimension() != highlightedIn) {
            if (System.currentTimeMillis() > highlightedUntil) highlighted = List.of();
            return;
        }
        // Blink like AE2's own find-in-storage highlight did on 1.7.10.
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        // Drawn twice: bright where the block is in view, dimmer through whatever is in front of it.
        for (BlockPos pos : blocks) {
            AABB box = new AABB(pos).inflate(0.005);
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderBlockOutlineHook.LINES_BEHIND_BLOCK), box,
                    1f, 0.55f, 0f, alpha * 0.5f);
            LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, 1f, 0.55f, 0f, alpha);
        }
        pose.popPose();
        buffers.endBatch(RenderBlockOutlineHook.LINES_BEHIND_BLOCK);
        buffers.endBatch(RenderType.lines());
    }
}
