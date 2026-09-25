package com.example.ae2universalpattern.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.*;

public final class WildcardProviderManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<IWildcardPatternHolder> ACTIVE_HOLDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<IGrid, Map<UUID, String>> GRID_PLAYER_QUERIES = new WeakHashMap<>();
    private static final Map<IGrid, List<IPatternDetails>> GRID_PATTERNS = new WeakHashMap<>();

    private WildcardProviderManager() {}

    public static synchronized void registerHolder(IWildcardPatternHolder holder) {
        if (holder != null) {
            ACTIVE_HOLDERS.add(holder);
            LOGGER.info("[AE2UniversalPattern] Registered Wildcard Pattern Holder: {}", holder.getClass().getSimpleName());
            IGrid grid = holder.ae2universalpattern$getGrid();
            if (grid != null) {
                refreshGridPatterns(grid);
            }
        }
    }

    public static synchronized void unregisterHolder(IWildcardPatternHolder holder) {
        if (holder != null) {
            ACTIVE_HOLDERS.remove(holder);
            LOGGER.info("[AE2UniversalPattern] Unregistered Wildcard Pattern Holder: {}", holder.getClass().getSimpleName());
        }
    }

    public static synchronized void registerProvider(PatternProviderLogic logic) {
        if (logic instanceof IWildcardPatternHolder holder) {
            registerHolder(holder);
        }
    }

    public static synchronized void unregisterProvider(PatternProviderLogic logic) {
        if (logic instanceof IWildcardPatternHolder holder) {
            unregisterHolder(holder);
        }
    }

    public static synchronized List<IWildcardPatternHolder> getHoldersForGrid(IGrid grid) {
        if (grid == null) return Collections.emptyList();
        List<IWildcardPatternHolder> list = new ArrayList<>();
        Iterator<IWildcardPatternHolder> it = ACTIVE_HOLDERS.iterator();
        while (it.hasNext()) {
            IWildcardPatternHolder holder = it.next();
            if (holder == null || !holder.ae2universalpattern$isValid()) {
                it.remove();
                continue;
            }
            if (holder.ae2universalpattern$getGrid() == grid) {
                list.add(holder);
            }
        }
        return list;
    }

    public static synchronized List<IPatternDetails> getPatternsForGrid(IGrid grid) {
        if (grid == null) return Collections.emptyList();
        return GRID_PATTERNS.getOrDefault(grid, Collections.emptyList());
    }

    public static synchronized void updateSearch(IGrid targetGrid, UUID playerId, String query) {
        if (targetGrid == null || playerId == null) return;

        Map<UUID, String> playerQueries = GRID_PLAYER_QUERIES.computeIfAbsent(targetGrid, g -> new HashMap<>());
        if (query == null || query.isBlank()) {
            playerQueries.remove(playerId);
        } else {
            playerQueries.put(playerId, query.trim());
        }

        refreshGridPatterns(targetGrid);
    }

    public static synchronized void clearPlayerSearch(UUID playerId) {
        if (playerId == null) return;
        List<IGrid> gridsToRefresh = new ArrayList<>();
        for (Map.Entry<IGrid, Map<UUID, String>> entry : GRID_PLAYER_QUERIES.entrySet()) {
            if (entry.getValue().remove(playerId) != null) {
                gridsToRefresh.add(entry.getKey());
            }
        }
        for (IGrid grid : gridsToRefresh) {
            refreshGridPatterns(grid);
        }
    }

    public static synchronized void invalidateCache() {
        GRID_PATTERNS.clear();
        for (IWildcardPatternHolder holder : ACTIVE_HOLDERS) {
            if (holder != null) {
                holder.ae2universalpattern$setDynamicPatterns(Collections.emptyList());
            }
        }
    }

    public static synchronized void refreshGridPatterns(IGrid grid) {
        if (grid == null) return;

        List<IWildcardPatternHolder> holders = getHoldersForGrid(grid);
        if (holders.isEmpty()) {
            return;
        }

        Map<UUID, String> playerQueries = GRID_PLAYER_QUERIES.get(grid);
        Set<String> activeQueries = new HashSet<>();
        if (playerQueries != null) {
            for (String q : playerQueries.values()) {
                if (q != null && !q.isBlank()) {
                    activeQueries.add(q);
                }
            }
        }

        boolean isGridBusy = false;
        ICraftingService craftingService = grid.getCraftingService();
        if (craftingService != null) {
            for (ICraftingCPU cpu : craftingService.getCpus()) {
                if (cpu.isBusy()) {
                    isGridBusy = true;
                    break;
                }
            }
        }
        if (!isGridBusy) {
            for (IWildcardPatternHolder holder : holders) {
                if (holder instanceof ICraftingProvider cp && cp.isBusy()) {
                    isGridBusy = true;
                    break;
                }
            }
        }

        Level level = null;
        for (IWildcardPatternHolder holder : holders) {
            BlockEntity be = holder.ae2universalpattern$getBlockEntity();
            if (be != null && be.getLevel() != null) {
                level = be.getLevel();
                break;
            }
        }
        if (level == null) {
            return;
        }

        Map<Item, Long> systemInventory = getGridItemCounts(grid);

        List<IPatternDetails> newPatterns = RecipePatternIndexer.searchCraftingRecipes(
                level,
                activeQueries,
                systemInventory
        );

        List<IPatternDetails> finalPatterns;
        if (isGridBusy) {
            List<IPatternDetails> current = GRID_PATTERNS.getOrDefault(grid, Collections.emptyList());
            Set<AEItemKey> seen = new HashSet<>();
            finalPatterns = new ArrayList<>();
            for (IPatternDetails p : current) {
                if (seen.add(p.getDefinition())) {
                    finalPatterns.add(p);
                }
            }
            for (IPatternDetails p : newPatterns) {
                if (seen.add(p.getDefinition())) {
                    finalPatterns.add(p);
                }
            }
        } else {
            finalPatterns = newPatterns;
        }

        GRID_PATTERNS.put(grid, finalPatterns);
        LOGGER.info("[AE2UniversalPattern] Loaded {} dynamic crafting patterns to {} holders (queries: {})",
                finalPatterns.size(), holders.size(), activeQueries);

        if (!activeQueries.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (IPatternDetails p : finalPatterns) {
                var outputs = p.getOutputs();
                if (!outputs.isEmpty() && outputs.get(0).what() != null) {
                    if (count > 0) sb.append(", ");
                    sb.append(outputs.get(0).what().getDisplayName().getString());
                    count++;
                    if (count >= 15) {
                        sb.append("...");
                        break;
                    }
                }
            }
            LOGGER.info("[AE2UniversalPattern] Sample outputs for {}: [{}]", activeQueries, sb);
        }

        for (IWildcardPatternHolder holder : holders) {
            holder.ae2universalpattern$setDynamicPatterns(finalPatterns);
        }
    }

    private static Map<Item, Long> getGridItemCounts(IGrid grid) {
        Map<Item, Long> counts = new HashMap<>();
        var storage = grid.getStorageService();
        if (storage != null) {
            for (var entry : storage.getCachedInventory()) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                    counts.merge(itemKey.getItem(), entry.getLongValue(), Long::sum);
                }
            }
        }
        return counts;
    }
}
