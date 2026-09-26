package com.example.ae2universalpattern.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.util.CraftingRecipeUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.text.Normalizer;
import java.util.*;

public final class RecipePatternIndexer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput = null;
    private static int lastRecipeCount = -1;

    private static final int MAX_PRIMARY_RECIPES = 250;
    private static final int MAX_TOTAL_PATTERNS = 2500;

    private RecipePatternIndexer() {}

    private record VariantCandidate(ItemStack stack, long score) {}
    private record ScoredRecipe(RecipeHolder<CraftingRecipe> holder, long relevanceScore, long availabilityScore, long totalScore) {}
    private record ScoredSubRecipe(RecipeHolder<CraftingRecipe> holder, ItemStack[] inputs, long score) {}
    private record QueuedItem(Item item, int depth) {}
    private static final int MAX_BFS_DEPTH = 32;

    public static synchronized void invalidateRecipeCache() {
        recipesByOutput = null;
        lastRecipeCount = -1;
    }

    public static synchronized Map<Item, List<RecipeHolder<CraftingRecipe>>> getRecipesByOutput(Level level) {
        List<RecipeHolder<CraftingRecipe>> all = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        if (recipesByOutput == null || lastRecipeCount != all.size()) {
            Map<Item, List<RecipeHolder<CraftingRecipe>>> map = new HashMap<>();
            for (RecipeHolder<CraftingRecipe> holder : all) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) continue;
                ItemStack out;
                try {
                    out = recipe.getResultItem(level.registryAccess());
                } catch (Exception e) {
                    continue;
                }
                if (!out.isEmpty()) {
                    map.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(holder);
                }
            }
            recipesByOutput = map;
            lastRecipeCount = all.size();
        }
        return recipesByOutput;
    }

    public static List<IPatternDetails> searchCraftingRecipes(Level level, Collection<String> queries) {
        return searchCraftingRecipes(level, queries, Collections.emptySet(), Collections.emptyMap());
    }

    public static List<IPatternDetails> searchCraftingRecipes(
            Level level,
            Collection<String> queries,
            Map<Item, Long> systemInventory) {
        return searchCraftingRecipes(level, queries, Collections.emptySet(), systemInventory);
    }

    public static List<IPatternDetails> searchCraftingRecipes(
            Level level,
            Collection<String> queries,
            Set<String> clientMatchedItemIds,
            Map<Item, Long> systemInventory) {

        if (level == null) {
            return List.of();
        }

        List<String> validQueries = new ArrayList<>();
        if (queries != null) {
            for (String q : queries) {
                if (q != null && !q.isBlank()) {
                    validQueries.add(cleanString(q));
                }
            }
        }
        boolean hasSearchQuery = !validQueries.isEmpty() || (clientMatchedItemIds != null && !clientMatchedItemIds.isEmpty());

        Map<Item, Long> inv = systemInventory != null ? systemInventory : Collections.emptyMap();
        Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOut = getRecipesByOutput(level);

        List<RecipeHolder<CraftingRecipe>> allCraftingRecipes =
                level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);

        Map<Item, Long> scoreCache = new HashMap<>();

        // 1. Encontra e pontua todas as receitas candidatas
        List<ScoredRecipe> scoredCandidates = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : allCraftingRecipes) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial()) continue;

            ItemStack previewOut;
            try {
                previewOut = recipe.getResultItem(level.registryAccess());
            } catch (Exception e) {
                continue;
            }
            if (previewOut.isEmpty()) continue;

            if (hasSearchQuery) {
                if (matchesAnyQuery(previewOut, validQueries, clientMatchedItemIds)) {
                    long relScore = computeQueryRelevanceScore(previewOut, validQueries, clientMatchedItemIds);
                    long availScore = computeRecipeAvailabilityScore(recipe, inv, recipesByOut, scoreCache);
                    scoredCandidates.add(new ScoredRecipe(holder, relScore, availScore, relScore + availScore));
                }
            } else {
                // Quando a busca está vazia, lista receitas com materiais disponíveis
                if (!inv.isEmpty()) {
                    long score = computeRecipeAvailabilityScore(recipe, inv, recipesByOut, scoreCache);
                    if (score >= 10_000L) {
                        scoredCandidates.add(new ScoredRecipe(holder, 0L, score, score));
                    }
                }
            }
        }

        // Garante receitas básicas quando a busca está vazia
        if (!hasSearchQuery) {
            for (RecipeHolder<CraftingRecipe> holder : allCraftingRecipes) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) continue;
                ItemStack out;
                try {
                    out = recipe.getResultItem(level.registryAccess());
                } catch (Exception e) {
                    continue;
                }
                if (out.is(net.minecraft.world.item.Items.CRAFTING_TABLE)
                        || out.is(net.minecraft.world.item.Items.STICK)
                        || out.is(net.minecraft.world.item.Items.CHEST)
                        || out.is(net.minecraft.world.item.Items.FURNACE)
                        || out.is(net.minecraft.world.item.Items.IRON_INGOT)
                        || out.is(net.minecraft.world.item.Items.IRON_NUGGET)) {
                    long score = computeRecipeAvailabilityScore(recipe, inv, recipesByOut, scoreCache);
                    long s = Math.max(score, 10L);
                    scoredCandidates.add(new ScoredRecipe(holder, 0L, s, s));
                }
            }
        }

        if (scoredCandidates.isEmpty()) {
            return List.of();
        }

        // 2. Ordena os resultados: relevância da busca no topo absoluto, com materiais disponíveis como desempate
        scoredCandidates.sort((a, b) -> Long.compare(b.totalScore, a.totalScore));

        // Descarta receitas inferiores concorrentes para o mesmo item de saída:
        // Se para o mesmo item existe receita realizável com materiais do ME (>= 100_000L),
        // não registra variantes impossíveis de fabricar (ex: receita com cardboard quando existe couro/cow essence).
        Map<Item, Long> bestScoreByItem = new HashMap<>();
        for (ScoredRecipe sr : scoredCandidates) {
            ItemStack out;
            try {
                out = sr.holder.value().getResultItem(level.registryAccess());
            } catch (Exception e) {
                continue;
            }
            if (!out.isEmpty()) {
                bestScoreByItem.merge(out.getItem(), sr.availabilityScore, Math::max);
            }
        }

        List<RecipeHolder<CraftingRecipe>> primaryToEncode = new ArrayList<>();
        Map<Item, Integer> primaryCountByItem = new HashMap<>();

        for (ScoredRecipe sr : scoredCandidates) {
            ItemStack out;
            try {
                out = sr.holder.value().getResultItem(level.registryAccess());
            } catch (Exception e) {
                continue;
            }
            if (out.isEmpty()) continue;

            long bestForThisItem = bestScoreByItem.getOrDefault(out.getItem(), 0L);
            if (bestForThisItem >= 100_000L && sr.availabilityScore < 100_000L) {
                continue;
            }

            int count = primaryCountByItem.getOrDefault(out.getItem(), 0);
            if (count >= 2) {
                continue;
            }
            primaryCountByItem.put(out.getItem(), count + 1);

            primaryToEncode.add(sr.holder);
            if (primaryToEncode.size() >= MAX_PRIMARY_RECIPES) {
                break;
            }
        }

        // 3. Codifica os patterns das receitas primárias e TODA a árvore de sub-receitas via BFS iterativo
        List<IPatternDetails> result = new ArrayList<>();
        Set<AEItemKey> seenPatternKeys = new HashSet<>();
        Set<RecipeHolder<CraftingRecipe>> encodedRecipes = new HashSet<>();

        for (RecipeHolder<CraftingRecipe> holder : primaryToEncode) {
            if (result.size() >= MAX_TOTAL_PATTERNS) {
                break;
            }

            processPrimaryRecipe(
                    holder,
                    level,
                    inv,
                    recipesByOut,
                    scoreCache,
                    result,
                    seenPatternKeys,
                    encodedRecipes
            );
        }

        return List.copyOf(result);
    }

    public static List<IPatternDetails> indexJeiClickedRecipe(
            Item targetItem,
            ResourceLocation recipeId,
            Level level,
            Map<Item, Long> systemInventory) {

        if (level == null) return Collections.emptyList();

        Map<Item, Long> inv = systemInventory != null ? systemInventory : Collections.emptyMap();
        Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOut = getRecipesByOutput(level);
        Map<Item, Long> scoreCache = new HashMap<>();

        RecipeHolder<CraftingRecipe> selectedHolder = null;

        if (recipeId != null) {
            var opt = level.getRecipeManager().byKey(recipeId);
            if (opt.isPresent() && opt.get().value() instanceof CraftingRecipe) {
                //noinspection unchecked
                selectedHolder = (RecipeHolder<CraftingRecipe>) (Object) opt.get();
            }
        }

        if (selectedHolder == null && targetItem != null) {
            List<RecipeHolder<CraftingRecipe>> candidates = recipesByOut.get(targetItem);
            if (candidates != null && !candidates.isEmpty()) {
                long bestScore = Long.MIN_VALUE;
                for (RecipeHolder<CraftingRecipe> holder : candidates) {
                    if (holder.value().isSpecial()) continue;
                    long score = computeRecipeAvailabilityScore(holder.value(), inv, recipesByOut, scoreCache);
                    if (score > bestScore) {
                        bestScore = score;
                        selectedHolder = holder;
                    }
                }
            }
        }

        if (selectedHolder == null) {
            return Collections.emptyList();
        }

        List<IPatternDetails> result = new ArrayList<>();
        Set<AEItemKey> seenPatternKeys = new HashSet<>();
        Set<RecipeHolder<CraftingRecipe>> encodedRecipes = new HashSet<>();

        processPrimaryRecipe(
                selectedHolder,
                level,
                inv,
                recipesByOut,
                scoreCache,
                result,
                seenPatternKeys,
                encodedRecipes
        );

        LOGGER.info("[AE2UniversalPattern] JEI recipe click indexed {} pattern(s) for item: {} (recipe: {})",
                result.size(), targetItem, recipeId);

        return List.copyOf(result);
    }

    public static List<IPatternDetails> searchCompatibleFallback(
            Item outputItem,
            Level level,
            Map<Item, Long> systemInventory,
            Set<ResourceLocation> excludeRecipeIds) {

        if (level == null || outputItem == null) return Collections.emptyList();

        Map<Item, Long> inv = systemInventory != null ? systemInventory : Collections.emptyMap();
        Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOut = getRecipesByOutput(level);
        Map<Item, Long> scoreCache = new HashMap<>();

        List<RecipeHolder<CraftingRecipe>> candidates = recipesByOut.get(outputItem);
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();

        RecipeHolder<CraftingRecipe> bestFallback = null;
        long bestScore = -1L;

        for (RecipeHolder<CraftingRecipe> holder : candidates) {
            if (holder.value().isSpecial()) continue;
            if (excludeRecipeIds != null && excludeRecipeIds.contains(holder.id())) continue;

            long score = computeRecipeAvailabilityScore(holder.value(), inv, recipesByOut, scoreCache);
            if (score >= 100_000L && score > bestScore) {
                bestScore = score;
                bestFallback = holder;
            }
        }

        if (bestFallback == null) return Collections.emptyList();

        List<IPatternDetails> result = new ArrayList<>();
        Set<AEItemKey> seenPatternKeys = new HashSet<>();
        Set<RecipeHolder<CraftingRecipe>> encodedRecipes = new HashSet<>();

        processPrimaryRecipe(
                bestFallback,
                level,
                inv,
                recipesByOut,
                scoreCache,
                result,
                seenPatternKeys,
                encodedRecipes
        );

        return List.copyOf(result);
    }

    public static boolean isPatternCraftable(
            IPatternDetails pattern,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache) {

        if (pattern == null) return false;
        for (var input : pattern.getInputs()) {
            boolean slotSatisfied = false;
            for (var candidate : input.getPossibleInputs()) {
                if (candidate.what() instanceof AEItemKey itemKey) {
                    long count = systemInventory.getOrDefault(itemKey.getItem(), 0L);
                    if (count > 0) {
                        slotSatisfied = true;
                        break;
                    }
                    long score = getItemScore(itemKey.getItem(), systemInventory, recipesByOutput, scoreCache, new HashSet<>(), 0);
                    if (score >= 100_000L) {
                        slotSatisfied = true;
                        break;
                    }
                }
            }
            if (!slotSatisfied) {
                return false;
            }
        }
        return true;
    }

    private static void processPrimaryRecipe(
            RecipeHolder<CraftingRecipe> holder,
            Level level,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache,
            List<IPatternDetails> result,
            Set<AEItemKey> seenPatternKeys,
            Set<RecipeHolder<CraftingRecipe>> encodedRecipes) {

        CraftingRecipe recipe = holder.value();
        NonNullList<Ingredient> matrix;
        try {
            matrix = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        } catch (Exception e) {
            return;
        }

        Ingredient primaryMulti = null;
        int maxOptions = 1;
        for (Ingredient ing : matrix) {
            if (!ing.isEmpty()) {
                ItemStack[] items = ing.getItems();
                if (items.length > maxOptions) {
                    maxOptions = items.length;
                    primaryMulti = ing;
                }
            }
        }

        List<ItemStack> candidateVariants = new ArrayList<>();
        if (primaryMulti != null && maxOptions > 1) {
            List<VariantCandidate> scoredVariants = new ArrayList<>();
            for (ItemStack item : primaryMulti.getItems()) {
                if (!item.isEmpty()) {
                    long score = getItemScore(item.getItem(), systemInventory, recipesByOutput, scoreCache, new HashSet<>(), 0);
                    scoredVariants.add(new VariantCandidate(item, score));
                }
            }

            scoredVariants.sort((a, b) -> Long.compare(b.score, a.score));

            long topScore = scoredVariants.isEmpty() ? 0L : scoredVariants.get(0).score;

            // Se existem variantes realizáveis pelo estoque (>= 100_000L), usa APENAS elas!
            // Evita registrar padrões que pedem materiais que o jogador não consegue fazer.
            if (topScore >= 100_000L) {
                for (VariantCandidate vc : scoredVariants) {
                    if (vc.score >= 100_000L) {
                        candidateVariants.add(vc.stack);
                        if (candidateVariants.size() >= 3) {
                            break;
                        }
                    }
                }
            } else {
                for (VariantCandidate vc : scoredVariants) {
                    candidateVariants.add(vc.stack);
                    if (candidateVariants.size() >= 2) {
                        break;
                    }
                }
            }
        } else {
            candidateVariants.add(ItemStack.EMPTY);
        }

        for (ItemStack variantItem : candidateVariants) {
            if (result.size() >= MAX_TOTAL_PATTERNS) {
                break;
            }

            ItemStack[] in = new ItemStack[9];
            Arrays.fill(in, ItemStack.EMPTY);

            for (int i = 0; i < 9; i++) {
                Ingredient ing = matrix.get(i);
                if (ing.isEmpty()) continue;

                if (!variantItem.isEmpty() && ing.test(variantItem)) {
                    in[i] = variantItem.copy();
                } else {
                    in[i] = getBestItemForIngredient(ing, systemInventory, recipesByOutput, scoreCache).copy();
                }
            }

            // Executa a resolução BFS iterativa da árvore inteira a partir desta variante
            buildCompleteTreeBfs(
                    holder,
                    in,
                    level,
                    systemInventory,
                    recipesByOutput,
                    scoreCache,
                    result,
                    seenPatternKeys,
                    encodedRecipes
            );
        }
    }

    /**
     * Resolução iterativa BFS da árvore completa de crafting.
     * Não utiliza recursão, garantindo 0 lag e execução instantânea mesmo em modpacks gigantes.
     */
    private static void buildCompleteTreeBfs(
            RecipeHolder<CraftingRecipe> primaryHolder,
            ItemStack[] primaryInputs,
            Level level,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache,
            List<IPatternDetails> result,
            Set<AEItemKey> seenPatternKeys,
            Set<RecipeHolder<CraftingRecipe>> encodedRecipes) {

        // 1. Codifica a receita principal (se for uma variante diferente, seenPatternKeys cuidará da deduplicação)
        encodedRecipes.add(primaryHolder);
        encodeSinglePattern(primaryHolder, primaryInputs, level, result, seenPatternKeys);

        // 2. Fila BFS para descer em todos os ingredientes de sub-craft de forma iterativa
        // Garante que mesmo itens que já possuem estoque parcial no ME sejam codificados,
        // permitindo que o AE2 fabrique mais unidades quando a quantidade solicitada for maior que o estoque!
        Queue<QueuedItem> queue = new ArrayDeque<>();
        Set<Item> queuedItems = new HashSet<>();

        for (ItemStack in : primaryInputs) {
            if (!in.isEmpty()) {
                if (queuedItems.add(in.getItem())) {
                    queue.add(new QueuedItem(in.getItem(), 1));
                }
            }
        }

        while (!queue.isEmpty() && result.size() < MAX_TOTAL_PATTERNS) {
            QueuedItem qi = queue.poll();
            Item currentItem = qi.item();
            int currentDepth = qi.depth();

            List<RecipeHolder<CraftingRecipe>> candidates = recipesByOutput.get(currentItem);
            if (candidates == null || candidates.isEmpty()) {
                continue;
            }

            // Encontra e pontua as melhores receitas para produzir currentItem
            List<ScoredSubRecipe> scoredList = new ArrayList<>();

            for (RecipeHolder<CraftingRecipe> holder : candidates) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) continue;

                NonNullList<Ingredient> matrix;
                try {
                    matrix = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
                } catch (Exception e) {
                    continue;
                }

                ItemStack[] currentInputs = new ItemStack[9];
                Arrays.fill(currentInputs, ItemStack.EMPTY);

                long currentRecipeScore = 0;
                long minSlotScore = Long.MAX_VALUE;
                boolean valid = true;

                for (int i = 0; i < 9; i++) {
                    Ingredient ing = matrix.get(i);
                    if (ing.isEmpty()) continue;

                    ItemStack bestForIng = getBestItemForIngredient(ing, systemInventory, recipesByOutput, scoreCache);
                    if (bestForIng.isEmpty()) {
                        valid = false;
                        break;
                    }
                    currentInputs[i] = bestForIng.copy();

                    long score = getItemScore(bestForIng.getItem(), systemInventory, recipesByOutput, scoreCache, new HashSet<>(), 0);
                    if (score < minSlotScore) {
                        minSlotScore = score;
                    }
                    currentRecipeScore += score;
                }

                if (!valid) continue;

                if (minSlotScore >= 10_000_000L) {
                    currentRecipeScore += 50_000_000L;
                } else if (minSlotScore >= 1_000_000L) {
                    currentRecipeScore += 20_000_000L;
                } else if (minSlotScore >= 100_000L) {
                    currentRecipeScore += 5_000_000L;
                }

                scoredList.add(new ScoredSubRecipe(holder, currentInputs, currentRecipeScore));
            }

            if (scoredList.isEmpty()) {
                continue;
            }

            scoredList.sort((a, b) -> Long.compare(b.score, a.score));

            // Codifica a melhor receita encontrada e até uma secundária viável (ex: conversões bloco <-> lingote <-> pepita)
            long bestSubScore = scoredList.get(0).score;
            int countEncoded = 0;
            for (ScoredSubRecipe ssr : scoredList) {
                if (countEncoded >= 1) {
                    if (bestSubScore >= 100_000L && ssr.score < 100_000L) {
                        break;
                    }
                    if (countEncoded >= 2) {
                        break;
                    }
                }

                if (encodedRecipes.contains(ssr.holder)) {
                    countEncoded++;
                    // Propaga sub-ingredientes pela fila BFS para garantir árvore completa
                    if (currentDepth < MAX_BFS_DEPTH) {
                        for (ItemStack subIn : ssr.inputs) {
                            if (!subIn.isEmpty() && queuedItems.add(subIn.getItem())) {
                                queue.add(new QueuedItem(subIn.getItem(), currentDepth + 1));
                            }
                        }
                    }
                    continue;
                }

                if (encodedRecipes.add(ssr.holder)) {
                    boolean success = encodeSinglePattern(ssr.holder, ssr.inputs, level, result, seenPatternKeys);
                    if (success) {
                        countEncoded++;

                        // Adiciona os ingredientes da sub-receita na fila BFS para expandir a árvore
                        if (currentDepth < MAX_BFS_DEPTH) {
                            for (ItemStack subIn : ssr.inputs) {
                                if (!subIn.isEmpty() && queuedItems.add(subIn.getItem())) {
                                    queue.add(new QueuedItem(subIn.getItem(), currentDepth + 1));
                                }
                            }
                        }
                    } else {
                        encodedRecipes.remove(ssr.holder);
                    }
                }
            }
        }
    }

    private static boolean encodeSinglePattern(
            RecipeHolder<CraftingRecipe> holder,
            ItemStack[] in,
            Level level,
            List<IPatternDetails> result,
            Set<AEItemKey> seenPatternKeys) {

        CraftingRecipe recipe = holder.value();
        ItemStack[] normalizedInputs = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            normalizedInputs[i] = (in[i] == null || in[i].isEmpty()) ? ItemStack.EMPTY : in[i].copyWithCount(1);
        }

        CraftingInput fullInput = CraftingInput.of(3, 3, Arrays.asList(normalizedInputs));
        CraftingInput positionedInput = null;
        try {
            positionedInput = CraftingInput.ofPositioned(3, 3, Arrays.asList(normalizedInputs)).input();
        } catch (Exception ignored) {}

        boolean matches = (!fullInput.isEmpty() && recipe.matches(fullInput, level))
                || (positionedInput != null && !positionedInput.isEmpty() && recipe.matches(positionedInput, level));
        if (!matches) {
            return false;
        }

        ItemStack out = ItemStack.EMPTY;
        try {
            out = recipe.assemble(fullInput, level.registryAccess());
        } catch (Exception ignored) {}
        if (out.isEmpty() && positionedInput != null) {
            try {
                out = recipe.assemble(positionedInput, level.registryAccess());
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            try {
                out = recipe.getResultItem(level.registryAccess());
            } catch (Exception ignored) {}
        }
        if (out.isEmpty()) {
            return false;
        }

        try {
            ItemStack patternStack = PatternDetailsHelper.encodeCraftingPattern(holder, normalizedInputs, out, true, true);
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(patternStack, level);
            if (pattern != null) {
                if (seenPatternKeys.add(pattern.getDefinition())) {
                    result.add(pattern);
                }
                return true;
            } else {
                LOGGER.warn("[AE2UniversalPattern] decodePattern returned null for recipe {}", holder.id());
            }
        } catch (Exception e) {
            LOGGER.warn("[AE2UniversalPattern] Failed to encode pattern for recipe {}: {}", holder.id(), e.getMessage());
        }
        return false;
    }

    /**
     * Retorna a melhor opção de item para um determinado ingrediente com base no que está disponível
     * ou é craftável através do inventário do sistema ME.
     */
    private static ItemStack getBestItemForIngredient(
            Ingredient ing,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache) {

        ItemStack[] items = ing.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        if (items.length == 1) {
            return items[0];
        }

        ItemStack bestStack = ItemStack.EMPTY;
        long bestScore = -1;

        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            long score = getItemScore(item.getItem(), systemInventory, recipesByOutput, scoreCache, new HashSet<>(), 0);

            if (bestStack.isEmpty() || score > bestScore) {
                bestScore = score;
                bestStack = item;
            }
        }

        return bestStack.isEmpty() && items.length > 0 ? items[0] : bestStack;
    }

    /**
     * Calcula o score de disponibilidade de um Item em relação aos recursos do ME.
     * Tier 0: Presente diretamente no inventário do ME (score >= 10_000_000L).
     * Tier 1+: Craftável a partir de recursos do ME, com penalidade suave por passo (-50_000L) (score >= 1_000_000L).
     * Tier Parcial: Tem alguns ingredientes no ME (score 200L a 10_000L).
     * Tier Sem Recursos: Tem receita de Crafting Table, mas faltam materiais no ME (score = 100L).
     * Tier Sem Receita: Não possui receita de Crafting Table no jogo (score = 1L).
     * Bônus de desempate: +10L para itens com namespace 'minecraft' (prioriza itens vanilla).
     */
    private static long getItemScore(
            Item item,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache,
            Set<Item> visiting,
            int depth) {

        if (item == null) return 0L;

        Long cached = scoreCache.get(item);
        if (cached != null) {
            return cached;
        }

        // 1. Diretamente no inventário do ME (Tier 0)
        long directStock = systemInventory.getOrDefault(item, 0L);
        if (directStock > 0) {
            long score = 10_000_000L + Math.min(directStock, 1_000_000L) * 10L;
            scoreCache.put(item, score);
            return score;
        }

        boolean isVanilla = BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft");
        long vanillaBonus = isVanilla ? 10L : 0L;

        // Limite de profundidade para garantir performance instantânea ou evitar ciclos
        if (depth >= 16 || !visiting.add(item)) {
            long fallback = (recipesByOutput.containsKey(item) ? 100L : 1L) + vanillaBonus;
            return fallback;
        }

        try {
            List<RecipeHolder<CraftingRecipe>> recipes = recipesByOutput.get(item);
            if (recipes == null || recipes.isEmpty()) {
                long s = 1L + vanillaBonus;
                scoreCache.put(item, s);
                return s;
            }

            long bestRecipeScore = 100L + vanillaBonus;

            for (RecipeHolder<CraftingRecipe> holder : recipes) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) continue;

                NonNullList<Ingredient> matrix;
                try {
                    matrix = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
                } catch (Exception e) {
                    continue;
                }

                long minIngredientScore = Long.MAX_VALUE;
                long totalIngredientScore = 0;
                int ingredientCount = 0;

                for (Ingredient ing : matrix) {
                    if (ing.isEmpty()) continue;
                    ingredientCount++;

                    long bestForSlot = 0;
                    for (ItemStack option : ing.getItems()) {
                        if (option.isEmpty()) continue;
                        long s = getItemScore(option.getItem(), systemInventory, recipesByOutput, scoreCache, visiting, depth + 1);
                        if (s > bestForSlot) {
                            bestForSlot = s;
                        }
                    }

                    if (bestForSlot < minIngredientScore) {
                        minIngredientScore = bestForSlot;
                    }
                    totalIngredientScore += bestForSlot;
                }

                if (ingredientCount == 0) continue;

                if (minIngredientScore >= 1_000_000L) {
                    // Todos os ingredientes estão diretamente no ME ou são craftáveis a partir do ME!
                    // Cada nível de profundidade reduz 50_000L para priorizar caminhos mais curtos,
                    // mas mantendo o score altíssimo (> 1_000_000L) mesmo em cadeias profundas de até 16 passos.
                    long baseCraftScore = Math.min(minIngredientScore - 50_000L, 9_900_000L);
                    long rScore = Math.max(1_000_000L, baseCraftScore)
                            + Math.min(totalIngredientScore / (ingredientCount * 1000L), 5_000L)
                            + vanillaBonus;
                    if (rScore > bestRecipeScore) {
                        bestRecipeScore = rScore;
                    }
                } else if (totalIngredientScore > (ingredientCount * 100L)) {
                    // Tem pelo menos algum ingrediente no ME
                    long partialScore = 200L + (totalIngredientScore / ingredientCount / 1000) + vanillaBonus;
                    if (partialScore > bestRecipeScore) {
                        bestRecipeScore = partialScore;
                    }
                }
            }

            scoreCache.put(item, bestRecipeScore);
            return bestRecipeScore;
        } finally {
            visiting.remove(item);
        }
    }

    private static long computeRecipeAvailabilityScore(
            CraftingRecipe recipe,
            Map<Item, Long> systemInventory,
            Map<Item, List<RecipeHolder<CraftingRecipe>>> recipesByOutput,
            Map<Item, Long> scoreCache) {

        NonNullList<Ingredient> matrix;
        try {
            matrix = CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        } catch (Exception e) {
            return -1;
        }

        long totalScore = 0;
        int ingredientSlots = 0;
        long minSlotScore = Long.MAX_VALUE;

        for (Ingredient ing : matrix) {
            if (ing.isEmpty()) continue;
            ingredientSlots++;

            long bestScoreForSlot = 0;
            ItemStack[] items = ing.getItems();
            for (ItemStack item : items) {
                if (item.isEmpty()) continue;
                long s = getItemScore(item.getItem(), systemInventory, recipesByOutput, scoreCache, new HashSet<>(), 0);
                if (s > bestScoreForSlot) {
                    bestScoreForSlot = s;
                }
            }
            if (bestScoreForSlot < minSlotScore) {
                minSlotScore = bestScoreForSlot;
            }
            totalScore += bestScoreForSlot;
        }

        if (ingredientSlots == 0) return 0;

        // Bônus prioritário maciço se todos os ingredientes forem realizáveis com o estoque atual
        if (minSlotScore >= 10_000_000L) {
            totalScore += 50_000_000L;
        } else if (minSlotScore >= 1_000_000L) {
            totalScore += 20_000_000L;
        } else if (minSlotScore >= 100_000L) {
            totalScore += 5_000_000L;
        }

        return totalScore - ingredientSlots;
    }

    public static List<IPatternDetails> scanCraftingTableRecipes(Level level) {
        return searchCraftingRecipes(level, List.of(""), Collections.emptyMap());
    }

    private static String cleanString(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private static long computeQueryRelevanceScore(ItemStack stack, List<String> queries, Set<String> clientMatchedItemIds) {
        if (stack.isEmpty()) {
            return 0L;
        }

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String cleanDisplayName = cleanString(stack.getHoverName().getString());

        long maxScore = 0L;

        if (clientMatchedItemIds != null && clientMatchedItemIds.contains(id.toString())) {
            maxScore = 1_500_000_000L;
        }

        if (queries != null) {
            for (String rawQuery : queries) {
                if (rawQuery == null || rawQuery.isBlank()) continue;
                String q = cleanString(rawQuery);

                long score;
                if (cleanDisplayName.equals(q) || path.equals(q) || fullId.equals(q)) {
                    score = 2_000_000_000L;
                } else if (cleanDisplayName.startsWith(q) || path.startsWith(q)) {
                    score = 1_000_000_000L;
                } else if (cleanDisplayName.contains(" " + q) || path.contains("_" + q)) {
                    score = 500_000_000L;
                } else if (cleanDisplayName.contains(q) || path.contains(q)) {
                    score = 200_000_000L;
                } else {
                    score = 50_000_000L;
                }

                if (score > maxScore) {
                    maxScore = score;
                }
            }
        }

        return maxScore;
    }

    private static boolean matchesAnyQuery(ItemStack stack, List<String> queries, Set<String> clientMatchedItemIds) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (clientMatchedItemIds != null && clientMatchedItemIds.contains(id.toString())) {
            return true;
        }

        String modId = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String fullId = id.toString().toLowerCase(Locale.ROOT);
        String cleanDisplayName = cleanString(stack.getHoverName().getString());

        if (queries != null) {
            for (String q : queries) {
                if (q == null || q.isBlank()) continue;
                String[] tokens = q.trim().split("\\s+");
                boolean allTokensMatch = true;

                for (String rawToken : tokens) {
                    if (rawToken.isEmpty()) continue;
                    String token = cleanString(rawToken);

                    if (token.startsWith("@")) {
                        String mod = token.substring(1);
                        if (mod.isEmpty() || !modId.contains(mod)) {
                            allTokensMatch = false;
                            break;
                        }
                    } else if (token.startsWith("#")) {
                        String tagQuery = token.substring(1);
                        if (!tagQuery.isEmpty()) {
                            boolean tagMatches = stack.getTags().anyMatch(tagKey -> {
                                String tagPath = tagKey.location().getPath().toLowerCase(Locale.ROOT);
                                String tagFull = tagKey.location().toString().toLowerCase(Locale.ROOT);
                                return tagPath.contains(tagQuery) || tagFull.contains(tagQuery);
                            });
                            if (!tagMatches) {
                                allTokensMatch = false;
                                break;
                            }
                        }
                    } else {
                        boolean tokenMatches = cleanDisplayName.contains(token) || path.contains(token) || fullId.contains(token);
                        if (!tokenMatches) {
                            allTokensMatch = false;
                            break;
                        }
                    }
                }

                if (allTokensMatch) {
                    return true;
                }
            }
        }
        return false;
    }
}
