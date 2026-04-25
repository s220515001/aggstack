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
import tfh.agstack.util.DropFlag;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void onDropInventory(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (player.getWorld().isClient) return;
        if (player.getWorld().getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;

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

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void onDropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (DropFlag.shouldSkipDropProcessing()) {
            return;
        }
        if (stack.isEmpty()) return;
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack primary = comp.getPrimary().copy();
        primary.setCount(1);
        ItemEntity entity = player.dropItem(primary, throwRandomly, retainOwnership);
        stack.decrement(1);
        cir.setReturnValue(entity);
        cir.cancel();
    }

    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/entity/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void onDropItemSimple(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
        if (DropFlag.shouldSkipDropProcessing()) {
            return;
        }
        if (stack.isEmpty()) return;
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        PlayerEntity player = (PlayerEntity) (Object) this;
        ItemStack primary = comp.getPrimary().copy();
        primary.setCount(1);
        ItemEntity entity = player.dropItem(primary, throwRandomly);
        stack.decrement(1);
        cir.setReturnValue(entity);
        cir.cancel();
    }
}