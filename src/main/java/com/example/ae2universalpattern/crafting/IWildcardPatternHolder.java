package com.example.ae2universalpattern.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public interface IWildcardPatternHolder {
    boolean ae2universalpattern$hasWildcardPattern();
    void ae2universalpattern$setDynamicPatterns(List<IPatternDetails> patterns);
    List<IPatternDetails> ae2universalpattern$getDynamicPatterns();
    BlockEntity ae2universalpattern$getBlockEntity();
    IGrid ae2universalpattern$getGrid();

    default boolean ae2universalpattern$isValid() {
        BlockEntity be = ae2universalpattern$getBlockEntity();
        return be != null && !be.isRemoved();
    }
}
