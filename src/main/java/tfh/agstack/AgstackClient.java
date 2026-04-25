package tfh.agstack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import tfh.agstack.config.ConfigScreen;
import tfh.agstack.config.ModConfig;
import tfh.agstack.screen.ExpandedPanel;

public class AgstackClient implements ClientModInitializer {
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        System.out.println("[agstack] AgstackClient initializing...");

        // 注册配置界面快捷键
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.agstack.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                "category.agstack"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 检查配置按键
            if (configKeyBinding.wasPressed()) {
                System.out.println("[agstack] Config key pressed");
                if (client.currentScreen == null) {
                    client.setScreen(new ConfigScreen(null));
                }
            }

            // 检查展开按键（用于HOLD模式）
            if (client.player != null && client.currentScreen != null) {
                ModConfig config = ModConfig.get();
                if (config.expandBehavior == ModConfig.ExpandBehavior.HOLD) {
                    boolean keyDown = isKeyPressed(config.expandKey);
                    ExpandedPanel.setKeyHeld(keyDown);
                }
            }
        });

        System.out.println("[agstack] AgstackClient initialized");
    }

    private boolean isKeyPressed(String keyTranslation) {
        int keyCode = getKeyCodeFromTranslation(keyTranslation);
        if (keyCode == -1) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return InputUtil.isKeyPressed(window, keyCode);
    }

    private int getKeyCodeFromTranslation(String translation) {
        return switch (translation) {
            case "key.keyboard.left.alt" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "key.keyboard.right.alt" -> GLFW.GLFW_KEY_RIGHT_ALT;
            case "key.keyboard.left.ctrl" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "key.keyboard.right.ctrl" -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "key.keyboard.left.shift" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "key.keyboard.right.shift" -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            default -> -1;
        };
    }
}