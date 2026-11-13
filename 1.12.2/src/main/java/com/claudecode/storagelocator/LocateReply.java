package com.claudecode.storagelocator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import appeng.api.storage.data.IAEStack;

import com.claudecode.storagelocator.client.ClientState;

/**
 * Server to client: the tooltip lines for one stack (empty when the open screen isn't on a network), and the blocks
 * holding it in the player's dimension.
 */
public final class LocateReply implements IMessage {

    public IAEStack<?> what;
    public List<ITextComponent> lines = new ArrayList<>();
    public boolean highlight;
    public List<BlockPos> positions = new ArrayList<>();

    public LocateReply() {
    }

    public LocateReply(IAEStack<?> what, List<ITextComponent> lines, boolean highlight, List<BlockPos> positions) {
        this.what = what;
        this.lines = lines;
        this.highlight = highlight;
        this.positions = positions;
    }

    @Override
    public void toBytes(ByteBuf b) {
        PacketBuffer buf = new PacketBuffer(b);
        Stacks.write(buf, what);
        int n = Math.min(lines.size(), 32);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeTextComponent(lines.get(i));
        buf.writeBoolean(highlight);
        int m = Math.min(positions.size(), 4096);
        buf.writeVarInt(m);
        for (int i = 0; i < m; i++) buf.writeBlockPos(positions.get(i));
    }

    @Override
    public void fromBytes(ByteBuf b) {
        PacketBuffer buf = new PacketBuffer(b);
        what = Stacks.read(buf);
        if (what == null) return;
        try {
            int n = Math.min(buf.readVarInt(), 32);
            for (int i = 0; i < n; i++) lines.add(buf.readTextComponent());
        } catch (IOException e) {
            what = null;
            return;
        }
        highlight = buf.readBoolean();
        int m = Math.min(buf.readVarInt(), 4096);
        for (int i = 0; i < m; i++) positions.add(buf.readBlockPos());
    }

    public static final class Handler implements IMessageHandler<LocateReply, IMessage> {
        @Override
        public IMessage onMessage(LocateReply msg, MessageContext ctx) {
            if (msg.what != null) ClientState.schedule(msg);
            return null;
        }
    }
}
