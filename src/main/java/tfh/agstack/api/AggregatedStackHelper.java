package tfh.agstack.api;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

import java.util.ArrayList;
import java.util.List;

public final class AggregatedStackHelper implements AggregatedStackAPI {
    public static final AggregatedStackHelper INSTANCE = new AggregatedStackHelper();

    private AggregatedStackHelper() {}

    @Override
    public boolean isAggregatedStack(ItemStack stack) {
        return stack != null && stack.contains(ModDataComponents.AGGREGATED_STACK);
    }

    @Override
    public List<ItemStack> getSubItems(ItemStack stack) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.subItems().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(comp.subItems());
    }

    @Override
    public ItemStack getPrimaryItem(ItemStack stack) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) {
            return ItemStack.EMPTY;
        }
        return comp.getPrimary();
    }

    @Override
    public boolean setPrimaryItem(ItemStack stack, int index) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || index < 0 || index >= comp.subItems().size()) {
            return false;
        }
        stack.set(ModDataComponents.AGGREGATED_STACK, comp.withNewPrimary(index));
        return true;
    }

    @Override
    public boolean addSubItem(ItemStack stack, ItemStack subItem) {
        if (subItem.isEmpty()) return false;

        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) {
            // 如果原物品不是聚合槽，转换为聚合槽
            AggregatedStackComponent newComp = new AggregatedStackComponent(
                    List.of(subItem.copy()), 0
            );
            stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
            return true;
        }

        AggregatedStackComponent newComp = comp.addSubItem(subItem);
        stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
        return true;
    }

    @Override
    public ItemStack removeSubItem(ItemStack stack, int index) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = comp.getSubItem(index).copy();
        AggregatedStackComponent newComp = comp.removeSubItem(index);

        if (newComp == null) {
            stack.remove(ModDataComponents.AGGREGATED_STACK);
        } else {
            stack.set(ModDataComponents.AGGREGATED_STACK, newComp);
        }

        return removed;
    }

    @Override
    public int getSubItemCount(ItemStack stack) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        return comp == null ? 0 : comp.totalCount();
    }

    @Override
    public ItemStack createAggregatedStack(ItemStack item) {
        if (item.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(item.getItem());
        AggregatedStackComponent comp = new AggregatedStackComponent(
                List.of(item.copy()), 0
        );
        result.set(ModDataComponents.AGGREGATED_STACK, comp);
        return result;
    }
}