package com.example.ae2universalpattern.network;

import com.example.ae2universalpattern.AE2UniversalPatternMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record JEIRecipeClickPayload(String itemId, String recipeId) implements CustomPacketPayload {

    public static final Type<JEIRecipeClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2UniversalPatternMod.MOD_ID, "jei_recipe_click"));

    public static final StreamCodec<ByteBuf, JEIRecipeClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            JEIRecipeClickPayload::itemId,
            ByteBufCodecs.STRING_UTF8,
            JEIRecipeClickPayload::recipeId,
            JEIRecipeClickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
