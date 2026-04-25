package tfh.agstack.network;

import tfh.agstack.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.config.ModConfig;
import tfh.agstack.network.ConfigSyncPayload;

public class ConfigSyncReceiver implements ServerPlayNetworking.PlayPayloadHandler<ConfigSyncPayload> {
    @Override
    public void receive(ConfigSyncPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        // 可以添加权限检查
        if (player.hasPermissionLevel(2)) {
            ModConfig.updateFrom(payload.config());
        }
    }
}