package com.example.ae2universalpattern.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.example.ae2universalpattern.item.WildcardPatternItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

import java.util.*;

public final class WildcardProviderManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<IWildcardPatternHolder> ACTIVE_HOLDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<IGrid, Map<UUID, String>> GRID_PLAYER_QUERIES = new WeakHashMap<>();
    private static final Map<IGrid, Set<IPatternDetails>> GRID_PERMANENT_PATTERNS = new WeakHashMap<>();
    private static final Map<IGrid, List<IPatternDetails>> GRID_JEI_PATTERNS = new WeakHashMap<>();
    private static final Map<IGrid, List<IPatternDetails>> GRID_PATTERNS = new WeakHashMap<>();

    private WildcardProviderManager() {}

    public static synchronized void registerHolder(IWildcardPatternHolder holder) {
        if (holder != null) {
            ACTIVE_HOLDERS.add(holder);
            LOGGER.info("[AE2UniversalPattern] Registered Wildcard Pattern Holder: {}", holder.getClass().getSimpleName());
            IGrid grid = holder.ae2universalpattern$getGrid();
            if (grid != null) {
                loadPermanentPatternsFromHolder(holder, grid);
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

    public static synchronized void handleJeiRecipeClick(IGrid targetGrid, ServerPlayer player, String itemIdStr, String recipeIdStr) {
        if (targetGrid == null) return;

        List<IWildcardPatternHolder> holders = getHoldersForGrid(targetGrid);
        if (holders.isEmpty()) return;

        Level level = null;
        for (IWildcardPatternHolder holder : holders) {
            BlockEntity be = holder.ae2universalpattern$getBlockEntity();
            if (be != null && be.getLevel() != null) {
                level = be.getLevel();
                break;
            }
        }
        if (level == null) return;

        Item targetItem = null;
        if (itemIdStr != null && !itemIdStr.isBlank()) {
            ResourceLocation rl = ResourceLocation.tryParse(itemIdStr);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                targetItem = BuiltInRegistries.ITEM.get(rl);
            }
        }

        ResourceLocation recipeId = null;
        if (recipeIdStr != null && !recipeIdStr.isBlank()) {
            recipeId = ResourceLocation.tryParse(recipeIdStr);
        }

        Map<Item, Long> systemInventory = getGridItemCounts(targetGrid);
        List<IPatternDetails> jeiPatterns = RecipePatternIndexer.indexJeiClickedRecipe(targetItem, recipeId, level, systemInventory);

        if (!jeiPatterns.isEmpty()) {
            GRID_JEI_PATTERNS.put(targetGrid, jeiPatterns);
            LOGGER.info("[AE2UniversalPattern] JEI recipe click registered {} pattern(s) for target item {} on grid {}",
                    jeiPatterns.size(), targetItem, targetGrid);
            refreshGridPatterns(targetGrid);
        }
    }

    public static synchronized void recordCraftedPatterns(IGrid grid, Collection<IPatternDetails> patterns) {
        if (grid == null || patterns == null || patterns.isEmpty()) return;

        Set<IPatternDetails> permanentSet = GRID_PERMANENT_PATTERNS.computeIfAbsent(grid, g -> new LinkedHashSet<>());
        List<IWildcardPatternHolder> holders = getHoldersForGrid(grid);
        Level level = null;
        for (IWildcardPatternHolder h : holders) {
            BlockEntity be = h.ae2universalpattern$getBlockEntity();
            if (be != null && be.getLevel() != null) {
                level = be.getLevel();
                break;
            }
        }

        boolean anyAdded = false;
        for (IPatternDetails pattern : patterns) {
            if (pattern != null) {
                boolean added = permanentSet.add(pattern);
                if (added) {
                    anyAdded = true;
                    if (level != null) {
                        for (IWildcardPatternHolder holder : holders) {
                            savePatternToStack(holder.ae2universalpattern$getWildcardStack(), pattern, level);
                        }
                    }
                }
            }
        }

        if (anyAdded) {
            LOGGER.info("[AE2UniversalPattern] Grid now has {} permanent crafting pattern(s).", permanentSet.size());
            refreshGridPatterns(grid);
        }
    }

    public static synchronized void recordCraftedPattern(IGrid grid, IPatternDetails pattern) {
        if (grid != null && pattern != null) {
            recordCraftedPatterns(grid, List.of(pattern));
        }
    }

    private static void savePatternToStack(ItemStack stack, IPatternDetails pattern, Level level) {
        if (stack.isEmpty() || !(stack.getItem() instanceof WildcardPatternItem) || pattern == null || level == null) {
            return;
        }

        try {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag root = customData.copyTag();
            ListTag list = root.getList("SavedPatterns", Tag.TAG_COMPOUND);

            AEItemKey defKey = pattern.getDefinition();
            ItemStack patternStack = defKey != null ? defKey.toStack() : ItemStack.EMPTY;
            if (patternStack.isEmpty()) return;

            Tag patternTag = patternStack.save(level.registryAccess());
            if (patternTag instanceof CompoundTag compound) {
                String defString = defKey.toString();
                boolean exists = false;
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag existing = list.getCompound(i);
                    if (defString.equals(existing.getString("_defKey"))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    compound.putString("_defKey", defString);
                    list.add(compound);
                    root.put("SavedPatterns", list);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[AE2UniversalPattern] Failed to save permanent pattern to stack: {}", e.getMessage());
        }
    }

    private static void loadPermanentPatternsFromHolder(IWildcardPatternHolder holder, IGrid grid) {
        if (holder == null || grid == null) return;
        ItemStack stack = holder.ae2universalpattern$getWildcardStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof WildcardPatternItem)) return;

        BlockEntity be = holder.ae2universalpattern$getBlockEntity();
        if (be == null || be.getLevel() == null) return;
        Level level = be.getLevel();

        try {
            CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            if (customData.isEmpty()) return;

            CompoundTag root = customData.copyTag();
            ListTag list = root.getList("SavedPatterns", Tag.TAG_COMPOUND);
            if (list.isEmpty()) return;

            Set<IPatternDetails> permanentSet = GRID_PERMANENT_PATTERNS.computeIfAbsent(grid, g -> new LinkedHashSet<>());
            int loaded = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag patternTag = list.getCompound(i);
                ItemStack patternStack = ItemStack.parse(level.registryAccess(), patternTag).orElse(ItemStack.EMPTY);
                if (!patternStack.isEmpty()) {
                    IPatternDetails decoded = PatternDetailsHelper.decodePattern(patternStack, level);
                    if (decoded != null) {
                        permanentSet.add(decoded);
                        loaded++;
                    }
                }
            }
            LOGGER.info("[AE2UniversalPattern] Loaded {} permanent pattern(s) from Wildcard Pattern item for grid {}",
                    loaded, grid);
        } catch (Exception e) {
            LOGGER.warn("[AE2UniversalPattern] Failed to load permanent patterns from holder stack: {}", e.getMessage());
        }
    }

    public static synchronized void invalidateCache() {
        GRID_PATTERNS.clear();
        GRID_JEI_PATTERNS.clear();
        for (IWildcardPatternHolder holder : ACTIVE_HOLDERS) {
            if (holder != null) {
                holder.ae2universalpattern$setDynamicPatterns(Collections.emptyList());
            }
        }
    }

    public static synchronized void refreshGridPatterns(IGrid grid) {
        if (grid == null) return;

        List<IWildcardPatternHolder> holders = getHoldersForGrid(grid);
        if (holders.isEmpty()) return;

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
        if (level == null) return;

        Map<Item, Long> systemInventory = getGridItemCounts(grid);
        Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOut = RecipePatternIndexer.getRecipesByOutput(level);
        Map<Item, Long> scoreCache = new HashMap<>();

        Set<AEItemKey> seenKeys = new HashSet<>();
        List<IPatternDetails> combinedPatterns = new ArrayList<>();

        if (!activeQueries.isEmpty()) {
            // 1. Receitas da busca ativa no terminal ME vêm PRIMEIRO quando há termo digitado
            List<IPatternDetails> searchPatterns = RecipePatternIndexer.searchCraftingRecipes(
                    level,
                    activeQueries,
                    systemInventory
            );
            for (IPatternDetails sp : searchPatterns) {
                if (seenKeys.add(sp.getDefinition())) {
                    combinedPatterns.add(sp);
                }
            }

            // 2. Receitas Permanentes (Craftadas anteriormente)
            Set<IPatternDetails> permanentPatterns = GRID_PERMANENT_PATTERNS.get(grid);
            if (permanentPatterns != null) {
                for (IPatternDetails perm : permanentPatterns) {
                    if (perm == null) continue;
                    if (seenKeys.add(perm.getDefinition())) {
                        combinedPatterns.add(perm);
                    }
                }
            }

            // 3. Receitas clicadas no JEI
            List<IPatternDetails> jeiPatterns = GRID_JEI_PATTERNS.get(grid);
            if (jeiPatterns != null) {
                for (IPatternDetails jeiP : jeiPatterns) {
                    if (seenKeys.add(jeiP.getDefinition())) {
                        combinedPatterns.add(jeiP);
                    }
                }
            }
        } else {
            // Busca vazia:
            // 1. Receitas Permanentes (Craftadas anteriormente) com fallback dinâmico
            Set<IPatternDetails> permanentPatterns = GRID_PERMANENT_PATTERNS.get(grid);
            if (permanentPatterns != null) {
                for (IPatternDetails perm : permanentPatterns) {
                    if (perm == null) continue;
                    if (seenKeys.add(perm.getDefinition())) {
                        combinedPatterns.add(perm);
                    }

                    // Verifica se os ingredientes da receita gravada permanente estão disponíveis no sistema.
                    // Caso NÃO estejam disponíveis, faz uma busca rápida achando alguma receita compatível!
                    boolean craftable = RecipePatternIndexer.isPatternCraftable(perm, systemInventory, recipesByOut, scoreCache);
                    if (!craftable) {
                        var outputs = perm.getOutputs();
                        if (!outputs.isEmpty() && outputs.get(0).what() instanceof AEItemKey outKey) {
                            Item outItem = outKey.getItem();
                            List<IPatternDetails> fallbackPatterns = RecipePatternIndexer.searchCompatibleFallback(
                                    outItem,
                                    level,
                                    systemInventory,
                                    Collections.emptySet()
                            );
                            for (IPatternDetails fb : fallbackPatterns) {
                                if (seenKeys.add(fb.getDefinition())) {
                                    combinedPatterns.add(fb);
                                }
                            }
                        }
                    }
                }
            }

            // 2. Receitas clicadas no JEI com terminal aberto
            List<IPatternDetails> jeiPatterns = GRID_JEI_PATTERNS.get(grid);
            if (jeiPatterns != null) {
                for (IPatternDetails jeiP : jeiPatterns) {
                    if (seenKeys.add(jeiP.getDefinition())) {
                        combinedPatterns.add(jeiP);
                    }
                }
            }

            // 3. Receitas disponíveis com estoque no ME (básicas)
            List<IPatternDetails> searchPatterns = RecipePatternIndexer.searchCraftingRecipes(
                    level,
                    activeQueries,
                    systemInventory
            );
            for (IPatternDetails sp : searchPatterns) {
                if (seenKeys.add(sp.getDefinition())) {
                    combinedPatterns.add(sp);
                }
            }
        }

        List<IPatternDetails> finalPatterns;
        if (isGridBusy) {
            List<IPatternDetails> current = GRID_PATTERNS.getOrDefault(grid, Collections.emptyList());
            Set<AEItemKey> busySeen = new HashSet<>(seenKeys);
            finalPatterns = new ArrayList<>(combinedPatterns);
            for (IPatternDetails p : current) {
                if (busySeen.add(p.getDefinition())) {
                    finalPatterns.add(p);
                }
            }
        } else {
            finalPatterns = combinedPatterns;
        }

        GRID_PATTERNS.put(grid, finalPatterns);
        LOGGER.info("[AE2UniversalPattern] Loaded {} dynamic crafting patterns to {} holders (queries: {}, permanent: {}, jei: {})",
                finalPatterns.size(), holders.size(), activeQueries,
                GRID_PERMANENT_PATTERNS.get(grid) != null ? GRID_PERMANENT_PATTERNS.get(grid).size() : 0,
                GRID_JEI_PATTERNS.get(grid) != null ? GRID_JEI_PATTERNS.get(grid).size() : 0);

        if (LOGGER.isInfoEnabled() && !finalPatterns.isEmpty()) {
            List<String> sampleNames = new ArrayList<>();
            for (int i = 0; i < Math.min(10, finalPatterns.size()); i++) {
                var out = finalPatterns.get(i).getOutputs();
                if (!out.isEmpty()) {
                    sampleNames.add(out.get(0).what().toString());
                }
            }
            LOGGER.info("[AE2UniversalPattern] Sample outputs for {}: [{}]", grid, String.join(", ", sampleNames));
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
