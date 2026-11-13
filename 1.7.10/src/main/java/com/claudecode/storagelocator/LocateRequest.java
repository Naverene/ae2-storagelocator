package com.claudecode.storagelocator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Client to server: where is this item (or fluid, by name) kept in the network of my open terminal? */
public class LocateRequest implements IMessage {

    String key;
    ItemStack item; // null for a fluid
    String fluid; // null for an item

    public LocateRequest() {}

    LocateRequest(String key, ItemStack item, String fluid) {
        this.key = key;
        this.item = item;
        this.fluid = fluid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        key = ByteBufUtils.readUTF8String(buf);
        if (buf.readBoolean()) {
            NBTTagCompound t = ByteBufUtils.readTag(buf);
            item = t == null ? null : ItemStack.loadItemStackFromNBT(t);
        } else {
            fluid = ByteBufUtils.readUTF8String(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, key);
        buf.writeBoolean(item != null);
        if (item != null) ByteBufUtils.writeTag(buf, item.writeToNBT(new NBTTagCompound()));
        else ByteBufUtils.writeUTF8String(buf, fluid == null ? "" : fluid);
    }

    public static class Handler implements IMessageHandler<LocateRequest, IMessage> {

        @Override
        public IMessage onMessage(LocateRequest msg, MessageContext ctx) {
            if (msg.key == null || msg.key.length() > 512) return null;
            if (!StorageLocator.allow(ctx.getServerHandler().playerEntity)) return null;
            StorageLocator.PENDING.add(new Object[] { ctx.getServerHandler().playerEntity, msg });
            return null;
        }
    }
}
