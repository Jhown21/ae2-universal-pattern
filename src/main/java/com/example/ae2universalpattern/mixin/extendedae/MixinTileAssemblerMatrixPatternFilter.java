package com.example.ae2universalpattern.mixin.extendedae;

import appeng.api.inventories.InternalInventory;
import com.example.ae2universalpattern.item.WildcardPatternItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern$Filter", remap = false)
public abstract class MixinTileAssemblerMatrixPatternFilter {

    @Inject(method = "allowInsert", at = @At("HEAD"), cancellable = true)
    private void ae2universalpattern$allowWildcard(InternalInventory inv, int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof WildcardPatternItem) {
            cir.setReturnValue(true);
        }
    }
}
