package com.example.ae2universalpattern.mixin;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.Normalizer;
import java.util.Locale;

@Mixin(targets = "appeng.client.gui.me.search.NameSearchPredicate", remap = false)
public class MixinNameSearchPredicate {

    @Shadow @Final private String term;

    @Inject(method = "test(Lappeng/menu/me/common/GridInventoryEntry;)Z", at = @At("HEAD"), cancellable = true)
    private void ae2universalpattern$onTest(GridInventoryEntry gridInventoryEntry, CallbackInfoReturnable<Boolean> cir) {
        if (gridInventoryEntry == null) return;
        AEKey entryInfo = gridInventoryEntry.getWhat();
        if (entryInfo == null) return;

        String cleanTerm = ae2universalpattern$clean(this.term);
        if (cleanTerm.isEmpty()) return;

        // 1. Normalized display name check (strips diacritics / accents, lowercase)
        String cleanDisplay = ae2universalpattern$clean(entryInfo.getDisplayName().getString());
        if (cleanDisplay.contains(cleanTerm)) {
            cir.setReturnValue(true);
            return;
        }

        // 2. Registry path & full ID check (e.g. quantum -> advanced_ae:quantum_accelerator)
        ResourceLocation id = entryInfo.getId();
        if (id != null) {
            String path = ae2universalpattern$clean(id.getPath());
            String pathWithSpaces = path.replace('_', ' ');
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            if (path.contains(cleanTerm) || pathWithSpaces.contains(cleanTerm) || fullId.contains(cleanTerm)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Unique
    private static String ae2universalpattern$clean(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
