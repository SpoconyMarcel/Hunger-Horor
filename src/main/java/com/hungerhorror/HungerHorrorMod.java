package com.hungerhorror;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HungerHorrorMod implements ModInitializer {
    public static final String MOD_ID = "hungerhorror";
    public static final HungerConfig CONFIG = HungerConfig.load();

    public static EntityType<HungerEntity> HUNGER;

    private static final RegistryKey<DamageType> HUNGER_DAMAGE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(MOD_ID, "hunger_monster"));

    /** player UUID -> monster UUID */
    private static final Map<UUID, UUID> CHASES = new HashMap<>();
    /** player UUID -> server tick when the next chase is allowed */
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    /** player UUID -> server tick of the next scream */
    private static final Map<UUID, Integer> NEXT_SCREAM = new HashMap<>();
    /** player UUID -> server tick when the monster vanishes (set after the player ate) */
    private static final Map<UUID, Integer> VANISH_AT = new HashMap<>();

    @Override
    public void onInitialize() {
        HUNGER = Registry.register(Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "the_hunger"),
                FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, HungerEntity::new)
                        .dimensions(EntityDimensions.fixed(0.7f, 2.9f))
                        .trackRangeBlocks(128)
                        .build());
        FabricDefaultAttributeRegistry.register(HUNGER, HungerEntity.createAttributes());

        PayloadTypeRegistry.playS2C().register(ChaseStatePayload.ID, ChaseStatePayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(HungerHorrorMod::onTick);

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> removeMonster(server, handler.player.getUuid()));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CHASES.clear();
            COOLDOWNS.clear();
            NEXT_SCREAM.clear();
            VANISH_AT.clear();
        });
    }

    private static void onTick(MinecraftServer server) {
        int ticks = server.getTicks();
        if (ticks % 5 != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            handlePlayer(server, player, ticks);
        }
    }

    private static void handlePlayer(MinecraftServer server, ServerPlayerEntity player, int ticks) {
        UUID playerId = player.getUuid();
        ServerWorld world = player.getServerWorld();
        boolean valid = player.isAlive() && !player.isCreative() && !player.isSpectator();
        int food = player.getHungerManager().getFoodLevel();
        UUID monsterId = CHASES.get(playerId);

        if (monsterId != null) {
            Entity entity = world.getEntity(monsterId);
            if (valid && entity instanceof HungerEntity monster && monster.isAlive()) {
                Integer vanishAt = VANISH_AT.get(playerId);

                // the player ate: the monster stands close, stares at the player, then vanishes
                if (vanishAt != null) {
                    faceTarget(monster, player);
                    if (ticks >= vanishAt) {
                        vanish(world, monster);
                        endChase(server, player, ticks);
                    }
                    return;
                }
                if (food > CONFIG.hungerThreshold) {
                    beginVanish(world, player, monster, ticks);
                    return;
                }

                // normal chase
                monster.setTarget(player);
                int distance = Math.round(monster.distanceTo(player));
                if (monster.squaredDistanceTo(player) < 2.5 * 2.5) {
                    kill(world, player, monster);
                    endChase(server, player, ticks);
                    return;
                }
                sendState(player, true, distance);
                scream(world, player, monster, distance, ticks);
                return;
            }
            // player died, changed dimension, or killed the monster
            endChase(server, player, ticks);
            return;
        }

        if (!valid || food > CONFIG.hungerThreshold) {
            return;
        }
        if (ticks < COOLDOWNS.getOrDefault(playerId, 0)) {
            return;
        }
        // random delay so it does not appear at the exact moment hunger hits the threshold
        if (world.getRandom().nextInt(30) != 0) {
            return;
        }
        startChase(world, player, ticks);
    }

    private static void startChase(ServerWorld world, ServerPlayerEntity player, int ticks) {
        BlockPos spawn = findSpawn(world, player);
        if (spawn == null) {
            return;
        }
        HungerEntity monster = HUNGER.create(world);
        if (monster == null) {
            return;
        }
        monster.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                world.getRandom().nextFloat() * 360f, 0f);
        monster.setCustomName(Text.literal("The Hunger"));
        monster.setCustomNameVisible(false);
        monster.setPersistent();
        monster.setTarget(player);
        world.spawnEntity(monster);

        CHASES.put(player.getUuid(), monster.getUuid());
        NEXT_SCREAM.put(player.getUuid(), 0);
        VANISH_AT.remove(player.getUuid());

        int distance = Math.round(monster.distanceTo(player));
        sendState(player, true, distance);
        scream(world, player, monster, distance, ticks);
    }

    /** The player ate. Move the monster right next to the player, freeze it and make it stare. */
    private static void beginVanish(ServerWorld world, ServerPlayerEntity player, HungerEntity monster, int ticks) {
        VANISH_AT.put(player.getUuid(), ticks + Math.max(1, CONFIG.vanishDelaySeconds) * 20);

        BlockPos near = findNear(world, player);
        if (near != null) {
            monster.refreshPositionAndAngles(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0f, 0f);
        }
        monster.setTarget(null);
        monster.getNavigation().stop();
        monster.setVelocity(Vec3d.ZERO);
        monster.setAiDisabled(true);
        faceTarget(monster, player);

        sendState(player, false, 0); // red screen off
        world.playSound(null, monster.getX(), monster.getY(), monster.getZ(),
                SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 3.0f, 0.5f);
    }

    private static void faceTarget(HungerEntity monster, ServerPlayerEntity player) {
        monster.lookAtEntity(player, 360.0f, 360.0f);
        monster.setBodyYaw(monster.getYaw());
        monster.setHeadYaw(monster.getYaw());
    }

    private static void vanish(ServerWorld world, HungerEntity monster) {
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, monster.getX(), monster.getY() + 1.5, monster.getZ(),
                60, 0.4, 1.2, 0.4, 0.02);
        world.playSound(null, monster.getX(), monster.getY(), monster.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.5f, 0.5f);
    }

    /** Loud screams: several layered sounds, more often the closer the monster is. */
    private static void scream(ServerWorld world, ServerPlayerEntity player, HungerEntity monster, int distance, int ticks) {
        UUID id = player.getUuid();
        if (ticks < NEXT_SCREAM.getOrDefault(id, 0)) {
            return;
        }
        int interval = distance > 30 ? 90 : distance > 15 ? 60 : distance > 6 ? 35 : 20;
        NEXT_SCREAM.put(id, ticks + interval);

        float pitch = 0.4f + world.getRandom().nextFloat() * 0.3f;
        // volume > 1 makes the sound audible from further away (4.0 = ~64 blocks)
        world.playSound(null, monster.getX(), monster.getY(), monster.getZ(),
                SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 4.0f, pitch);
        world.playSound(null, monster.getX(), monster.getY(), monster.getZ(),
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 4.0f, pitch + 0.1f);
        if (distance <= 10) {
            // jumpscare layer right in the player's ears
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 3.0f, 0.5f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 3.0f, 0.6f);
        }
    }

    /** Random spot far from the player (where the monster first appears). */
    private static BlockPos findSpawn(ServerWorld world, ServerPlayerEntity player) {
        BlockPos origin = player.getBlockPos();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * CONFIG.spawnDistance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * CONFIG.spawnDistance);
            BlockPos pos = standable(world, x, origin.getY(), z, 8, 12);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    /** Spot a few blocks in front of the player (where the monster stares at them). */
    private static BlockPos findNear(ServerWorld world, ServerPlayerEntity player) {
        BlockPos origin = player.getBlockPos();
        double yaw = Math.toRadians(player.getYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        int[] distances = {6, 5, 7, 4};
        double[] offsets = {0.0, 0.5, -0.5, 1.0, -1.0};
        for (int d : distances) {
            for (double off : offsets) {
                double c = Math.cos(off);
                double s = Math.sin(off);
                int x = origin.getX() + (int) Math.round((fx * c - fz * s) * d);
                int z = origin.getZ() + (int) Math.round((fx * s + fz * c) * d);
                BlockPos pos = standable(world, x, origin.getY(), z, 3, 4);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static BlockPos standable(ServerWorld world, int x, int baseY, int z, int up, int down) {
        for (int dy = up; dy >= -down; dy--) {
            BlockPos pos = new BlockPos(x, baseY + dy, z);
            if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
                    && world.getBlockState(pos).isAir()
                    && world.getBlockState(pos.up()).isAir()
                    && world.getBlockState(pos.up(2)).isAir()) {
                return pos;
            }
        }
        return null;
    }

    private static void kill(ServerWorld world, ServerPlayerEntity player, HungerEntity monster) {
        Registry<DamageType> registry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE);
        RegistryEntry<DamageType> type = registry.entryOf(HUNGER_DAMAGE);
        player.damage(new DamageSource(type, monster), Float.MAX_VALUE);
    }

    private static void endChase(MinecraftServer server, ServerPlayerEntity player, int ticks) {
        removeMonster(server, player.getUuid());
        COOLDOWNS.put(player.getUuid(), ticks + CONFIG.cooldownSeconds * 20);
        sendState(player, false, 0);
    }

    private static void removeMonster(MinecraftServer server, UUID playerId) {
        NEXT_SCREAM.remove(playerId);
        VANISH_AT.remove(playerId);
        UUID monsterId = CHASES.remove(playerId);
        if (monsterId == null) {
            return;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(monsterId);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private static void sendState(ServerPlayerEntity player, boolean active, int distance) {
        if (ServerPlayNetworking.canSend(player, ChaseStatePayload.ID)) {
            ServerPlayNetworking.send(player, new ChaseStatePayload(active, distance));
        }
    }
}
