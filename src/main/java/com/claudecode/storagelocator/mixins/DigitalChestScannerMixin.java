package com.claudecode.storagelocator.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.claudecode.storagelocator.ClusterStatus;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.tileentities.storage.MTEDigitalChestBase;

/** Portable scanner on a Super/Quantum Chest: adds the "In AE / Not in AE" line. */
@Mixin(value = MTEDigitalChestBase.class, remap = false)
public abstract class DigitalChestScannerMixin {

    @Inject(method = "getInfoData", at = @At("RETURN"), cancellable = true)
    private void ae2storagelocator$scanner(CallbackInfoReturnable<String[]> cir) {
        IGregTechTileEntity base = ((MTEDigitalChestBase) (Object) this).getBaseMetaTileEntity();
        if (base == null) return;
        String status = ClusterStatus.describe(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (status == null) return;
        String[] old = cir.getReturnValue();
        String[] out = new String[(old == null ? 0 : old.length) + 1];
        if (old != null) System.arraycopy(old, 0, out, 0, old.length);
        out[out.length - 1] = status;
        cir.setReturnValue(out);
    }
}
