package com.example.ae2universalpattern.mixin.extendedae;

import com.example.ae2universalpattern.item.WildcardPatternItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "com.glodblock.github.extendedae.client.gui.GuiAssemblerMatrix", remap = false)
public abstract class MixinGuiAssemblerMatrix {

    @Inject(method = "itemStackMatchesSearchTerm", at = @At("HEAD"), cancellable = true)
    private void ae2universalpattern$matchWildcard(ItemStack stack, List<String> terms, CallbackInfoReturnable<Boolean> cir) {
        if (stack != null && !stack.isEmpty() && stack.getItem() instanceof WildcardPatternItem) {
            cir.setReturnValue(true);
        }
    }
}
