package tfh.agstack.api;

import net.minecraft.item.ItemStack;
import tfh.agstack.component.ModDataComponents;

import java.util.List;

public interface AggregatedStackAPI {
    /**
     * 判断物品栈是否为聚合槽
     */
    boolean isAggregatedStack(ItemStack stack);

    /**
     * 获取所有子物品列表
     */
    List<ItemStack> getSubItems(ItemStack stack);

    /**
     * 获取顶层物品
     */
    ItemStack getPrimaryItem(ItemStack stack);

    /**
     * 设置顶层物品（通过索引）
     * @return 是否成功
     */
    boolean setPrimaryItem(ItemStack stack, int index);

    /**
     * 添加子物品
     * @return 是否成功
     */
    boolean addSubItem(ItemStack stack, ItemStack subItem);

    /**
     * 移除子物品
     * @return 被移除的物品
     */
    ItemStack removeSubItem(ItemStack stack, int index);

    /**
     * 获取子物品数量
     */
    int getSubItemCount(ItemStack stack);

    /**
     * 将普通物品转换为聚合槽
     * @return 新的聚合槽物品栈
     */
    ItemStack createAggregatedStack(ItemStack item);
}

