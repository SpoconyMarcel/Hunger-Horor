package com.hungerhorror;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;

/** Humanoid model with both arms stretched out in front (zombie-like pose). */
public class HungerModel extends BipedEntityModel<HungerEntity> {
    public HungerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setAngles(HungerEntity entity, float limbAngle, float limbDistance,
                          float animationProgress, float headYaw, float headPitch) {
        super.setAngles(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        float sway = (float) Math.sin(animationProgress * 0.1f) * 0.05f;
        this.rightArm.pitch = -1.45f + sway;
        this.leftArm.pitch = -1.45f - sway;
        this.rightArm.yaw = 0.0f;
        this.leftArm.yaw = 0.0f;
        this.rightArm.roll = 0.0f;
        this.leftArm.roll = 0.0f;
    }
}
