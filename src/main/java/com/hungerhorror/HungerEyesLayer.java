package com.hungerhorror;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.util.Identifier;

public class HungerEyesLayer extends EyesFeatureRenderer<HungerEntity, HungerModel> {
    private static final RenderLayer EYES = RenderLayer.getEyes(
            Identifier.of(HungerHorrorMod.MOD_ID, "textures/entity/the_hunger_eyes.png"));

    public HungerEyesLayer(FeatureRendererContext<HungerEntity, HungerModel> context) {
        super(context);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return EYES;
    }
}
