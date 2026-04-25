package tfh.agstack.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tfh.agstack.Agstack;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.config.ModConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {

    @Shadow public abstract ItemStack getCursorStack();
    @Shadow public abstract void setCursorStack(ItemStack stack);
    @Shadow public abstract ItemStack quickMove(PlayerEntity player, int slot);

    @Unique
    private boolean justSplitAggregated = false;
    @Unique
    private int lastSplitSlot = -1;

    private static final Set<Class<?>> DISALLOWED_HANDLERS = Set.of(
            CraftingScreenHandler.class,
            AbstractFurnaceScreenHandler.class,
            BrewingStandScreenHandler.class,
            MerchantScreenHandler.class,
            GrindstoneScreenHandler.class,
            AnvilScreenHandler.class,
            SmithingScreenHandler.class,
            StonecutterScreenHandler.class,
            LoomScreenHandler.class,
            CartographyTableScreenHandler.class
    );

    private boolean isAllowedContainer(ScreenHandler handler) {
        for (Class<?> disallowed : DISALLOWED_HANDLERS) {
            if (disallowed.isInstance(handler)) {
                return false;
            }
        }
        return true;
    }

    @Inject(method = "internalOnSlotClick", at = @At("HEAD"), cancellable = true)
    private void onSlotClick(int slotIndex, int button, SlotActionType actionType,
                             PlayerEntity player, CallbackInfo ci) {
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (slotIndex < 0 || slotIndex >= handler.slots.size()) return;

        Slot slot = handler.slots.get(slotIndex);
        ItemStack slotStack = slot.getStack();
        AggregatedStackComponent slotComp = slotStack.get(ModDataComponents.AGGREGATED_STACK);
        ItemStack cursor = getCursorStack();
        AggregatedStackComponent cursorComp = cursor.get(ModDataComponents.AGGREGATED_STACK);
        boolean isPlayerInventory = slot.inventory instanceof PlayerInventory;
        boolean allowed = isAllowedContainer(handler);
        ModConfig config = ModConfig.get();

        // ===== 处理 QUICK_MOVE (Shift+左键) =====
        if (actionType == SlotActionType.QUICK_MOVE) {
            if (justSplitAggregated && slotIndex == lastSplitSlot) {
                justSplitAggregated = false;
                lastSplitSlot = -1;
                ci.cancel();
                return;
            }

            if (slotComp != null) {
                // 从聚合槽中拆分一个物品（如果主物品是黑名单，也应该允许拆分？不阻止）
                splitOneItemFromAggregated(slot, slotComp, player);
                justSplitAggregated = true;
                lastSplitSlot = slotIndex;
                ci.cancel();
                return;
            }
            return;
        }

        // ===== 双击合并（PICKUP_ALL）优先处理，跳过黑名单物品 =====
        if (actionType == SlotActionType.PICKUP_ALL && button == 0 && !slotStack.isEmpty()) {
            handlePickupAll(slotIndex, player, handler);
            ci.cancel();
            return;
        }

        // ===== 鼠标持有聚合槽并点击空格子 =====
        if (slotComp == null && slotStack.isEmpty() && !cursor.isEmpty()) {
            if (cursorComp != null) {
                // 检查光标上的聚合槽整体是否含有黑名单物品？不应该，因为黑名单物品无法进入聚合槽
                slot.setStack(cursor.copy());
                setCursorStack(ItemStack.EMPTY);
                ci.cancel();
                return;
            }
        }

        // 禁止容器逻辑：如果光标上是聚合槽，不允许放入；如果槽位是聚合槽且光标是普通物品，不允许交互
        if (!allowed && !isPlayerInventory) {
            if (cursorComp != null) {
                ci.cancel();
                return;
            }
            if (slotComp != null && !cursor.isEmpty() && cursorComp == null) {
                ci.cancel();
                return;
            }
        }

        // ===== 拖拽创建聚合槽（两个普通物品），检查黑名单 =====
        if (slotComp == null && !slotStack.isEmpty() && !cursor.isEmpty() &&
                cursor.getItem() == slotStack.getItem() &&
                cursorComp == null &&
                actionType == SlotActionType.PICKUP && button == 0) {

            if (config.isBlacklisted(slotStack) || config.isBlacklisted(cursor)) {
                ci.cancel();
                return;
            }

            if (canMergeNormally(cursor, slotStack)) {
                return;
            }
            createAggregatedFromTwoStacks(slot, slotStack, cursor);
            ci.cancel();
            return;
        }

        // ===== 聚合槽相关交互 =====
        if (slotComp != null) {

            if (actionType == SlotActionType.THROW) {
                handleThrow(slot, slotComp, player);
                ci.cancel();
                return;
            }

            if (actionType == SlotActionType.PICKUP && button == 0) {

                if (cursorComp != null && cursor.getItem() == slotStack.getItem()) {
                    // 合并两个聚合槽，检查来源聚合槽是否含有黑名单物品
                    if (containsBlacklisted(cursorComp)) {
                        ci.cancel();
                        return;
                    }
                    mergeAggregatedStacks(slot, slotStack, slotComp, cursor, cursorComp);
                    ci.cancel();
                    return;
                }

                if (cursor.isEmpty()) {
                    setCursorStack(slotStack.copy());
                    slot.setStack(ItemStack.EMPTY);
                    ci.cancel();
                    return;
                }

                // Shift+左键将光标上的普通物品添加到聚合槽
                if (player.isSneaking() && cursorComp == null && cursor.getItem() == slotStack.getItem()) {
                    if (slotComp.getSubItemCount() < config.maxSubItems && !config.isBlacklisted(cursor)) {
                        AggregatedStackComponent newComp = slotComp.addSubItem(cursor.copy());
                        ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newComp);
                        newComp.applyToItemStack(newSlotStack);
                        slot.setStack(newSlotStack);
                        setCursorStack(ItemStack.EMPTY);
                    }
                    ci.cancel();
                    return;
                }

                // 普通交换
                ItemStack temp = slotStack.copy();
                slot.setStack(cursor.copy());
                setCursorStack(temp);
                ci.cancel();
                return;
            }

            if (actionType == SlotActionType.PICKUP && button == 1) {
                handleRightClick(slot, slotStack, slotComp, cursor);
                ci.cancel();
                return;
            }
        }
    }

    // ==================== 辅助方法 ====================

    @Unique
    private boolean containsBlacklisted(AggregatedStackComponent comp) {
        ModConfig config = ModConfig.get();
        for (ItemStack sub : comp.subItems()) {
            if (config.isBlacklisted(sub)) return true;
        }
        return false;
    }

    @Unique
    private void handlePickupAll(int slotIndex, PlayerEntity player, ScreenHandler handler) {
        ModConfig config = ModConfig.get();
        Slot clickedSlot = handler.slots.get(slotIndex);
        ItemStack clickedStack = clickedSlot.getStack();
        if (clickedStack.isEmpty()) return;

        net.minecraft.item.Item targetItem = clickedStack.getItem();

        // 收集所有同物品且不在黑名单中的槽位
        List<Slot> matchingSlots = new ArrayList<>();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && stack.getItem() == targetItem && !config.isBlacklisted(stack)) {
                matchingSlots.add(slot);
            }
        }
        if (matchingSlots.size() <= 1) return;

        List<ItemStack> allSubItems = new ArrayList<>();
        for (Slot slot : matchingSlots) {
            ItemStack stack = slot.getStack();
            AggregatedStackComponent otherComp = stack.get(ModDataComponents.AGGREGATED_STACK);
            if (otherComp != null) {
                allSubItems.addAll(otherComp.subItems());
            } else {
                allSubItems.add(stack.copy());
            }
            slot.setStack(ItemStack.EMPTY);
        }

        int maxSub = config.maxSubItems;
        List<AggregatedStackComponent> aggregates = new ArrayList<>();
        List<ItemStack> currentBatch = new ArrayList<>();
        for (ItemStack sub : allSubItems) {
            if (currentBatch.size() >= maxSub) {
                aggregates.add(new AggregatedStackComponent(new ArrayList<>(currentBatch), 0));
                currentBatch.clear();
            }
            currentBatch.add(sub.copy());
        }
        if (!currentBatch.isEmpty()) {
            aggregates.add(new AggregatedStackComponent(new ArrayList<>(currentBatch), 0));
        }

        List<Slot> slotsToFill = new ArrayList<>();
        slotsToFill.add(clickedSlot);
        for (Slot slot : matchingSlots) {
            if (slot != clickedSlot) slotsToFill.add(slot);
        }

        int fillIndex = 0;
        for (AggregatedStackComponent agg : aggregates) {
            ItemStack finalStack = new ItemStack(targetItem);
            finalStack.set(ModDataComponents.AGGREGATED_STACK, agg);
            agg.applyToItemStack(finalStack);
            if (fillIndex < slotsToFill.size()) {
                slotsToFill.get(fillIndex).setStack(finalStack);
                fillIndex++;
            } else {
                player.getInventory().offerOrDrop(finalStack);
            }
        }
    }

    @Unique
    private void mergeAggregatedStacks(Slot slot, ItemStack slotStack,
                                       AggregatedStackComponent targetComp,
                                       ItemStack cursorStack,
                                       AggregatedStackComponent sourceComp) {
        ModConfig config = ModConfig.get();
        int maxItems = config.maxSubItems;
        int currentCount = targetComp.getSubItemCount();
        int available = maxItems - currentCount;

        if (available <= 0) {
            ItemStack temp = slotStack.copy();
            slot.setStack(cursorStack.copy());
            setCursorStack(temp);
            return;
        }

        List<ItemStack> sourceItems = new ArrayList<>(sourceComp.subItems());
        List<ItemStack> newTargetItems = new ArrayList<>(targetComp.subItems());
        int added = 0;

        for (ItemStack sub : sourceItems) {
            if (added >= available) break;
            // 检查子物品是否在黑名单中，如果是则不允许合并（实际上不应该发生）
            if (config.isBlacklisted(sub)) continue;
            newTargetItems.add(sub.copy());
            added++;
        }

        AggregatedStackComponent newTargetComp = new AggregatedStackComponent(
                newTargetItems,
                targetComp.primaryIndex()
        );
        ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newTargetComp);
        newTargetComp.applyToItemStack(newSlotStack);
        slot.setStack(newSlotStack);

        if (added >= sourceItems.size()) {
            setCursorStack(ItemStack.EMPTY);
        } else {
            List<ItemStack> remaining = sourceItems.subList(added, sourceItems.size());
            int newPrimary = sourceComp.primaryIndex() - added;
            if (newPrimary < 0) newPrimary = 0;

            AggregatedStackComponent newSourceComp = new AggregatedStackComponent(
                    new ArrayList<>(remaining),
                    newPrimary
            );
            ItemStack newCursor = cursorStack.copy();
            newCursor.set(ModDataComponents.AGGREGATED_STACK, newSourceComp);
            newSourceComp.applyToItemStack(newCursor);
            setCursorStack(newCursor);
        }
    }

    @Unique
    private void splitOneItemFromAggregated(Slot slot, AggregatedStackComponent comp, PlayerEntity player) {
        ItemStack primary = comp.getPrimary().copy();
        ItemStack splitItem;
        if (primary.getCount() > 1) {
            splitItem = primary.copy();
            splitItem.setCount(1);
            splitItem.remove(ModDataComponents.AGGREGATED_STACK);
            primary.setCount(primary.getCount() - 1);
            AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
            ItemStack newSlotStack = createAggregatedStack(primary.getItem(), newComp);
            newComp.applyToItemStack(newSlotStack);
            slot.setStack(newSlotStack);
        } else {
            splitItem = primary.copy();
            splitItem.remove(ModDataComponents.AGGREGATED_STACK);
            AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
            if (newComp == null || newComp.subItems().isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                ItemStack newSlotStack = createAggregatedStack(primary.getItem(), newComp);
                newComp.applyToItemStack(newSlotStack);
                slot.setStack(newSlotStack);
            }
        }
        PlayerInventory inv = player.getInventory();
        if (!inv.insertStack(splitItem)) {
            player.dropItem(splitItem, false);
        }
    }

    @Unique
    private boolean canMergeNormally(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsAndComponentsEqual(a, b)) return false;
        int total = a.getCount() + b.getCount();
        return total <= a.getMaxCount();
    }

    @Unique
    private void createAggregatedFromTwoStacks(Slot slot, ItemStack slotStack, ItemStack cursorStack) {
        AggregatedStackComponent newComp = new AggregatedStackComponent(
                List.of(cursorStack.copy(), slotStack.copy()), 0);
        ItemStack aggregated = createAggregatedStack(slotStack.getItem(), newComp);
        newComp.applyToItemStack(aggregated);
        slot.setStack(aggregated);
        setCursorStack(ItemStack.EMPTY);
    }

    @Unique
    private void handleRightClick(Slot slot, ItemStack slotStack,
                                  AggregatedStackComponent comp, ItemStack cursorStack) {
        ModConfig config = ModConfig.get();
        if (cursorStack.isEmpty()) {
            ItemStack primary = comp.getPrimary().copy();
            if (primary.getMaxCount() > 1) {
                int half = (int) Math.ceil(primary.getCount() / 2.0);
                ItemStack halfStack = primary.copy();
                halfStack.setCount(half);
                setCursorStack(halfStack);
                primary.setCount(primary.getCount() - half);
                if (primary.getCount() <= 0) {
                    AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                    if (newComp == null || newComp.subItems().isEmpty()) {
                        slot.setStack(ItemStack.EMPTY);
                    } else {
                        ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newComp);
                        newComp.applyToItemStack(newSlotStack);
                        slot.setStack(newSlotStack);
                    }
                } else {
                    AggregatedStackComponent newComp = comp.withUpdatedPrimary(primary);
                    ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newComp);
                    newComp.applyToItemStack(newSlotStack);
                    slot.setStack(newSlotStack);
                }
            } else {
                setCursorStack(primary);
                AggregatedStackComponent newComp = comp.removeSubItem(comp.primaryIndex());
                if (newComp == null || newComp.subItems().isEmpty()) {
                    slot.setStack(ItemStack.EMPTY);
                } else {
                    ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newComp);
                    newComp.applyToItemStack(newSlotStack);
                    slot.setStack(newSlotStack);
                }
            }
        } else {
            if (cursorStack.getItem() == slotStack.getItem() &&
                    cursorStack.get(ModDataComponents.AGGREGATED_STACK) == null &&
                    !config.isBlacklisted(cursorStack)) {
                if (comp.getSubItemCount() < config.maxSubItems) {
                    ItemStack toAdd = cursorStack.copy();
                    toAdd.setCount(1);
                    AggregatedStackComponent newComp = comp.addSubItem(toAdd);
                    ItemStack newSlotStack = createAggregatedStack(slotStack.getItem(), newComp);
                    newComp.applyToItemStack(newSlotStack);
                    slot.setStack(newSlotStack);
                    cursorStack.decrement(1);
                    if (cursorStack.isEmpty()) {
                        setCursorStack(ItemStack.EMPTY);
                    } else {
                        setCursorStack(cursorStack);
                    }
                }
            }
        }
    }

    @Unique
    private void handleThrow(Slot slot, AggregatedStackComponent comp, PlayerEntity player) {
        ItemStack slotStack = slot.getStack();
        player.dropItem(slotStack, true);
        slot.setStack(ItemStack.EMPTY);
    }

    @Unique
    private ItemStack createAggregatedStack(net.minecraft.item.Item item,
                                            AggregatedStackComponent comp) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.AGGREGATED_STACK, comp);
        return stack;
    }
}