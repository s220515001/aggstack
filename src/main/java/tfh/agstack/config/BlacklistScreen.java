package tfh.agstack.config;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.stream.Collectors;

public class BlacklistScreen extends Screen {
    private final Screen parent;
    private Set<String> workingBlacklist;
    private List<ItemStack> allItemVariants;          // 所有物品变种（包括药水变种）
    private List<ItemStack> categoryFiltered;          // 当前分类过滤后的物品
    private List<ItemStack> filteredItems;             // 最终显示（搜索过滤后）
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private TextFieldWidget searchBox;
    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;
    private CyclingButtonWidget<Category> categoryButton;
    private ButtonWidget batchDisableButton;
    private ButtonWidget batchEnableButton;

    // 鼠标拖动切换状态
    private boolean isLeftMouseDown = false;
    private Set<Integer> toggledIndices = new HashSet<>();

    // 布局参数
    private static final int SLOT_SIZE = 18;
    private static final int CELL_SPACING = 24;
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int VISIBLE_ROWS = 5;
    private static final int HEADER_HEIGHT = 75; // 增加高度以容纳分类按钮

    // 颜色常量
    private static final int BACKGROUND_DARKEN = 0xAA000000;
    private static final int SLOT_BG_COLOR = 0xAA3A3A3A;
    private static final int BLACKLIST_OVERLAY = 0x99000000;

    public enum Category {
        ALL("全部"),
        BLOCKS("方块"),
        ITEMS("物品"),
        POTIONS("药水"),
        ENCHANTED_BOOKS("附魔书");

        private final String name;
        Category(String name) { this.name = name; }
        public Text getText() { return Text.literal(name); }
    }

    public BlacklistScreen(Screen parent) {
        super(Text.translatable("agstack.blacklist.title"));
        this.parent = parent;
        this.workingBlacklist = new HashSet<>(ModConfig.get().blacklistedItems);
    }

    @Override
    protected void init() {
        super.init();
        buildAllItemVariants();

        // 初始化分类按钮（循环切换）
        categoryButton = CyclingButtonWidget.<Category>builder(category -> category.getText())
                .values(Category.values())
                .initially(Category.ALL)
                .build(0, 0, 100, 20, Text.literal("分类"), (button, category) -> {
                    onCategoryChanged(category);
                });

        // 批量禁用按钮：将当前显示的所有物品加入黑名单
        batchDisableButton = ButtonWidget.builder(Text.literal("全部禁用"), btn -> {
            for (ItemStack stack : filteredItems) {
                String key = ModConfig.getBlacklistKeyStatic(stack);
                workingBlacklist.add(key);
            }
            // 刷新显示（不需要重建列表，只需要重绘）
        }).dimensions(0, 0, 80, 20).build();

        // 批量启用按钮：将当前显示的所有物品移出黑名单
        batchEnableButton = ButtonWidget.builder(Text.literal("全部启用"), btn -> {
            for (ItemStack stack : filteredItems) {
                String key = ModConfig.getBlacklistKeyStatic(stack);
                workingBlacklist.remove(key);
            }
        }).dimensions(0, 0, 80, 20).build();

        // 搜索框
        searchBox = new TextFieldWidget(textRenderer, width / 2 - 80, 20, 160, 16, Text.translatable("agstack.blacklist.search"));
        searchBox.setMaxLength(50);
        searchBox.setChangedListener(text -> filterItems());

        // 完成/取消按钮
        doneButton = ButtonWidget.builder(Text.translatable("gui.done"), btn -> saveAndClose())
                .dimensions(width - 210, height - 28, 100, 20).build();
        cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), btn -> close())
                .dimensions(width - 105, height - 28, 100, 20).build();

        // 添加控件
        addDrawableChild(categoryButton);
        addDrawableChild(batchDisableButton);
        addDrawableChild(batchEnableButton);
        addDrawableChild(searchBox);
        addDrawableChild(doneButton);
        addDrawableChild(cancelButton);

        // 初始分类
        onCategoryChanged(Category.ALL);
        updateScrollLimit();
    }

    private void buildAllItemVariants() {
        allItemVariants = new ArrayList<>();

        // 1. 药水变种（按效果细分）
        for (Potion potion : Registries.POTION) {
            Optional<RegistryEntry<Potion>> entryOptional = Optional.ofNullable(Registries.POTION.getEntry(potion));
            if (!entryOptional.isPresent()) continue;
            RegistryEntry<Potion> potionEntry = entryOptional.get();
            PotionContentsComponent contents = PotionContentsComponent.DEFAULT.with(potionEntry);

            allItemVariants.add(createPotion(Items.POTION, contents));
            allItemVariants.add(createPotion(Items.SPLASH_POTION, contents));
            allItemVariants.add(createPotion(Items.LINGERING_POTION, contents));
        }

        // 2. 其他普通物品（包括附魔书基类）
        for (Item item : Registries.ITEM) {
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) {
                allItemVariants.add(stack);
            }
        }
    }

    private ItemStack createPotion(Item item, PotionContentsComponent contents) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.POTION_CONTENTS, contents);
        return stack;
    }

    private void onCategoryChanged(Category category) {
        // 根据分类筛选物品
        categoryFiltered = new ArrayList<>();
        for (ItemStack stack : allItemVariants) {
            switch (category) {
                case ALL:
                    categoryFiltered.add(stack);
                    break;
                case BLOCKS:
                    if (stack.getItem() instanceof BlockItem) categoryFiltered.add(stack);
                    break;
                case ITEMS:
                    // 物品：非方块、非药水变种、非附魔书基类
                    if (!(stack.getItem() instanceof BlockItem) &&
                            stack.get(DataComponentTypes.POTION_CONTENTS) == null &&
                            stack.getItem() != Items.ENCHANTED_BOOK) {
                        categoryFiltered.add(stack);
                    }
                    break;
                case POTIONS:
                    if (stack.get(DataComponentTypes.POTION_CONTENTS) != null) categoryFiltered.add(stack);
                    break;
                case ENCHANTED_BOOKS:
                    if (stack.getItem() == Items.ENCHANTED_BOOK) categoryFiltered.add(stack);
                    break;
            }
        }
        // 重新应用搜索过滤
        filterItems();
    }

    private void filterItems() {
        String query = searchBox.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            filteredItems = new ArrayList<>(categoryFiltered);
        } else {
            filteredItems = categoryFiltered.stream()
                    .filter(stack -> stack.getName().getString().toLowerCase().contains(query) ||
                            Registries.ITEM.getId(stack.getItem()).toString().toLowerCase().contains(query))
                    .collect(Collectors.toList());
        }
        scrollOffset = 0;
        updateScrollLimit();
    }

    private void updateScrollLimit() {
        int totalRows = (int) Math.ceil(filteredItems.size() / (double) COLUMNS);
        maxScroll = Math.max(0, totalRows - VISIBLE_ROWS);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private int getSlotIndexAt(double mouseX, double mouseY) {
        int gridWidth = (COLUMNS - 1) * CELL_SPACING + SLOT_SIZE;
        int gridStartX = (width - gridWidth) / 2;
        int gridStartY = HEADER_HEIGHT;

        if (mouseY >= gridStartY && mouseY <= gridStartY + ROWS * CELL_SPACING) {
            int col = (int) ((mouseX - gridStartX) / CELL_SPACING);
            int row = (int) ((mouseY - gridStartY) / CELL_SPACING);
            if (col >= 0 && col < COLUMNS && row >= 0 && row < ROWS) {
                int index = (scrollOffset + row) * COLUMNS + col;
                if (index >= 0 && index < filteredItems.size()) {
                    return index;
                }
            }
        }
        return -1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 5, 0xFFFFFF);

        // 布局控件位置
        int centerX = width / 2;
        categoryButton.setX(centerX - 200);
        categoryButton.setY(25);
        batchDisableButton.setX(centerX - 100);
        batchDisableButton.setY(25);
        batchEnableButton.setX(centerX - 10);
        batchEnableButton.setY(25);
        searchBox.setX(centerX - 80);
        searchBox.setY(45);

        int gridWidth = (COLUMNS - 1) * CELL_SPACING + SLOT_SIZE;
        int gridStartX = (width - gridWidth) / 2;
        int gridStartY = HEADER_HEIGHT;
        int gridHeight = ROWS * CELL_SPACING;

        context.enableScissor(0, gridStartY, width, gridStartY + gridHeight);

        int startIndex = scrollOffset * COLUMNS;
        int endIndex = Math.min(startIndex + ROWS * COLUMNS, filteredItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack stack = filteredItems.get(i);
            int localIndex = i - startIndex;
            int row = localIndex / COLUMNS;
            int col = localIndex % COLUMNS;
            int x = gridStartX + col * CELL_SPACING;
            int y = gridStartY + row * CELL_SPACING;

            context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BG_COLOR);
            context.drawItem(stack, x, y);

            String key = ModConfig.getBlacklistKeyStatic(stack);
            if (workingBlacklist.contains(key)) {
                context.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, BLACKLIST_OVERLAY);
                context.drawText(textRenderer, "✖", x + 2, y + 2, 0xFFFF5555, false);
            }

            if (!isLeftMouseDown && mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE) {
                List<Text> tooltip = new ArrayList<>();
                tooltip.add(stack.getName().copy());
                tooltip.add(Text.literal(Registries.ITEM.getId(stack.getItem()).toString()).formatted(Formatting.GRAY));

                var potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (potionContents != null && potionContents.potion().isPresent()) {
                    String potionId = potionContents.potion().get().getKey().map(k -> k.getValue().toString()).orElse("unknown");
                    tooltip.add(Text.literal("Potion: " + potionId).formatted(Formatting.AQUA));
                }

                if (workingBlacklist.contains(key)) {
                    tooltip.add(Text.translatable("agstack.blacklist.blacklisted").formatted(Formatting.RED));
                } else {
                    tooltip.add(Text.translatable("agstack.blacklist.click_to_toggle").formatted(Formatting.GRAY));
                }
                context.drawTooltip(textRenderer, tooltip, mouseX, mouseY);
            }
        }

        context.disableScissor();

        if (maxScroll > 0) {
            int visibleHeight = ROWS * CELL_SPACING;
            int contentHeight = (int) Math.ceil(filteredItems.size() / (double) COLUMNS) * CELL_SPACING;
            int barHeight = Math.max(30, visibleHeight * visibleHeight / contentHeight);
            int barY = gridStartY + (scrollOffset * (visibleHeight - barHeight) / maxScroll);
            context.fill(width - 6, barY, width - 2, barY + barHeight, 0xCCAAAAAA);
        }

        // 鼠标拖动批量切换
        if (isLeftMouseDown) {
            int currentIndex = getSlotIndexAt(mouseX, mouseY);
            if (currentIndex != -1 && !toggledIndices.contains(currentIndex)) {
                toggleBlacklist(filteredItems.get(currentIndex));
                toggledIndices.add(currentIndex);
            }
        }

        // 渲染所有控件
        categoryButton.render(context, mouseX, mouseY, delta);
        batchDisableButton.render(context, mouseX, mouseY, delta);
        batchEnableButton.render(context, mouseX, mouseY, delta);
        searchBox.render(context, mouseX, mouseY, delta);
        doneButton.render(context, mouseX, mouseY, delta);
        cancelButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKGROUND_DARKEN);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isLeftMouseDown = true;
            toggledIndices.clear();
            int index = getSlotIndexAt(mouseX, mouseY);
            if (index != -1) {
                toggleBlacklist(filteredItems.get(index));
                toggledIndices.add(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isLeftMouseDown = false;
            toggledIndices.clear();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void toggleBlacklist(ItemStack stack) {
        String key = ModConfig.getBlacklistKeyStatic(stack);
        if (workingBlacklist.contains(key)) {
            workingBlacklist.remove(key);
        } else {
            workingBlacklist.add(key);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchBox.isFocused() && searchBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void saveAndClose() {
        ModConfig.get().setBlacklist(workingBlacklist);
        close();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}