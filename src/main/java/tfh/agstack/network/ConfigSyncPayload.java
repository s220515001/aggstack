package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;
import tfh.agstack.config.ModConfig;

public record ConfigSyncPayload(ModConfig config) implements CustomPayload {
    public static final CustomPayload.Id<ConfigSyncPayload> ID =
            new CustomPayload.Id<>(Agstack.id("config_sync"));

    public static final PacketCodec<RegistryByteBuf, ConfigSyncPayload> CODEC =
            PacketCodec.tuple(
                    ModConfig.PACKET_CODEC, ConfigSyncPayload::config,
                    ConfigSyncPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}