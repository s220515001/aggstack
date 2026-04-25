package tfh.agstack.mixin;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;

import java.util.List;

@Mixin(PlayerInventory.class)
public abstract class InventoryMixin {

    @Inject(method = "insertStack(Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.get();
        if (!config.autoPickupStack) return;
        if (stack.isEmpty()) return;

        // 黑名单物品不参与任何聚合，直接交给原版逻辑（放入普通槽位或丢弃）
        if (config.isBlacklisted(stack)) {
            return;
        }

        // 如果拾取的就是聚合槽，交给原版（聚合槽数量强制为1）
        if (stack.get(ModDataComponents.AGGREGATED_STACK) != null) {
            if (stack.getCount() != 1) stack.setCount(1);
            return;
        }

        PlayerInventory inv = (PlayerInventory)(Object)this;

        // 1. 尝试放入已有的聚合槽
        for (int i = 0; i < inv.size(); i++) {
            ItemStack existing = inv.getStack(i);
            AggregatedStackComponent comp = existing.get(ModDataComponents.AGGREGATED_STACK);
            if (comp != null && existing.getItem() == stack.getItem() &&
                    comp.getSubItemCount() < config.maxSubItems) {
                // 确保组件兼容（避免不同药水混装）
                if (!ItemStack.areItemsAndComponentsEqual(comp.getPrimary(), stack)) {
                    continue;
                }
                // 聚合槽本身的主物品可能已经被黑名单（但聚合槽创建时已保证不会包含黑名单物品）
                AggregatedStackComponent newComp = comp.addSubItem(stack.copy());
                existing.set(ModDataComponents.AGGREGATED_STACK, newComp);
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            }
        }

        // 2. 没有聚合槽，但存在同ID且组件不同的普通物品 -> 创建聚合槽（前提：两个物品都不在黑名单）
        for (int i = 0; i < inv.size(); i++) {
            ItemStack existing = inv.getStack(i);
            if (!existing.isEmpty() && existing.getItem() == stack.getItem() &&
                    existing.get(ModDataComponents.AGGREGATED_STACK) == null &&
                    !ItemStack.areItemsAndComponentsEqual(existing, stack)) {
                // 检查现有物品和当前物品是否在黑名单中
                if (config.isBlacklisted(existing) || config.isBlacklisted(stack)) {
                    continue;
                }
                AggregatedStackComponent newComp = new AggregatedStackComponent(
                        List.of(existing.copy(), stack.copy()), 0);
                ItemStack aggregated = new ItemStack(stack.getItem());
                aggregated.set(ModDataComponents.AGGREGATED_STACK, newComp);
                inv.setStack(i, aggregated);
                stack.setCount(0);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}