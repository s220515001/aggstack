package tfh.agstack.config;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import tfh.agstack.network.ConfigSyncPayload;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private ModConfig workingConfig;
    private boolean hasChildScreen = false;  // 新增：标记是否有子屏幕

    private CyclingButtonWidget<Boolean> enabledToggle;
    private TextFieldWidget maxSubItemsField;
    private CyclingButtonWidget<Boolean> autoPickupToggle;
    private CyclingButtonWidget<ModConfig.ExpandBehavior> expandBehaviorButton;
    private ButtonWidget expandKeyButton;
    private CyclingButtonWidget<ModConfig.DeathDrop> deathDropButton;
    private CyclingButtonWidget<Boolean> creativeAutoToggle;
    private ButtonWidget splitKeyButton;
    private ButtonWidget configKeyButton;
    private ButtonWidget blacklistButton;

    private ButtonWidget doneButton;
    private ButtonWidget cancelButton;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private static final int CONTENT_START_Y = 45;
    private static final int BOTTOM_BUTTONS_PADDING = 20;
    private static final int ITEM_SPACING = 28;

    private String pendingField = null;
    private ButtonWidget pendingButton = null;

    private int animationTicks = 0;
    private static final int ANIMATION_DURATION = 10;

    public ConfigScreen(Screen parent) {
        super(Text.translatable("agstack.config.title"));
        this.parent = parent;
        this.workingConfig = new ModConfig();

        ModConfig current = ModConfig.get();
        workingConfig.enabled = current.enabled;
        workingConfig.maxSubItems = current.maxSubItems;
        workingConfig.autoPickupStack = current.autoPickupStack;
        workingConfig.expandKey = current.expandKey;
        workingConfig.expandBehavior = current.expandBehavior;
        workingConfig.deathDrop = current.deathDrop;
        workingConfig.creativeAutoStack = current.creativeAutoStack;
        workingConfig.splitKey = current.splitKey;
        workingConfig.configKey = current.configKey;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;

        enabledToggle = CyclingButtonWidget.onOffBuilder(workingConfig.enabled)
                .build(0, 0, 200, 20, Text.translatable("agstack.config.enabled"));

        maxSubItemsField = new TextFieldWidget(textRenderer, 0, 0, 100, 20, Text.empty());
        maxSubItemsField.setText(String.valueOf(workingConfig.maxSubItems));
        maxSubItemsField.setTextPredicate(s -> {
            if (s.isEmpty()) return true;
            if (!s.matches("\\d+")) return false;
            int val = Integer.parseInt(s);
            return val >= 1 && val <= 64;
        });

        autoPickupToggle = CyclingButtonWidget.onOffBuilder(workingConfig.autoPickupStack)
                .build(0, 0, 200, 20, Text.translatable("agstack.config.auto_pickup"));

        expandBehaviorButton = CyclingButtonWidget.<ModConfig.ExpandBehavior>builder(behavior ->
                        Text.translatable("agstack.config.expand_behavior." + behavior.name().toLowerCase()))
                .values(ModConfig.ExpandBehavior.values())
                .initially(workingConfig.expandBehavior)
                .build(0, 0, 200, 20, Text.translatable("agstack.config.expand_behavior"));

        expandKeyButton = ButtonWidget.builder(getKeyText(workingConfig.expandKey), btn -> startKeyCapture("expandKey", btn))
                .dimensions(0, 0, 120, 20).build();

        deathDropButton = CyclingButtonWidget.<ModConfig.DeathDrop>builder(drop ->
                        Text.translatable("agstack.config.death_drop." + drop.name().toLowerCase()))
                .values(ModConfig.DeathDrop.values())
                .initially(workingConfig.deathDrop)
                .build(0, 0, 200, 20, Text.translatable("agstack.config.death_drop"));

        creativeAutoToggle = CyclingButtonWidget.onOffBuilder(workingConfig.creativeAutoStack)
                .build(0, 0, 200, 20, Text.translatable("agstack.config.creative_auto"));

        splitKeyButton = ButtonWidget.builder(getKeyText(workingConfig.splitKey), btn -> startKeyCapture("splitKey", btn))
                .dimensions(0, 0, 120, 20).build();

        configKeyButton = ButtonWidget.builder(getKeyText(workingConfig.configKey), btn -> startKeyCapture("configKey", btn))
                .dimensions(0, 0, 120, 20).build();

        // 黑名单按钮 - 修改：打开子屏幕时设置 hasChildScreen = true
        blacklistButton = ButtonWidget.builder(Text.translatable("agstack.config.blacklist"), btn -> {
                    if (client != null) {
                        hasChildScreen = true;
                        client.setScreen(new BlacklistScreen(this));
                    }
                })
                .dimensions(0, 0, 200, 20).build();

        int doneX = width - 110;
        int cancelX = width - 110;
        int btnY = height - BOTTOM_BUTTONS_PADDING - 25;
        doneButton = ButtonWidget.builder(Text.translatable("gui.done"), button -> saveAndClose())
                .dimensions(doneX, btnY, 100, 20).build();
        cancelButton = ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(cancelX, btnY + 25, 100, 20).build();

        updateMaxScroll();
    }

    // 新增：重置子屏幕标志的方法
    public void resetChildScreenFlag() {
        this.hasChildScreen = false;
    }

    private void updateMaxScroll() {
        int totalHeight = 10 * ITEM_SPACING;
        int availableHeight = height - CONTENT_START_Y - 60;
        maxScroll = Math.max(0, totalHeight - availableHeight);
    }

    private void startKeyCapture(String fieldName, ButtonWidget button) {
        pendingField = fieldName;
        pendingButton = button;
        button.setMessage(Text.literal("..."));
    }

    private Text getKeyText(String keyTranslation) {
        String keyName = keyTranslation.replace("key.keyboard.", "").replace(".", " ");
        return Text.literal(keyName.toUpperCase());
    }

    @Override
    public void tick() {
        if (animationTicks < ANIMATION_DURATION) animationTicks++;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pendingField != null) {
            String keyName = getKeyNameFromCode(keyCode, scanCode);
            if (keyName != null) {
                switch (pendingField) {
                    case "expandKey" -> workingConfig.expandKey = keyName;
                    case "splitKey" -> workingConfig.splitKey = keyName;
                    case "configKey" -> workingConfig.configKey = keyName;
                }
                if (pendingButton != null) pendingButton.setMessage(getKeyText(keyName));
            }
            pendingField = null;
            pendingButton = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            scrollOffset = Math.max(0, scrollOffset - 15);
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 15);
            return true;
        }
        if (maxSubItemsField.isFocused()) {
            return maxSubItemsField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollOffset -= (int) (verticalAmount * 15);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String getKeyNameFromCode(int keyCode, int scanCode) {
        String keyName = GLFW.glfwGetKeyName(keyCode, scanCode);
        if (keyName != null) {
            return "key.keyboard." + keyName.toLowerCase();
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "key.keyboard.left.ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "key.keyboard.right.ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "key.keyboard.left.alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "key.keyboard.right.alt";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "key.keyboard.left.shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "key.keyboard.right.shift";
            default -> null;
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 修改：只有当没有子屏幕时才渲染父屏幕
        if (!hasChildScreen && parent != null) {
            parent.render(context, 0, 0, delta);
        }

        float alpha = Math.min(1.0f, animationTicks / (float) ANIMATION_DURATION);
        int bgColor = (int) (0x88 * alpha) << 24;
        context.fill(0, 0, width, height, bgColor);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);

        int centerX = width / 2;
        int currentY = CONTENT_START_Y - scrollOffset;

        int clipTop = CONTENT_START_Y - 5;
        int clipBottom = height - 60;
        context.enableScissor(0, clipTop, width, clipBottom);

        // 启用
        enabledToggle.setX(centerX - 100);
        enabledToggle.setY(currentY);
        enabledToggle.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 最大子物品数
        maxSubItemsField.setX(centerX);
        maxSubItemsField.setY(currentY);
        maxSubItemsField.render(context, mouseX, mouseY, delta);
        context.drawText(textRenderer, Text.translatable("agstack.config.max_sub_items"), centerX - 100, currentY + 5, 0xFFFFFF, true);
        currentY += ITEM_SPACING;

        // 自动拾取
        autoPickupToggle.setX(centerX - 100);
        autoPickupToggle.setY(currentY);
        autoPickupToggle.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 展开行为
        expandBehaviorButton.setX(centerX - 100);
        expandBehaviorButton.setY(currentY);
        expandBehaviorButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 展开键
        context.drawText(textRenderer, Text.translatable("agstack.config.expand_key_label"), centerX - 100, currentY + 5, 0xFFFFFF, true);
        expandKeyButton.setX(centerX + 30);
        expandKeyButton.setY(currentY);
        expandKeyButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 死亡掉落
        deathDropButton.setX(centerX - 100);
        deathDropButton.setY(currentY);
        deathDropButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 创造模式自动聚合
        creativeAutoToggle.setX(centerX - 100);
        creativeAutoToggle.setY(currentY);
        creativeAutoToggle.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 拆分键
        context.drawText(textRenderer, Text.translatable("agstack.config.split_key_label"), centerX - 100, currentY + 5, 0xFFFFFF, true);
        splitKeyButton.setX(centerX + 30);
        splitKeyButton.setY(currentY);
        splitKeyButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 配置键
        context.drawText(textRenderer, Text.translatable("agstack.config.config_key_label"), centerX - 100, currentY + 5, 0xFFFFFF, true);
        configKeyButton.setX(centerX + 30);
        configKeyButton.setY(currentY);
        configKeyButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        // 黑名单按钮
        blacklistButton.setX(centerX - 100);
        blacklistButton.setY(currentY);
        blacklistButton.render(context, mouseX, mouseY, delta);
        currentY += ITEM_SPACING;

        context.disableScissor();

        if (maxScroll > 0) {
            int barHeight = Math.max(30, (int)((float)(height - 60 - CONTENT_START_Y) / (10 * ITEM_SPACING) * (height - 60 - CONTENT_START_Y)));
            int barY = CONTENT_START_Y + (int)((float)scrollOffset / maxScroll * (height - 60 - CONTENT_START_Y - barHeight));
            context.fill(width - 6, barY, width - 2, barY + barHeight, 0xFFAAAAAA);
        }

        doneButton.render(context, mouseX, mouseY, delta);
        cancelButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (doneButton.isMouseOver(mouseX, mouseY)) {
            doneButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (cancelButton.isMouseOver(mouseX, mouseY)) {
            cancelButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (maxSubItemsField.isMouseOver(mouseX, mouseY)) {
            maxSubItemsField.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (enabledToggle.isMouseOver(mouseX, mouseY)) {
            enabledToggle.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (autoPickupToggle.isMouseOver(mouseX, mouseY)) {
            autoPickupToggle.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (expandBehaviorButton.isMouseOver(mouseX, mouseY)) {
            expandBehaviorButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (expandKeyButton.isMouseOver(mouseX, mouseY)) {
            expandKeyButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (deathDropButton.isMouseOver(mouseX, mouseY)) {
            deathDropButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (creativeAutoToggle.isMouseOver(mouseX, mouseY)) {
            creativeAutoToggle.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (splitKeyButton.isMouseOver(mouseX, mouseY)) {
            splitKeyButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (configKeyButton.isMouseOver(mouseX, mouseY)) {
            configKeyButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (blacklistButton.isMouseOver(mouseX, mouseY)) {
            blacklistButton.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void saveAndClose() {
        workingConfig.enabled = enabledToggle.getValue();
        workingConfig.autoPickupStack = autoPickupToggle.getValue();
        workingConfig.expandBehavior = expandBehaviorButton.getValue();
        workingConfig.deathDrop = deathDropButton.getValue();
        workingConfig.creativeAutoStack = creativeAutoToggle.getValue();

        try {
            int val = Integer.parseInt(maxSubItemsField.getText());
            if (val >= 1 && val <= 64) workingConfig.maxSubItems = val;
        } catch (NumberFormatException ignored) {}

        ModConfig.updateFrom(workingConfig);
        if (client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(new ConfigSyncPayload(workingConfig));
        }
        close();
    }

    @Override
    public void close() {
        // 修改：关闭时重置父屏幕的子屏幕标志
        if (parent instanceof ConfigScreen) {
            ((ConfigScreen) parent).resetChildScreenFlag();
        }
        client.setScreen(parent);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        int doneX = width - 110;
        int cancelX = width - 110;
        int btnY = height - BOTTOM_BUTTONS_PADDING - 25;
        doneButton.setX(doneX);
        doneButton.setY(btnY);
        cancelButton.setX(cancelX);
        cancelButton.setY(btnY + 25);
        updateMaxScroll();
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }
}