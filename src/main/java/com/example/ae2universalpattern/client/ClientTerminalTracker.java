package com.example.ae2universalpattern.client;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.MEStorageMenu;
import com.example.ae2universalpattern.AE2UniversalPatternMod;
import com.example.ae2universalpattern.client.jei.JEIInteractionHelper;
import com.example.ae2universalpattern.network.SearchQueryPayload;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@EventBusSubscriber(modid = AE2UniversalPatternMod.MOD_ID, value = Dist.CLIENT)
public final class ClientTerminalTracker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static String lastSentQuery = null;
    private static String pendingQuery = "";
    private static long lastChangeTime = 0;

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
    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean inTerminal = mc.player.containerMenu instanceof MEStorageMenu;
        if (!inTerminal && mc.screen instanceof MEStorageScreen) {
            inTerminal = true;
        }

        if (!inTerminal) return;

        if (ModList.get().isLoaded("jei")) {
            JEIInteractionHelper.handleScreenClick(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;

        if (screen instanceof MEStorageScreen<?> storageScreen) {
            String currentSearch = extractSearchText(storageScreen);

            if (!currentSearch.equals(pendingQuery)) {
                pendingQuery = currentSearch;
                lastChangeTime = System.currentTimeMillis();
            }

            // Se mudou ou primeira vez no terminal
            if (lastSentQuery == null || !pendingQuery.equals(lastSentQuery)) {
                // Se foi limpo, envia de imediato; se digitando, debounce de 200ms
                if (pendingQuery.isEmpty() || (System.currentTimeMillis() - lastChangeTime >= 200)) {
                    lastSentQuery = pendingQuery;
                    LOGGER.info("[AE2UniversalPattern] Sending search query to server: '{}'", pendingQuery);
                    List<String> matchedItemIds = findMatchingItemIds(pendingQuery);
                    PacketDistributor.sendToServer(new SearchQueryPayload(pendingQuery, matchedItemIds, false));
                }
            }
        }
    }

    public static List<String> findMatchingItemIds(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String cleanQuery = clean(query);
        if (cleanQuery.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> matched = new ArrayList<>();
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            Item item = entry.getValue();
            ResourceLocation id = entry.getKey().location();
            String path = clean(id.getPath());
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            String localizedName = clean(item.getDescription().getString());

            if (localizedName.contains(cleanQuery) || path.contains(cleanQuery) || fullId.contains(cleanQuery)) {
                matched.add(id.toString());
                if (matched.size() >= 250) {
                    break;
                }
            }
        }
        return matched;
    }

    private static String clean(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static String extractSearchText(MEStorageScreen<?> screen) {
        if (SEARCH_FIELD != null) {
            try {
                EditBox editBox = (EditBox) SEARCH_FIELD.get(screen);
                if (editBox != null && editBox.isVisible()) {
                    return editBox.getValue();
                }
            } catch (Exception ignored) {}
        }

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
                if (child instanceof EditBox editBox && editBox.isVisible()) {
                    return editBox.getValue();
                }
            }
        } catch (Exception ignored) {}

        return "";
    }
}
