package com.claudecode.storagelocator.mixins;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.claudecode.storagelocator.ClusterStatus;

import gregtech.common.tileentities.storage.MTEDigitalChestBase;
import gregtech.common.tileentities.storage.MTEDigitalTankBase;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * WAILA line for Super/Quantum Chests and Tanks: is this block in AE, through which storage bus, and how far from it.
 * The server writes the line into the WAILA data; a client with this mod shows it.
 */
@Mixin(value = { MTEDigitalChestBase.class, MTEDigitalTankBase.class }, remap = false)
public abstract class DigitalStorageStatusMixin {

    @Inject(method = "getWailaNBTData", at = @At("TAIL"))
    private void ae2storagelocator$statusNbt(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world,
        int x, int y, int z, CallbackInfo ci) {
        String s = ClusterStatus.describe(world, x, y, z);
        if (s != null) tag.setString("ae2slStatus", s);
        String lock = ClusterStatus.lockLine(world, x, y, z);
        if (lock != null) tag.setString("ae2slLock", lock);
    }

    @Inject(method = "getWailaBody", at = @At("TAIL"))
    private void ae2storagelocator$statusBody(ItemStack stack, List<String> tip, IWailaDataAccessor acc,
        IWailaConfigHandler cfg, CallbackInfo ci) {
        NBTTagCompound tag = acc.getNBTData();
        if (tag != null && tag.hasKey("ae2slStatus")) tip.add(tag.getString("ae2slStatus"));
        if (tag != null && tag.hasKey("ae2slLock")) tip.add(tag.getString("ae2slLock"));
    }
}
