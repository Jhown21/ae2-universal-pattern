package com.example.ae2universalpattern.client.jei;

import com.example.ae2universalpattern.AE2UniversalPatternMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class AE2UniversalPatternJeiPlugin implements IModPlugin {

    public static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(AE2UniversalPatternMod.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JEIInteractionHelper.setJeiRuntime(jeiRuntime);
    }

    @Override
    public void onRuntimeUnavailable() {
        JEIInteractionHelper.setJeiRuntime(null);
    }
}
