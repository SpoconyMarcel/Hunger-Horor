package com.hungerhorror;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: chase active + distance (blocks) from the monster. */
public record ChaseStatePayload(boolean active, int distance) implements CustomPayload {
    public static final CustomPayload.Id<ChaseStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(HungerHorrorMod.MOD_ID, "chase_state"));

    public static final PacketCodec<RegistryByteBuf, ChaseStatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.BOOL, ChaseStatePayload::active,
                    PacketCodecs.VAR_INT, ChaseStatePayload::distance,
                    ChaseStatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
