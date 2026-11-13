package com.claudecode.storagelocator;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.claudecode.storagelocator.client.ClientState;

/** Server to client on login: this server has the mod, so tooltips may ask it. */
public final class Hello implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static final class Handler implements IMessageHandler<Hello, IMessage> {
        @Override
        public IMessage onMessage(Hello msg, MessageContext ctx) {
            ClientState.serverHasMod = true;
            return null;
        }
    }
}
