package com.example.ae2universalpattern.mixin;

import appeng.client.gui.me.common.Repo;
import appeng.client.gui.me.search.RepoSearch;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import com.google.common.collect.BiMap;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = Repo.class, remap = false)
public class MixinRepo {

    @Unique
    private static final Logger ae2universalpattern$LOGGER = LogUtils.getLogger();

    @Shadow @Final private BiMap<Long, GridInventoryEntry> entries;
    @Shadow @Final private ArrayList<GridInventoryEntry> view;
    @Shadow @Final private RepoSearch search;
    @Shadow @Final private ISortSource sortSrc;

    @Inject(method = "handleUpdate(ZLjava/util/List;)V", at = @At("HEAD"))
    private void ae2universalpattern$onHandleUpdate(boolean fullUpdate, List<GridInventoryEntry> incomingEntries, CallbackInfo ci) {
        int craftableCount = 0;
        List<String> sampleCraftables = new ArrayList<>();
        if (incomingEntries != null) {
            for (GridInventoryEntry e : incomingEntries) {
                if (e != null && e.isCraftable()) {
                    craftableCount++;
                    if (sampleCraftables.size() < 5 && e.getWhat() != null) {
                        sampleCraftables.add(e.getWhat().toString());
                    }
                }
            }
            ae2universalpattern$LOGGER.info("[AE2UniversalPattern Client Repo] handleUpdate: fullUpdate={}, totalIncoming={}, craftableCount={}, samples={}",
                    fullUpdate, incomingEntries.size(), craftableCount, sampleCraftables);
        }
    }

    @Inject(method = "updateView", at = @At("RETURN"))
    private void ae2universalpattern$onUpdateViewReturn(CallbackInfo ci) {
        String searchStr = this.search.getSearchString();
        int totalEntries = this.entries.size();
        int viewSize = this.view.size();
        int craftablesInEntries = 0;
        for (GridInventoryEntry e : this.entries.values()) {
            if (e != null && e.isCraftable()) {
                craftablesInEntries++;
            }
        }

        ae2universalpattern$LOGGER.info("[AE2UniversalPattern Client Repo] updateView: entries={}, craftablesInEntries={}, view={}, search='{}', sortDisplay={}, sortTypes={}",
                totalEntries, craftablesInEntries, viewSize, searchStr,
                this.sortSrc != null ? this.sortSrc.getSortDisplay() : "null",
                this.sortSrc != null ? this.sortSrc.getSortKeyTypes() : "null");

        // If search string is active and view is empty (or has 0 craftables), inspect why craftables didn't make it to view
        if (searchStr != null && !searchStr.isBlank() && viewSize == 0 && craftablesInEntries > 0) {
            int logged = 0;
            for (GridInventoryEntry e : this.entries.values()) {
                if (e != null && e.isCraftable() && e.getWhat() != null) {
                    boolean matches = this.search.matches(e);
                    ae2universalpattern$LOGGER.info("[AE2UniversalPattern Client Repo] Missing craftable check: {} (craftable={}, stored={}), search.matches={}",
                            e.getWhat(), e.isCraftable(), e.getStoredAmount(), matches);
                    logged++;
                    if (logged >= 5) break;
                }
            }
        }
    }
}
