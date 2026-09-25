package com.example.ae2universalpattern.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.menu.me.common.MEStorageMenu;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerSearchHandler {

    private ServerSearchHandler() {}

    public static void handle(SearchQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            IGrid targetGrid = null;
            if (player.containerMenu instanceof MEStorageMenu meStorageMenu) {
                IGridNode hostNode = meStorageMenu.getGridNode();
                if (hostNode != null && hostNode.isActive()) {
                    targetGrid = hostNode.getGrid();
                }
            }

            if (targetGrid != null) {
                // Notifica os Pattern Providers com o Padrão Coringa na rede
                WildcardProviderManager.updateSearch(targetGrid, player.getUUID(), payload.query());
            } else if (payload.query() == null || payload.query().isBlank()) {
                WildcardProviderManager.clearPlayerSearch(player.getUUID());
            }
        });
    }
}
