package tfh.agstack.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow public abstract int getCount();
    @Shadow public abstract void setCount(int count);
    @Shadow public abstract ItemStack copy();

    @Unique
    private static final ThreadLocal<Boolean> processing = ThreadLocal.withInitial(() -> false);

    /**
     * 拦截 setCount，强制聚合槽数量只能为 0 或 1
     */
    @Inject(method = "setCount", at = @At("HEAD"), cancellable = true)
    private void onSetCount(int newCount, CallbackInfo ci) {
        if (processing.get()) return;
        ItemStack self = (ItemStack)(Object)this;
        AggregatedStackComponent comp = self.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        int oldCount = getCount();

        if (newCount != 0 && newCount != 1) {
            if (newCount < oldCount && newCount == 0) {
                // 正常清空，允许
            } else {
                ci.cancel();
                return;
            }
        }

        if (newCount < oldCount) {
            ci.cancel();
            processing.set(true);
            try {
                int consumed = oldCount - newCount;
                ItemStack primary = comp.getPrimary().copy();
                int primaryCount = primary.getCount();
                if (primaryCount <= consumed) {
                    AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                    if (newComp == null || newComp.subItems().isEmpty()) {
                        self.setCount(0);
                    } else {
                        self.set(ModDataComponents.AGGREGATED_STACK, newComp);
                        newComp.applyToItemStack(self);
                        self.setCount(1);
                    }
                } else {
                    primary.setCount(primaryCount - consumed);
                    AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
                    self.set(ModDataComponents.AGGREGATED_STACK, newComp);
                    newComp.applyToItemStack(self);
                    self.setCount(1);
                }
            } finally {
                processing.set(false);
            }
        }
    }

    /**
     * 处理耐久消耗
     */
    @Inject(method = "damage(ILnet/minecraft/entity/LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V",
            at = @At("HEAD"), cancellable = true)
    private void onDamage(int amount, LivingEntity livingEntity, EquipmentSlot slot, CallbackInfo ci) {
        if (processing.get()) return;
        ItemStack self = (ItemStack)(Object)this;
        AggregatedStackComponent comp = self.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        processing.set(true);
        try {
            ItemStack primary = comp.getPrimary().copy();
            primary.damage(amount, livingEntity, slot);
            if (primary.getDamage() >= primary.getMaxDamage()) {
                AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                if (newComp == null || newComp.subItems().isEmpty()) {
                    self.setCount(0);
                } else {
                    self.set(ModDataComponents.AGGREGATED_STACK, newComp);
                    newComp.applyToItemStack(self);
                }
            } else {
                self.set(ModDataComponents.AGGREGATED_STACK, comp.withUpdatedPrimary(primary));
                comp.applyToItemStack(self);
            }
        } finally {
            processing.set(false);
        }
        ci.cancel();
    }

    /**
     * 拦截 finishUsing，使聚合栈中的药水/炖菜能正确触发效果并返还容器
     */
    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void onFinishUsing(World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (processing.get()) return;
        ItemStack self = (ItemStack)(Object)this;
        AggregatedStackComponent comp = self.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.isEmpty()) return;

        processing.set(true);
        try {
            ItemStack primary = comp.getPrimary().copy();
            ItemStack usedResult = primary.finishUsing(world, user);

            if (ItemStack.areItemsAndComponentsEqual(primary, usedResult)) {
                int oldCount = primary.getCount();
                int newCount = usedResult.getCount();
                if (newCount < oldCount) {
                    int consumed = oldCount - newCount;
                    reducePrimaryByCount(comp, self, consumed);
                }
            } else {
                AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                if (!usedResult.isEmpty()) {
                    if (user instanceof PlayerEntity player) {
                        player.getInventory().offerOrDrop(usedResult);
                    } else {
                        user.dropStack(usedResult);
                    }
                }
                if (newComp == null || newComp.subItems().isEmpty()) {
                    self.setCount(0);
                    cir.setReturnValue(ItemStack.EMPTY);
                } else {
                    self.set(ModDataComponents.AGGREGATED_STACK, newComp);
                    newComp.applyToItemStack(self);
                    self.setCount(1);
                    cir.setReturnValue(self);
                }
                cir.cancel();
                return;
            }

            if (self.isEmpty()) {
                cir.setReturnValue(ItemStack.EMPTY);
            } else {
                cir.setReturnValue(self);
            }
            cir.cancel();
        } finally {
            processing.set(false);
        }
    }


    @Unique
    private void reducePrimaryByCount(AggregatedStackComponent comp, ItemStack self, int amount) {
        ItemStack primary = comp.getPrimary().copy();
        int newCount = primary.getCount() - amount;
        if (newCount <= 0) {
            AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                self.setCount(0);
            } else {
                self.set(ModDataComponents.AGGREGATED_STACK, newComp);
                newComp.applyToItemStack(self);
                self.setCount(1);
            }
        } else {
            primary.setCount(newCount);
            AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
            self.set(ModDataComponents.AGGREGATED_STACK, newComp);
            newComp.applyToItemStack(self);
        }
    }
}