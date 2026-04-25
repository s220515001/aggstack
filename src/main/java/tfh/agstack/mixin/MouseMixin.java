package tfh.agstack.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;
import tfh.agstack.network.CyclePrimaryPayload;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null) return;

        boolean ctrlDown = net.minecraft.client.util.InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
        ) || net.minecraft.client.util.InputUtil.isKeyPressed(
                client.getWindow().getHandle(),
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL
        );
        if (!ctrlDown) return;

        if (client.player == null) return;
        ItemStack mainHand = client.player.getMainHandStack();
        AggregatedStackComponent comp = mainHand.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null) return;

        ci.cancel(); // 阻止原版滚动切换快捷栏

        int direction = vertical > 0 ? 1 : -1;
        int newIndex = comp.primaryIndex() + direction;
        if (newIndex < 0) newIndex = comp.subItems().size() - 1;
        if (newIndex >= comp.subItems().size()) newIndex = 0;

        if (newIndex != comp.primaryIndex()) {
            ItemStack newPrimary = comp.getSubItem(newIndex);
            if (!newPrimary.isEmpty()) {
                // 使用原版切换物品的提示方式
            }
        }

        ClientPlayNetworking.send(new CyclePrimaryPayload(direction));
    }
}