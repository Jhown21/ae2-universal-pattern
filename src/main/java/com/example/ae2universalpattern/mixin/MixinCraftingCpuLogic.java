package com.example.ae2universalpattern.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.crafting.execution.CraftingCpuLogic;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class MixinCraftingCpuLogic {

    @Unique
    private static final Logger ae2universalpattern$LOGGER = LogUtils.getLogger();

    @Inject(
            method = "trySubmitJob",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onTrySubmitJob(
            IGrid grid,
            ICraftingPlan plan,
            IActionSource src,
            ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> cir
    ) {
        if (cir.getReturnValue() != null && cir.getReturnValue().successful() && plan != null && grid != null) {
            var patterns = plan.patternTimes().keySet();
            if (!patterns.isEmpty()) {
                ae2universalpattern$LOGGER.info("[AE2UniversalPattern] Crafting job submitted successfully for output: {}. Recording {} permanent pattern(s).",
                        plan.finalOutput() != null && plan.finalOutput().what() != null ? plan.finalOutput().what() : "unknown",
                        patterns.size());
                WildcardProviderManager.recordCraftedPatterns(grid, patterns);
            }
        }
    }
}
