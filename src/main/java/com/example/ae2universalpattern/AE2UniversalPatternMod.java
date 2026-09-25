package com.example.ae2universalpattern;

import appeng.api.crafting.PatternDetailsHelper;
import com.example.ae2universalpattern.crafting.RecipePatternIndexer;
import com.example.ae2universalpattern.crafting.WildcardPatternDecoder;
import com.example.ae2universalpattern.crafting.WildcardProviderManager;
import com.example.ae2universalpattern.item.WildcardPatternItem;
import com.example.ae2universalpattern.network.SearchQueryPayload;
import com.example.ae2universalpattern.network.ServerSearchHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(AE2UniversalPatternMod.MOD_ID)
public class AE2UniversalPatternMod {

    public static final String MOD_ID = "ae2universalpattern";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredItem<WildcardPatternItem> WILDCARD_PATTERN =
            ITEMS.register("wildcard_pattern", () -> new WildcardPatternItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            CREATIVE_MODE_TABS.register("tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MOD_ID))
                    .icon(() -> new ItemStack(WILDCARD_PATTERN.get()))
                    .displayItems((params, output) -> {
                        output.accept(WILDCARD_PATTERN.get());
                    })
                    .build());

    public AE2UniversalPatternMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PatternDetailsHelper.registerDecoder(WildcardPatternDecoder.INSTANCE);
        });
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == TAB.getKey()) {
            return;
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS
                || event.getTabKey() == CreativeModeTabs.OP_BLOCKS
                || event.getTabKey().location().getNamespace().equals("appeng")) {
            event.accept(WILDCARD_PATTERN.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(MOD_ID).playToServer(
                SearchQueryPayload.TYPE,
                SearchQueryPayload.STREAM_CODEC,
                ServerSearchHandler::handle
        );
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WildcardProviderManager.clearPlayerSearch(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void object, ResourceManager resourceManager, ProfilerFiller profiler) {
                RecipePatternIndexer.invalidateRecipeCache();
                WildcardProviderManager.invalidateCache();
            }
        });
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        RecipePatternIndexer.invalidateRecipeCache();
        WildcardProviderManager.invalidateCache();
    }
}
