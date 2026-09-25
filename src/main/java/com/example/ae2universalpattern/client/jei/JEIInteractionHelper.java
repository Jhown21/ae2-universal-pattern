package com.example.ae2universalpattern.client.jei;

import com.example.ae2universalpattern.network.JEIRecipeClickPayload;
import com.mojang.logging.LogUtils;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Optional;

public final class JEIInteractionHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static IJeiRuntime jeiRuntime = null;

    private static String lastTargetKey = null;
    private static long lastClickTime = 0;

    private JEIInteractionHelper() {}

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static void handleScreenClick(Screen screen, double mouseX, double mouseY, int button) {
        if (screen == null) return;

        long now = System.currentTimeMillis();

        // 1. Clicou dentro do menu de visualização de receitas do JEI (RecipesGui)
        if (screen instanceof RecipesGui recipesGui) {
            var layoutOpt = recipesGui.getRecipeLayoutUnderMouse(mouseX, mouseY);
            if (layoutOpt.isPresent()) {
                IRecipeLayoutDrawable<?> drawable = layoutOpt.get().getRecipeLayout();
                if (drawable != null) {
                    ItemStack targetStack = ItemStack.EMPTY;

                    // Se clicou sobre um slot específico (ex: um componente específico da receita)
                    Optional<IRecipeSlotDrawable> slotUnderMouse = drawable.getRecipeSlotUnderMouse(mouseX, mouseY);
                    if (slotUnderMouse.isPresent()) {
                        targetStack = slotUnderMouse.get().getDisplayedItemStack().orElse(ItemStack.EMPTY);
                    }

                    // Se não clicou em um slot específico, pega o output da receita
                    if (targetStack.isEmpty()) {
                        var outputSlots = drawable.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.OUTPUT);
                        if (!outputSlots.isEmpty()) {
                            targetStack = outputSlots.get(0).getDisplayedItemStack().orElse(ItemStack.EMPTY);
                        }
                    }

                    String recipeIdStr = "";
                    Object recipeObj = drawable.getRecipe();
                    if (recipeObj instanceof RecipeHolder<?> holder) {
                        recipeIdStr = holder.id().toString();
                    }

                    String itemIdStr = "";
                    if (!targetStack.isEmpty()) {
                        itemIdStr = BuiltInRegistries.ITEM.getKey(targetStack.getItem()).toString();
                    }

                    if (!itemIdStr.isEmpty() || !recipeIdStr.isEmpty()) {
                        String key = itemIdStr + "|" + recipeIdStr;
                        if (!key.equals(lastTargetKey) || (now - lastClickTime >= 350)) {
                            lastTargetKey = key;
                            lastClickTime = now;
                            LOGGER.info("[AE2UniversalPattern] JEI recipe clicked: item={}, recipe={}. Sending packet to server.",
                                    itemIdStr, recipeIdStr);
                            PacketDistributor.sendToServer(new JEIRecipeClickPayload(itemIdStr, recipeIdStr));
                        }
                    }
                }
            }
            return;
        }

        // 2. Clicou em um item da lista do JEI (lateral ou bookmarks) enquanto o terminal ME está aberto
        if (jeiRuntime != null) {
            ItemStack stackUnderMouse = ItemStack.EMPTY;
            var overlay = jeiRuntime.getIngredientListOverlay();
            if (overlay != null) {
                ItemStack item = overlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
                if (item != null && !item.isEmpty()) {
                    stackUnderMouse = item;
                }
            }
            if (stackUnderMouse.isEmpty()) {
                var bookmarks = jeiRuntime.getBookmarkOverlay();
                if (bookmarks != null) {
                    ItemStack item = bookmarks.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
                    if (item != null && !item.isEmpty()) {
                        stackUnderMouse = item;
                    }
                }
            }

            if (!stackUnderMouse.isEmpty()) {
                String itemIdStr = BuiltInRegistries.ITEM.getKey(stackUnderMouse.getItem()).toString();
                String key = itemIdStr + "|";
                if (!key.equals(lastTargetKey) || (now - lastClickTime >= 350)) {
                    lastTargetKey = key;
                    lastClickTime = now;
                    LOGGER.info("[AE2UniversalPattern] JEI list item clicked: {}. Sending packet to server.", itemIdStr);
                    PacketDistributor.sendToServer(new JEIRecipeClickPayload(itemIdStr, ""));
                }
            }
        }
    }
}
