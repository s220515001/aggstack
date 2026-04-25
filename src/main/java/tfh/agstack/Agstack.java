package tfh.agstack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;
import tfh.agstack.network.*;

public class Agstack implements ModInitializer {
	public static final String MOD_ID = "agstack";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 注册数据组件
		ModDataComponents.register();

		// 加载配置
		ModConfig.load();

		// 注册网络包（双向）
		PayloadTypeRegistry.playC2S().register(CyclePrimaryPayload.ID, CyclePrimaryPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CyclePrimaryPayload.ID, new CyclePrimaryHandler());
		PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, new ConfigSyncHandler());
		PayloadTypeRegistry.playC2S().register(ExtractSubItemPayload.ID, ExtractSubItemPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ExtractSubItemPayload.ID, new ExtractSubItemHandler());

		LOGGER.info("Aggregated Stack Mod initialized successfully");
	}

	public static net.minecraft.util.Identifier id(String path) {
		return net.minecraft.util.Identifier.of(MOD_ID, path);
	}
}