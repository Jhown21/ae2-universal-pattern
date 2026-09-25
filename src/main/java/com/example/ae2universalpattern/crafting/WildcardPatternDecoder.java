package com.example.ae2universalpattern.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.IPatternDetailsDecoder;
import appeng.api.stacks.AEItemKey;
import com.example.ae2universalpattern.item.WildcardPatternItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class WildcardPatternDecoder implements IPatternDetailsDecoder {

    public static final WildcardPatternDecoder INSTANCE = new WildcardPatternDecoder();

    private WildcardPatternDecoder() {}

    @Override
    public boolean isEncodedPattern(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof WildcardPatternItem;
    }

    @Override
    @Nullable
    public IPatternDetails decodePattern(AEItemKey what, Level level) {
        return null;
    }

    @Override
    @Nullable
    public IPatternDetails decodePattern(ItemStack what, Level level) {
        return null;
    }
}
