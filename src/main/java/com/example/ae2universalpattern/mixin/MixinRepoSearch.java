package com.example.ae2universalpattern.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.Normalizer;
import java.util.Locale;

@Mixin(value = RepoSearch.class, remap = false)
public class MixinRepoSearch {

    @Unique
    private static final Logger ae2universalpattern$LOGGER = LogUtils.getLogger();

    @Shadow
    private String searchString;

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void ae2universalpattern$onMatches(GridInventoryEntry entry, CallbackInfoReturnable<Boolean> cir) {
        if (entry == null) return;
        AEKey key = entry.getWhat();
        if (key == null) return;

        if (this.searchString == null || this.searchString.isBlank()) return;

        String cleanSearch = ae2universalpattern$clean(this.searchString);
        if (cleanSearch.isEmpty()) return;

        // Support OR queries separated by '|'
        String[] orParts = cleanSearch.split("\\|");
        for (String orPart : orParts) {
            String[] tokens = orPart.trim().split("\\s+");
            if (tokens.length == 0 || (tokens.length == 1 && tokens[0].isEmpty())) continue;

            String cleanDisplay = ae2universalpattern$clean(key.getDisplayName().getString());

            String path = "";
            String pathWithSpaces = "";
            String fullId = "";
            String modId = "";
            String cleanDesc = "";

            ResourceLocation id = key.getId();
            if (id != null) {
                path = ae2universalpattern$clean(id.getPath());
                pathWithSpaces = path.replace('_', ' ');
                fullId = id.toString().toLowerCase(Locale.ROOT);
                modId = id.getNamespace().toLowerCase(Locale.ROOT);
            }

            if (key instanceof AEItemKey itemKey) {
                try {
                    Item item = itemKey.getItem();
                    cleanDesc = ae2universalpattern$clean(item.getDescription().getString());
                } catch (Exception ignored) {}
            }

            boolean allTokensMatch = true;
            for (String token : tokens) {
                if (token.isEmpty()) continue;

                if (token.startsWith("@")) {
                    String mod = token.substring(1);
                    if (mod.isEmpty() || !modId.contains(mod)) {
                        allTokensMatch = false;
                        break;
                    }
                } else if (token.startsWith("#") || token.startsWith("$") || token.startsWith("*")) {
                    // Defer tag, tooltip, and item id special queries to vanilla logic
                    return;
                } else {
                    boolean matches = cleanDisplay.contains(token)
                            || path.contains(token)
                            || pathWithSpaces.contains(token)
                            || fullId.contains(token)
                            || (!cleanDesc.isEmpty() && cleanDesc.contains(token));
                    if (!matches) {
                        allTokensMatch = false;
                        break;
                    }
                }
            }

            if (allTokensMatch) {
                if (entry.isCraftable()) {
                    ae2universalpattern$LOGGER.info("[AE2UniversalPattern MixinRepoSearch] Craftable item MATCHED query '{}': {} (serial={})",
                            this.searchString, key, entry.getSerial());
                }
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Unique
    private static String ae2universalpattern$clean(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
