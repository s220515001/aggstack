package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.config.ModConfig;

public class ConfigSyncHandler implements ServerPlayNetworking.PlayPayloadHandler<ConfigSyncPayload> {
    @Override
    public void receive(ConfigSyncPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        // 权限检查：只有 OP 可以修改服务端配置
        if (player.hasPermissionLevel(2)) {
            ModConfig.updateFrom(payload.config());
        }
    }
}