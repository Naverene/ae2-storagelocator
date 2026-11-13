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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.bus.api.IEventBus;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.render.AERenderTypes;

import com.claudecode.storagelocator.Config;
import com.claudecode.storagelocator.LocateReply;
import com.claudecode.storagelocator.LocateRequest;
import com.claudecode.storagelocator.StorageLocator;

/** Client side: the tooltip lines, the highlight key, and the in-world highlight. */
public final class ClientState {

    private ClientState() {
    }

    /** Shown in Options > Controls as "AE2 Storage Locator" (key.category.ae2storagelocator.main). */
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(StorageLocator.MOD_ID, "main"));

    public static final KeyMapping HIGHLIGHT = new KeyMapping("key.ae2storagelocator.highlight",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_INSERT, CATEGORY);

    /** A whole block, drawn as an outline at each highlighted position. */
    private static final VoxelShape BLOCK = Shapes.create(-0.005, -0.005, -0.005, 1.005, 1.005, 1.005);

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private record Answer(long at, List<Component> lines) {
    }

    private static final Map<AEKey, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<AEKey, Long> ASKED = new ConcurrentHashMap<>();

    private static volatile List<BlockPos> highlighted = List.of();
    private static volatile ResourceKey<Level> highlightedIn;
    private static volatile long highlightedUntil;

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.registerCategory(CATEGORY);
            e.register(HIGHLIGHT);
        });
        NeoForge.EVENT_BUS.addListener(ClientState::onTooltip);
        NeoForge.EVENT_BUS.addListener(ClientState::onKey);
        NeoForge.EVENT_BUS.addListener(ClientState::onRenderLevel);
    }

    public static void onReply(LocateReply r) {
        ANSWERS.put(r.what(), new Answer(System.currentTimeMillis(), r.lines()));
        ASKED.remove(r.what());
        if (!r.highlight()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int seconds = Config.HIGHLIGHT_SECONDS.get();
        if (r.positions().isEmpty()) {
            mc.player.sendSystemMessage(Component.translatable("ae2storagelocator.nothing_to_highlight",
                    r.what().getDisplayName()).withStyle(ChatFormatting.GRAY));
            return;
        }
        highlighted = List.copyOf(r.positions());
        highlightedIn = mc.player.level().dimension();
        highlightedUntil = System.currentTimeMillis() + seconds * 1000L;
        mc.player.sendSystemMessage(Component.translatable("ae2storagelocator.highlighted",
                r.positions().size(), r.what().getDisplayName(), seconds).withStyle(ChatFormatting.GRAY));
    }

    /** False when the server doesn't have the mod, so there is nobody to ask. */
    private static boolean serverHasMod() {
        var conn = Minecraft.getInstance().getConnection();
        return conn != null && conn.hasChannel(LocateRequest.TYPE);
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
                ClientPacketDistributor.sendToServer(new LocateRequest(what, false));
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
        if (!HIGHLIGHT.isActiveAndMatches(InputConstants.getKey(e.getKeyEvent()))) return;
        AEKey what = hoveredKey();
        if (what == null || !serverHasMod()) return;
        ClientPacketDistributor.sendToServer(new LocateRequest(what, true));
        e.setCanceled(true);
    }

    /** The highlighted blocks, or none once they have expired or the player is in another dimension. */
    private static List<BlockPos> visibleHighlights() {
        var mc = Minecraft.getInstance();
        if (highlighted.isEmpty() || mc.level == null) return List.of();
        if (System.currentTimeMillis() > highlightedUntil) {
            highlighted = List.of();
            return List.of();
        }
        return mc.level.dimension() == highlightedIn ? highlighted : List.of();
    }

    /** Orange, blinking like AE2's own find-in-storage highlight did on 1.7.10. */
    private static int color(float dim) {
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        return ARGB.color((int) (255 * alpha * dim), 255, 140, 0);
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks e) {
        List<BlockPos> blocks = visibleHighlights();
        if (blocks.isEmpty()) return;
        var mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // Drawn twice: bright where the block is in view, dimmer through whatever is in front of it.
        for (BlockPos pos : blocks) {
            double x = pos.getX() - cam.x, y = pos.getY() - cam.y, z = pos.getZ() - cam.z;
            ShapeRenderer.renderShape(pose, buffers.getBuffer(AERenderTypes.LINES_BEHIND_BLOCK), BLOCK, x, y, z,
                    color(0.5f), 2f);
            ShapeRenderer.renderShape(pose, buffers.getBuffer(RenderTypes.lines()), BLOCK, x, y, z, color(1f), 2f);
        }
        buffers.endBatch(AERenderTypes.LINES_BEHIND_BLOCK);
        buffers.endBatch(RenderTypes.lines());
    }
}
