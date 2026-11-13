package com.claudecode.storagelocator.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Slot;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.SlotFluidME;
import appeng.client.me.SlotME;

import com.claudecode.storagelocator.Config;
import com.claudecode.storagelocator.LocateReply;
import com.claudecode.storagelocator.LocateRequest;
import com.claudecode.storagelocator.StorageLocator;

/** Client side: the tooltip lines, the highlight key, and the in-world highlight. */
public final class ClientState {

    private ClientState() {
    }

    public static final KeyBinding HIGHLIGHT = new KeyBinding("key.ae2storagelocator.highlight", Keyboard.KEY_INSERT,
            "key.categories.ae2storagelocator");

    /** Set when the server says hello on login, so we only ask servers that have the mod. */
    public static volatile boolean serverHasMod;

    private static final long FRESH_MS = 5000, RETRY_MS = 1500;

    private static final class Answer {
        final long at;
        final List<String> lines;

        Answer(long at, List<String> lines) {
            this.at = at;
            this.lines = lines;
        }
    }

    // AE2 stacks compare and hash by type only (not amount), so they work as keys.
    private static final Map<IAEStack<?>, Answer> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<IAEStack<?>, Long> ASKED = new ConcurrentHashMap<>();

    private static volatile List<BlockPos> highlighted = Collections.emptyList();
    private static volatile int highlightedIn;
    private static volatile long highlightedUntil;
    private static boolean redrawing;

    public static void init() {
        ClientRegistry.registerKeyBinding(HIGHLIGHT);
        MinecraftForge.EVENT_BUS.register(ClientState.class);
    }

    @SubscribeEvent
    public static void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent e) {
        serverHasMod = false;
    }

    @SubscribeEvent
    public static void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        serverHasMod = false;
        ANSWERS.clear();
        ASKED.clear();
        highlighted = Collections.emptyList();
    }

    public static void schedule(LocateReply r) {
        Minecraft.getMinecraft().addScheduledTask(() -> onReply(r));
    }

    private static String nameOf(IAEStack<?> s) {
        if (s instanceof IAEFluidStack) return ((IAEFluidStack) s).getFluidStack().getLocalizedName();
        return s.asItemStackRepresentation().getDisplayName();
    }

    private static ITextComponent gray(ITextComponent c) {
        c.getStyle().setColor(TextFormatting.GRAY);
        return c;
    }

    private static void onReply(LocateReply r) {
        List<String> lines = new ArrayList<>(r.lines.size());
        for (ITextComponent c : r.lines) lines.add(c.getFormattedText());
        ANSWERS.put(r.what, new Answer(System.currentTimeMillis(), lines));
        ASKED.remove(r.what);
        if (!r.highlight) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        int seconds = Config.highlightSeconds;
        if (r.positions.isEmpty()) {
            mc.player.sendStatusMessage(gray(new TextComponentTranslation("ae2storagelocator.nothing_to_highlight",
                    nameOf(r.what))), false);
            return;
        }
        highlighted = Collections.unmodifiableList(new ArrayList<>(r.positions));
        highlightedIn = mc.player.world.provider.getDimension();
        highlightedUntil = System.currentTimeMillis() + seconds * 1000L;
        mc.player.sendStatusMessage(gray(new TextComponentTranslation("ae2storagelocator.highlighted",
                r.positions.size(), nameOf(r.what), seconds)), false);
    }

    /** Tooltip lines for a terminal entry, asking the server when we have nothing fresh. */
    private static List<String> linesFor(IAEStack<?> what) {
        if (!serverHasMod) return Collections.emptyList();
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
        return Collections.singletonList(TextFormatting.DARK_GRAY
                + new TextComponentTranslation("ae2storagelocator.locating").getUnformattedText());
    }

    /** The stack of the terminal entry under the mouse, when the terminal would show that entry's tooltip. */
    @Nullable
    private static IAEStack<?> hovered() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(mc.currentScreen instanceof GuiContainer) || mc.player == null) return null;
        if (!mc.player.inventory.getItemStack().isEmpty()) return null;
        Slot slot = ((GuiContainer) mc.currentScreen).getSlotUnderMouse();
        if (slot instanceof SlotME) return ((SlotME) slot).getAEStack();
        if (slot instanceof SlotFluidME) return ((SlotFluidME) slot).getAEFluidStack();
        return null;
    }

    /** Item entries: AE2 builds their tooltip from the item's own, which fires this event. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent e) {
        IAEStack<?> what = hovered();
        if (!(what instanceof IAEItemStack) || !((IAEItemStack) what).isSameType(e.getItemStack())) return;
        e.getToolTip().addAll(linesFor(what));
    }

    /**
     * Fluid entries: AE2 builds their tooltip itself, and the event's lines are read-only, so draw the tooltip again
     * with the lines added and cancel the original.
     */
    @SubscribeEvent
    public static void onFluidTooltip(RenderTooltipEvent.Pre e) {
        if (redrawing) return;
        IAEStack<?> what = hovered();
        if (!(what instanceof IAEFluidStack) || !e.getStack().isEmpty()) return;
        List<String> extra = linesFor(what);
        if (extra.isEmpty()) return;
        List<String> lines = new ArrayList<>(e.getLines());
        lines.addAll(extra);
        e.setCanceled(true);
        redrawing = true;
        try {
            GuiUtils.drawHoveringText(lines, e.getX(), e.getY(), e.getScreenWidth(), e.getScreenHeight(),
                    e.getMaxWidth(), e.getFontRenderer());
        } finally {
            redrawing = false;
        }
    }

    @SubscribeEvent
    public static void onKey(GuiScreenEvent.KeyboardInputEvent.Pre e) {
        if (!Keyboard.getEventKeyState() || !HIGHLIGHT.isActiveAndMatches(Keyboard.getEventKey())) return;
        IAEStack<?> what = hovered();
        if (what == null || !serverHasMod) return;
        StorageLocator.NET.sendToServer(new LocateRequest(what, true));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        List<BlockPos> blocks = highlighted;
        Entity view = mc.getRenderViewEntity();
        if (blocks.isEmpty() || mc.world == null || view == null) return;
        if (System.currentTimeMillis() > highlightedUntil || mc.world.provider.getDimension() != highlightedIn) {
            if (System.currentTimeMillis() > highlightedUntil) highlighted = Collections.emptyList();
            return;
        }
        // Blink like AE2's own find-in-storage highlight did on 1.7.10.
        float alpha = (System.currentTimeMillis() / 500 % 2 == 0) ? 1f : 0.45f;
        float pt = e.getPartialTicks();
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(3f);
        GlStateManager.depthMask(false);
        // Drawn twice: dimmer through whatever is in front of the block, bright where it is in view.
        GlStateManager.disableDepth();
        for (BlockPos pos : blocks) {
            RenderGlobal.drawSelectionBoundingBox(box(pos, cx, cy, cz), 1f, 0.55f, 0f, alpha * 0.5f);
        }
        GlStateManager.enableDepth();
        for (BlockPos pos : blocks) {
            RenderGlobal.drawSelectionBoundingBox(box(pos, cx, cy, cz), 1f, 0.55f, 0f, alpha);
        }
        GlStateManager.depthMask(true);
        GlStateManager.glLineWidth(1f);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static AxisAlignedBB box(BlockPos pos, double cx, double cy, double cz) {
        return new AxisAlignedBB(pos).grow(0.005).offset(-cx, -cy, -cz);
    }
}
