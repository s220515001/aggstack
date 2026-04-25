package tfh.agstack.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

public class ExtractSubItemHandler implements ServerPlayNetworking.PlayPayloadHandler<ExtractSubItemPayload> {
    @Override
    public void receive(ExtractSubItemPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        ScreenHandler handler = player.currentScreenHandler;

        if (handler.syncId != payload.syncId()) return;
        if (payload.slotId() < 0 || payload.slotId() >= handler.slots.size()) return;

        Slot slot = handler.getSlot(payload.slotId());
        if (slot == null) return;

        ItemStack stack = slot.getStack();
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) return;
        if (payload.subItemIndex() < 0 || payload.subItemIndex() >= comp.subItems().size()) return;

        // 处理快速移动 (Shift+左键)
        if (payload.quickMove()) {
            // 提取子物品副本
            ItemStack subItem = comp.getSubItem(payload.subItemIndex()).copy();
            if (subItem.isEmpty()) return;

            // 移除该子物品
            AggregatedStackComponent newComp = comp.removeSubItem(payload.subItemIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
                slot.setStack(stack);
            }

            // 尝试将子物品快速移动到容器中（使用原版快速移动逻辑）
            ItemStack remaining = handler.quickMove(player, slot.id);
            // 如果快速移动没有完全成功（剩余物品不为空），则将剩余物品给玩家或掉落
            if (!remaining.isEmpty()) {
                player.getInventory().offerOrDrop(remaining);
            }
            return;
        }

        // 普通左键（取出到鼠标）
        if (payload.button() == 0) {
            ItemStack subItem = comp.getSubItem(payload.subItemIndex()).copy();
            if (!subItem.isEmpty()) {
                AggregatedStackComponent newComp = comp.removeSubItem(payload.subItemIndex());
                if (newComp == null || newComp.subItems().isEmpty()) {
                    slot.setStack(ItemStack.EMPTY);
                } else {
                    stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
                    slot.setStack(stack);
                }
                handler.setCursorStack(subItem);
            }
        }
        // 右键（设为顶层）
        else if (payload.button() == 1) {
            AggregatedStackComponent newComp = comp.withNewPrimary(payload.subItemIndex());
            stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
            slot.setStack(stack);
        }
    }
}