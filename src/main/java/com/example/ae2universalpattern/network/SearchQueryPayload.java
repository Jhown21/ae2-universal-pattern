package com.example.ae2universalpattern.network;

import com.example.ae2universalpattern.AE2UniversalPatternMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SearchQueryPayload(
        String query,
        List<String> matchedItemIds,
        boolean terminalClosed
) implements CustomPacketPayload {

    public static final Type<SearchQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AE2UniversalPatternMod.MOD_ID, "search_query"));

    public static final StreamCodec<ByteBuf, SearchQueryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SearchQueryPayload::query,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(300)),
            SearchQueryPayload::matchedItemIds,
            ByteBufCodecs.BOOL,
            SearchQueryPayload::terminalClosed,
            SearchQueryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
