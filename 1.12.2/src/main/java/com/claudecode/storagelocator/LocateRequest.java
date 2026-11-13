package com.claudecode.storagelocator;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.storage.data.IAEStack;

/**
 * Client to server: where is this item or fluid kept in the network of my open terminal? With highlight set, the
 * server also closes the terminal and the client outlines the blocks.
 */
public final class LocateRequest implements IMessage {

    IAEStack<?> what;
    boolean highlight;

    public LocateRequest() {
    }

    public LocateRequest(IAEStack<?> what, boolean highlight) {
        this.what = what;
        this.highlight = highlight;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        Stacks.write(buf, what);
        buf.writeBoolean(highlight);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        what = Stacks.read(buf);
        highlight = what != null && buf.readBoolean();
    }

    public static final class Handler implements IMessageHandler<LocateRequest, IMessage> {
        @Override
        public IMessage onMessage(LocateRequest msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> answer(msg, p));
            return null;
        }
    }

    private static void answer(LocateRequest msg, EntityPlayerMP p) {
        if (msg.what == null || !StorageLocator.allow(p)) return;
        try {
            Locator.Result found = Locator.locate(p, msg.what);
            StorageLocator.NET.sendTo(new LocateReply(msg.what, found.lines, msg.highlight, found.positions), p);
            if (msg.highlight && !found.positions.isEmpty()) p.closeScreen();
        } catch (RuntimeException | LinkageError e) {
            StorageLocator.LOG.warn("ae2storagelocator: lookup failed", e);
        }
    }
}
