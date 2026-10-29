package com.hungerhorror;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public class HungerHorrorClient implements ClientModInitializer {
    public static final EntityModelLayer HUNGER_LAYER =
            new EntityModelLayer(Identifier.of(HungerHorrorMod.MOD_ID, "the_hunger"), "main");

    private static boolean chasing = false;
    private static int distance = 0;
    private static long startMs = 0;

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(HUNGER_LAYER,
                () -> TexturedModelData.of(BipedEntityModel.getModelData(Dilation.NONE, 0.0f), 64, 64));
        EntityRendererRegistry.register(HungerHorrorMod.HUNGER, HungerRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(ChaseStatePayload.ID, (payload, context) -> {
            boolean was = chasing;
            chasing = payload.active();
            distance = payload.distance();
            if (chasing && !was) {
                startMs = Util.getMeasuringTimeMs();
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> chasing = false);
        HudRenderCallback.EVENT.register(HungerHorrorClient::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!chasing || !HungerHorrorMod.CONFIG.showScreen) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        long t = Util.getMeasuringTimeMs() - startMs;

        // red overlay: the closer the monster, the stronger and faster it pulses
        double maxDist = Math.max(10, HungerHorrorMod.CONFIG.spawnDistance + 5);
        double closeness = Math.max(0.0, Math.min(1.0, 1.0 - distance / maxDist));
        double speed = 140.0 - closeness * 90.0;
        double pulse = 0.5 + 0.5 * Math.sin(t / speed);
        int alpha = (int) (30 + closeness * 140 + pulse * (20 + closeness * 60));
        alpha = Math.min(alpha, 245);
        if (t < 400) {
            alpha = Math.max(alpha, 210);
        }
        int red = 140 + (int) (closeness * 80);
        context.fill(0, 0, width, height, (alpha << 24) | (red << 16));

        // shaking RUN RUN RUN
        float shakeX = (float) Math.sin(t / 25.0) * 2f;
        float shakeY = (float) Math.cos(t / 31.0) * 2f;
        drawScaled(context, client, Text.literal("RUN RUN RUN"),
                width / 2f + shakeX, height / 3f + shakeY, 4f, 0xFFFF2020);

        // distance from the monster
        int color = distance < 10 ? 0xFFFF2020 : 0xFFFFFFFF;
        drawScaled(context, client, Text.literal("THE HUNGER: " + distance + " BLOCKS"),
                width / 2f, height / 3f + 50f, 2f, color);
    }

    private static void drawScaled(DrawContext context, MinecraftClient client, Text text,
                                   float x, float y, float scale, int color) {
        int textWidth = client.textRenderer.getWidth(text);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0f);
        matrices.scale(scale, scale, 1f);
        context.drawTextWithShadow(client.textRenderer, text, -textWidth / 2, -4, color);
        matrices.pop();
    }
}
