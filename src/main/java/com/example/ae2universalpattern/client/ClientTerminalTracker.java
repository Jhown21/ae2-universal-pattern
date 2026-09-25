package com.example.ae2universalpattern.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import com.example.ae2universalpattern.AE2UniversalPatternMod;
import com.example.ae2universalpattern.network.SearchQueryPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.lang.reflect.Field;

@EventBusSubscriber(modid = AE2UniversalPatternMod.MOD_ID, value = Dist.CLIENT)
public final class ClientTerminalTracker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static String lastSentQuery = null;
    private static String pendingQuery = "";
    private static long lastChangeTime = 0;
    private static boolean wasInAETerminal = false;

    private static Field REPO_FIELD;
    private static Field SEARCH_FIELD;

    static {
        try {
            REPO_FIELD = MEStorageScreen.class.getDeclaredField("repo");
            REPO_FIELD.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("[AE2UniversalPattern] MEStorageScreen.repo field not found directly: {}", e.getMessage());
        }

        try {
            SEARCH_FIELD = MEStorageScreen.class.getDeclaredField("searchField");
            SEARCH_FIELD.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("[AE2UniversalPattern] MEStorageScreen.searchField field not found directly: {}", e.getMessage());
        }
    }

    private ClientTerminalTracker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        if (screen instanceof MEStorageScreen<?> storageScreen) {
            wasInAETerminal = true;
            String currentSearch = extractSearchText(storageScreen);

            if (!currentSearch.equals(pendingQuery)) {
                pendingQuery = currentSearch;
                lastChangeTime = System.currentTimeMillis();
            }

            // Se mudou ou acabou de abrir (lastSentQuery == null)
            if (lastSentQuery == null || !pendingQuery.equals(lastSentQuery)) {
                // Se foi limpo ou acabou de abrir, envia de imediato; se digitando, debounce de 200ms
                if (lastSentQuery == null || pendingQuery.isEmpty() || (System.currentTimeMillis() - lastChangeTime >= 200)) {
                    lastSentQuery = pendingQuery;
                    LOGGER.info("[AE2UniversalPattern] Sending search query to server: '{}'", pendingQuery);
                    PacketDistributor.sendToServer(new SearchQueryPayload(pendingQuery));
                }
            }
        }
    }

    private static String extractSearchText(MEStorageScreen<?> screen) {
        if (REPO_FIELD != null) {
            try {
                Repo repo = (Repo) REPO_FIELD.get(screen);
                if (repo != null) {
                    String s = repo.getSearchString();
                    if (s != null) return s;
                }
            } catch (Exception ignored) {}
        }

        if (SEARCH_FIELD != null) {
            try {
                EditBox editBox = (EditBox) SEARCH_FIELD.get(screen);
                if (editBox != null) {
                    return editBox.getValue();
                }
            } catch (Exception ignored) {}
        }

        try {
            for (var child : screen.children()) {
                if (child instanceof EditBox editBox) {
                    return editBox.getValue();
                }
            }
        } catch (Exception ignored) {}

        return "";
    }
}
