package com.claudecode.storagelocator.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.hooks.RenderBlockOutlineHook;

import com.claudecode.storagelocator.Config;
import com.claudecode.storagelocator.LocateReply;
import com.claudecode.storagelocator.LocateRequest;
import com.claudecode.storagelocator.StorageLocator;

/** Client side: the tooltip lines, the highlight key, and the in-world highlight. */
public final class ClientState implements ClientModInitializer {

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

    @Override
    public void onInitializeClient() {
        Config.load();
        KeyBindingHelper.registerKeyBinding(HIGHLIGHT);
        ClientPlayNetworking.registerGlobalReceiver(StorageLocator.LOCATED, (client, handler, buf, sender) -> {
            LocateReply reply = LocateReply.read(buf);
            client.execute(() -> onReply(reply));
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof MEStorageScreen<?>) {
                ScreenKeyboardEvents.allowKeyPress(screen)
                        .register((s, key, scancode, modifiers) -> !onKey(key, scancode));
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ClientState::onRenderLevel);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!highlighted.isEmpty() && System.currentTimeMillis() > highlightedUntil) highlighted = List.of();
        });
    }

    private static void onReply(LocateReply r) {
        if (r.what() == null) return;
        ANSWERS.put(r.what(), new Answer(System.currentTimeMillis(), r.lines()));
        ASKED.remove(r.what());
        if (!r.highlight()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int seconds = Config.highlightSeconds;
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

    private static void send(LocateRequest req) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        req.write(buf);
        ClientPlayNetworking.send(StorageLocator.LOCATE, buf);
    }

    /** Tooltip lines for a terminal entry, asking the server when we have nothing fresh. */
    private static List<Component> linesFor(AEKey what) {
        // False when the server doesn't have the mod, so there is nobody to ask.
        if (!ClientPlayNetworking.canSend(StorageLocator.LOCATE)) return Collections.emptyList();
        long now = System.currentTimeMillis();
        Answer a = ANSWERS.get(what);
        if (a == null || now - a.at > FRESH_MS) {
            Long asked = ASKED.get(what);
            if (asked == null || now - asked > RETRY_MS) {
                ASKED.put(what, now);
                if (ANSWERS.size() > 2048) ANSWERS.clear();
                send(new LocateRequest(what, false));
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

    /** The tooltip AE2 is about to draw for the hovered entry, with where it is stored added. */
    public static List<Component> withLocations(List<Component> lines) {
        AEKey what = hoveredKey();
        if (what == null) return lines;
        List<Component> extra = linesFor(what);
        if (extra.isEmpty()) return lines;
        List<Component> out = new ArrayList<>(lines);
        out.addAll(extra);
        return out;
    }

    /** True when the key press was the highlight key on a terminal entry, and so was used up. */
    private static boolean onKey(int key, int scancode) {
        if (!HIGHLIGHT.matches(key, scancode)) return false;
        AEKey what = hoveredKey();
        if (what == null || !ClientPlayNetworking.canSend(StorageLocator.LOCATE)) return false;
        send(new LocateRequest(what, true));
        return true;
    }

    private static void onRenderLevel(WorldRenderContext ctx) {
        var mc = Minecraft.getInstance();
        List<BlockPos> blocks = highlighted;
        if (blocks.isEmpty() || mc.level == null || mc.level.dimension() != highlightedIn) return;
        if (System.currentTimeMillis() > highlightedUntil) return;
        // Blink like AE2's own find-in-storage highlight did on 1.7.10.
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        Vec3 cam = ctx.camera().getPosition();
        PoseStack pose = ctx.matrixStack();
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
