package tfh.agstack.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * 死亡掉落拆分逻辑（只有游戏规则允许死亡掉落时才拆分）
     */
    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void onDropInventory(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient) return;

        // 如果死亡不掉落规则开启，不进行任何拆分，保留原版行为
        if (player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) {
            return;
        }

        ModConfig config = ModConfig.get();
        if (config.deathDrop != ModConfig.DeathDrop.SPLIT) return;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);

            if (comp != null && !comp.isEmpty()) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
                for (ItemStack sub : comp.subItems()) {
                    player.dropItem(sub.copy(), true);
                }
            }
        }
    }

    /**
     * 丢弃聚合槽物品时，拆分成多个独立的物品实体（防止捡起后无限复制）
     */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (stack.isEmpty()) return;

        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;

        ItemEntity firstEntity = null;
        for (ItemStack sub : comp.subItems()) {
            ItemEntity entity = player.dropItem(sub.copy(), throwRandomly, retainOwnership);
            if (firstEntity == null) firstEntity = entity;
        }

        // 清空原聚合槽，防止原版再生成一个
        stack.setCount(0);
        cir.setReturnValue(firstEntity);
        cir.cancel();
    }

    /**
     * 简化丢弃方法的重载（兼容不同调用）
     */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void onDropItemSimple(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
        if (stack.isEmpty()) return;

        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;

        ItemEntity firstEntity = null;
        for (ItemStack sub : comp.subItems()) {
            ItemEntity entity = player.dropItem(sub.copy(), throwRandomly);
            if (firstEntity == null) firstEntity = entity;
        }

        stack.setCount(0);
        cir.setReturnValue(firstEntity);
        cir.cancel();
    }
}