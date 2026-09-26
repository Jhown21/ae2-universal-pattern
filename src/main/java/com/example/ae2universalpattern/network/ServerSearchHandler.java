package com.example.ae2universalpattern.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.menu.me.common.MEStorageMenu;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public final class ServerSearchHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerSearchHandler() {}

    public static void handle(SearchQueryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            IGrid targetGrid = null;
            MEStorageMenu targetMenu = null;
            if (player.containerMenu instanceof MEStorageMenu meStorageMenu) {
                targetMenu = meStorageMenu;
                IGridNode hostNode = meStorageMenu.getGridNode();
                if (hostNode == null && meStorageMenu.getHost() instanceof IActionHost actionHost) {
                    hostNode = actionHost.getActionableNode();
                }
                if (hostNode != null) {
                    targetGrid = hostNode.getGrid();
                }
            }

            if (targetGrid != null) {
                if (payload.terminalClosed() || payload.query() == null || payload.query().isBlank()) {
                    LOGGER.info("[AE2UniversalPattern] Player {} cleared search query.",
                            player.getGameProfile().getName());
                    WildcardProviderManager.updateSearch(targetGrid, player.getUUID(), "", null);
                } else {
                    LOGGER.info("[AE2UniversalPattern] Player {} search updated: '{}' (client matched {} items)",
                            player.getGameProfile().getName(), payload.query(), payload.matchedItemIds().size());
                    WildcardProviderManager.updateSearch(targetGrid, player.getUUID(), payload.query(), payload.matchedItemIds());
                }
                if (targetMenu != null) {
                    targetMenu.broadcastChanges();
                }
            } else if (payload.terminalClosed() || payload.query() == null || payload.query().isBlank()) {
                WildcardProviderManager.clearPlayerSearch(player.getUUID());
            }
        });
    }
}
