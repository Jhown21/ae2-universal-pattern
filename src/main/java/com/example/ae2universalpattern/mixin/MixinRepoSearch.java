package com.example.ae2universalpattern.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.search.RepoSearch;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
            String fullId = "";
            String modId = "";
            String cleanDesc = "";

            if (key instanceof AEItemKey itemKey) {
                Item item = itemKey.getItem();
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                if (id != null) {
                    path = ae2universalpattern$clean(id.getPath());
                    fullId = id.toString().toLowerCase(Locale.ROOT);
                    modId = id.getNamespace().toLowerCase(Locale.ROOT);
                }
                try {
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
                            || fullId.contains(token)
                            || (!cleanDesc.isEmpty() && cleanDesc.contains(token));
                    if (!matches) {
                        allTokensMatch = false;
                        break;
                    }
                }
            }

            if (allTokensMatch) {
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
