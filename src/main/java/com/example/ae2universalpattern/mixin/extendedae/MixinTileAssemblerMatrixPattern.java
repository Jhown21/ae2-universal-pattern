package com.example.ae2universalpattern.mixin.extendedae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.crafting.pattern.AECraftingPattern;
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

@Mixin(targets = "com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern", remap = false)
public abstract class MixinTileAssemblerMatrixPattern implements IWildcardPatternHolder {

    @Unique
    private static final Logger ae2universalpattern$LOGGER = LogUtils.getLogger();

    @Shadow @Final private AppEngInternalInventory patternInventory;
    @Shadow @Final private List<IPatternDetails> patterns;

    @Unique
    private boolean ae2universalpattern$hasWildcard = false;

    @Unique
    private final List<IPatternDetails> ae2universalpattern$dynamicPatterns = new ArrayList<>();

    @Unique
    private IManagedGridNode ae2universalpattern$getMainNode() {
        if ((Object) this instanceof AENetworkedBlockEntity networked) {
            return networked.getMainNode();
        }
        return null;
    }

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
            ae2universalpattern$LOGGER.info("[AE2UniversalPattern] Detected Wildcard Pattern inside Assembler Matrix Pattern block!");
            WildcardProviderManager.registerHolder(this);
        } else {
            WildcardProviderManager.unregisterHolder(this);
            this.patterns.removeAll(this.ae2universalpattern$dynamicPatterns);
            this.ae2universalpattern$dynamicPatterns.clear();
        }
    }

    @Inject(
            method = "onReady",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onReady(CallbackInfo ci) {
        var node = this.ae2universalpattern$getMainNode();
        if (this.ae2universalpattern$hasWildcard && node != null && node.getGrid() != null) {
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
        ae2universalpattern$LOGGER.info("[AE2UniversalPattern] AssemblerMatrix pushPattern HEAD for: {}", patternDetails.getOutputs());
    }

    @Inject(
            method = "pushPattern",
            at = @At("RETURN")
    )
    private void ae2universalpattern$onPushPatternReturn(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        ae2universalpattern$LOGGER.info("[AE2UniversalPattern] AssemblerMatrix pushPattern RETURN: {} for {}", cir.getReturnValue(), patternDetails.getOutputs());
    }

    @Inject(
            method = "clearContent",
            at = @At("HEAD")
    )
    private void ae2universalpattern$onClearContent(CallbackInfo ci) {
        WildcardProviderManager.unregisterHolder(this);
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

        if (newDynamicPatterns != null && !newDynamicPatterns.isEmpty()) {
            this.ae2universalpattern$dynamicPatterns.addAll(newDynamicPatterns);
            for (var pattern : newDynamicPatterns) {
                if (!this.patterns.contains(pattern)) {
                    this.patterns.add(pattern);
                }
            }
        }

        var node = this.ae2universalpattern$getMainNode();
        if (node != null && node.getNode() != null && node.getGrid() != null) {
            ICraftingProvider.requestUpdate(node);
        }
    }

    @Override
    public List<IPatternDetails> ae2universalpattern$getDynamicPatterns() {
        return this.ae2universalpattern$dynamicPatterns;
    }

    @Override
    public BlockEntity ae2universalpattern$getBlockEntity() {
        return (BlockEntity) (Object) this;
    }

    @Override
    public IGrid ae2universalpattern$getGrid() {
        var node = this.ae2universalpattern$getMainNode();
        return node != null ? node.getGrid() : null;
    }
}
