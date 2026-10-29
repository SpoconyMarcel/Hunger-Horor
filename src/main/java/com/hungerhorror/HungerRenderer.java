package com.hungerhorror;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.ZombieEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** 3D model: tall, thin humanoid with pale arms, empty face and glowing red eyes. */
public class HungerRenderer extends MobEntityRenderer<HungerEntity, ZombieEntityModel<HungerEntity>> {
    private static final Identifier TEXTURE =
            Identifier.of(HungerHorrorMod.MOD_ID, "textures/entity/the_hunger.png");

    public HungerRenderer(EntityRendererFactory.Context context) {
        super(context, new ZombieEntityModel<>(context.getPart(HungerHorrorClient.HUNGER_LAYER)), 0.5f);
        this.addFeature(new HungerEyesLayer(this));
    }

    @Override
    public Identifier getTexture(HungerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(HungerEntity entity, MatrixStack matrices, float amount) {
        // taller than a player: 1.5x height, slightly thinner
        matrices.scale(0.95f, 1.5f, 0.95f);
    }
}
