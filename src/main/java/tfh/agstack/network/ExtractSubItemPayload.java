package tfh.agstack.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import tfh.agstack.Agstack;

public record ExtractSubItemPayload(int syncId, int slotId, int subItemIndex, int button, boolean quickMove) implements CustomPayload {
    public static final CustomPayload.Id<ExtractSubItemPayload> ID = new CustomPayload.Id<>(Agstack.id("extract_subitem"));
    public static final PacketCodec<RegistryByteBuf, ExtractSubItemPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ExtractSubItemPayload::syncId,
            PacketCodecs.INTEGER, ExtractSubItemPayload::slotId,
            PacketCodecs.INTEGER, ExtractSubItemPayload::subItemIndex,
            PacketCodecs.INTEGER, ExtractSubItemPayload::button,
            PacketCodecs.BOOL, ExtractSubItemPayload::quickMove,
            ExtractSubItemPayload::new
    );
    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}