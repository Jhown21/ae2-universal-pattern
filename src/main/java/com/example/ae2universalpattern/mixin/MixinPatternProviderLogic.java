package com.example.ae2universalpattern.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.example.ae2universalpattern.crafting.IWildcardPatternHolder;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import com.example.ae2universalpattern.item.WildcardPatternItem;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MixinPatternProviderLogic implements IWildcardPatternHolder {

    @Unique
    private static final Logger ae2universalpattern$LOGGER = LogUtils.getLogger();

    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow @Final private AppEngInternalInventory patternInventory;
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private Set<AEKey> patternInputs;

    @Unique
    private boolean ae2universalpattern$hasWildcard = false;

    @Unique
    private final List<IPatternDetails> ae2universalpattern$dynamicPatterns = new ArrayList<>();

    @Inject(
            method = "updatePatterns",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onUpdatePatterns(CallbackInfo ci) {
        boolean hasWildcard = false;
        for (var stack : this.patternInventory) {
            if (!stack.isEmpty() && stack.getItem() instanceof WildcardPatternItem) {
                hasWildcard = true;
                break;
            }
        }
        this.ae2universalpattern$hasWildcard = hasWildcard;

        if (hasWildcard) {
            ae2universalpattern$LOGGER.info("[AE2UniversalPattern] Detected Wildcard Pattern inside Pattern Provider at {}",
                    this.host.getBlockEntity() != null ? this.host.getBlockEntity().getBlockPos() : "unknown");
            WildcardProviderManager.registerHolder(this);
        } else {
            WildcardProviderManager.unregisterHolder(this);
            this.patterns.removeAll(this.ae2universalpattern$dynamicPatterns);
            this.ae2universalpattern$dynamicPatterns.clear();
        }
    }

    @Inject(
            method = "onMainNodeStateChanged",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onMainNodeStateChanged(CallbackInfo ci) {
        if (this.ae2universalpattern$hasWildcard && this.mainNode.getGrid() != null) {
            WildcardProviderManager.registerHolder(this);
        }
    }

    @Inject(
            method = "pushPattern",
            at = @At("HEAD")
    )
    private void ae2universalpattern$onPushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        if (this.ae2universalpattern$hasWildcard && patternDetails instanceof AECraftingPattern) {
            if (!this.patterns.contains(patternDetails)) {
                this.patterns.add(patternDetails);
            }
        }
        ae2universalpattern$LOGGER.info("[AE2UniversalPattern] PatternProvider pushPattern HEAD for: {}", patternDetails.getOutputs());
    }

    @Inject(
            method = "pushPattern",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onPushPatternReturn(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        ae2universalpattern$LOGGER.info("[AE2UniversalPattern] PatternProvider pushPattern RETURN: {} for {}", cir.getReturnValue(), patternDetails.getOutputs());
    }

    @Override
    public boolean ae2universalpattern$hasWildcardPattern() {
        return this.ae2universalpattern$hasWildcard;
    }

    @Override
    public void ae2universalpattern$setDynamicPatterns(List<IPatternDetails> newDynamicPatterns) {
        if (!this.ae2universalpattern$hasWildcard) {
            return;
        }

        this.patterns.removeAll(this.ae2universalpattern$dynamicPatterns);
        this.ae2universalpattern$dynamicPatterns.clear();

        this.patternInputs.clear();
        for (var p : this.patterns) {
            for (var iinput : p.getInputs()) {
                for (var inputCandidate : iinput.getPossibleInputs()) {
                    this.patternInputs.add(inputCandidate.what().dropSecondary());
                }
            }
        }

        if (newDynamicPatterns != null && !newDynamicPatterns.isEmpty()) {
            this.ae2universalpattern$dynamicPatterns.addAll(newDynamicPatterns);
            for (var pattern : newDynamicPatterns) {
                if (!this.patterns.contains(pattern)) {
                    this.patterns.add(pattern);
                }
                for (var iinput : pattern.getInputs()) {
                    for (var inputCandidate : iinput.getPossibleInputs()) {
                        this.patternInputs.add(inputCandidate.what().dropSecondary());
                    }
                }
            }
        }

        if (this.mainNode.getNode() != null && this.mainNode.getGrid() != null) {
            ICraftingProvider.requestUpdate(this.mainNode);
        }
    }

    @Override
    public List<IPatternDetails> ae2universalpattern$getDynamicPatterns() {
        return this.ae2universalpattern$dynamicPatterns;
    }

    @Override
    public BlockEntity ae2universalpattern$getBlockEntity() {
        return this.host.getBlockEntity();
    }

    @Override
    public IGrid ae2universalpattern$getGrid() {
        return this.mainNode != null ? this.mainNode.getGrid() : null;
    }
}
