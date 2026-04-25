package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record CyclePrimaryPayload(int direction) implements CustomPayload {
    public static final CustomPayload.Id<CyclePrimaryPayload> ID = new CustomPayload.Id<>(Agstack.id("cycle_primary"));
    public static final PacketCodec<RegistryByteBuf, CyclePrimaryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, CyclePrimaryPayload::direction,
            CyclePrimaryPayload::new
    );
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}