package com.example.ae2universalpattern.network;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.menu.me.common.MEStorageMenu;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerJEIHandler {

    private ServerJEIHandler() {}

    public static void handle(JEIRecipeClickPayload payload, IPayloadContext context) {
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
                WildcardProviderManager.handleJeiRecipeClick(targetGrid, player, payload.itemId(), payload.recipeId());
                if (targetMenu != null) {
                    targetMenu.broadcastChanges();
                }
            }
        });
    }
}
