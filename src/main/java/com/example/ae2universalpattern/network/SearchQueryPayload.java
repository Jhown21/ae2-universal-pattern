package com.example.ae2universalpattern.network;

import com.example.ae2universalpattern.AE2UniversalPatternMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SearchQueryPayload(String query) implements CustomPacketPayload {

    public static final Type<SearchQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2UniversalPatternMod.MOD_ID, "search_query"));

    public static final StreamCodec<ByteBuf, SearchQueryPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(SearchQueryPayload::new, SearchQueryPayload::query);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
