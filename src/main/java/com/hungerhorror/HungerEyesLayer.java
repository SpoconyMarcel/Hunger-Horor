package com.hungerhorror;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.util.Identifier;

/** Glowing red eyes (rendered full-bright, also in the dark). */
public class HungerEyesLayer extends EyesFeatureRenderer<HungerEntity, ZombieEntityModel<HungerEntity>> {
    private static final RenderLayer EYES = RenderLayer.getEyes(
            Identifier.of(HungerHorrorMod.MOD_ID, "textures/entity/the_hunger_eyes.png"));

    public HungerEyesLayer(FeatureRendererContext<HungerEntity, ZombieEntityModel<HungerEntity>> context) {
        super(context);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return EYES;
    }
}
