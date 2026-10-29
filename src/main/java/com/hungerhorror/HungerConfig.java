package com.hungerhorror;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class HungerConfig {
    /** Monster appears when food level is at or below this (4 = two drumsticks). */
    public int hungerThreshold = 4;
    /** Monster movement speed (zombie = 0.23, player sprint ~0.13 base * 1.3). */
    public double monsterSpeed = 0.42;
    /** How far from the player the monster spawns (blocks). */
    public int spawnDistance = 40;
    /** Seconds of peace after a chase ends. */
    public int cooldownSeconds = 60;
    /** After you eat, the monster stands near you and stares for this many seconds, then vanishes. */
    public int vanishDelaySeconds = 3;
    /** Red screen + RUN text (client side). */
    public boolean showScreen = true;

    public static HungerConfig load() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hungerhorror.json");
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    HungerConfig loaded = gson.fromJson(reader, HungerConfig.class);
                    if (loaded != null) {
                        return loaded;
                    }
                }
            }
            HungerConfig config = new HungerConfig();
            Files.writeString(path, gson.toJson(config));
            return config;
        } catch (IOException | RuntimeException e) {
            return new HungerConfig();
        }
    }
}
