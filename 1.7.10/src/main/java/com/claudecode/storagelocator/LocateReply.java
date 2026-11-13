package com.claudecode.storagelocator;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server to client: the tooltip lines for one request key (empty when the open GUI isn't on a network). */
public class LocateReply implements IMessage {

    String key;
    List<String> lines = new ArrayList<>();

    public LocateReply() {}

    LocateReply(String key, List<String> lines) {
        this.key = key;
        this.lines = lines;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        key = ByteBufUtils.readUTF8String(buf);
        int n = Math.min(buf.readUnsignedByte(), 32);
        for (int i = 0; i < n; i++) lines.add(ByteBufUtils.readUTF8String(buf));
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, key);
        int n = Math.min(lines.size(), 32);
        buf.writeByte(n);
        for (int i = 0; i < n; i++) ByteBufUtils.writeUTF8String(buf, lines.get(i));
    }

    public static class Handler implements IMessageHandler<LocateReply, IMessage> {

        @Override
        public IMessage onMessage(LocateReply msg, MessageContext ctx) {
            ClientCache.put(msg.key, msg.lines);
            return null;
        }
    }
}
