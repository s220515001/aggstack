package tfh.agstack.component;

import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import tfh.agstack.Agstack;

public class ModDataComponents {
    public static final ComponentType<AggregatedStackComponent> AGGREGATED_STACK =
            ComponentType.<AggregatedStackComponent>builder()
                    .codec(AggregatedStackComponent.CODEC)
                    .packetCodec(AggregatedStackComponent.PACKET_CODEC)
                    .build();  // 移除 .syncToClient()

    public static void register() {
        Registry.register(Registries.DATA_COMPONENT_TYPE,
                Agstack.id("aggregated_data"),
                AGGREGATED_STACK);
    }
}