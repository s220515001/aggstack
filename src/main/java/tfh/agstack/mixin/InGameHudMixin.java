package tfh.agstack.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tfh.agstack.component.AggregatedStackComponent;
import tfh.agstack.component.ModDataComponents;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void onRenderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        renderAggregatedOverlay(context, stack, x, y);
    }

    @Inject(method = "renderOverlay", at = @At("TAIL"))
    private void onRenderOverlay(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        // 可选：在物品栏界面外也渲染，但通常不需要
    }

    private void renderAggregatedOverlay(DrawContext context, ItemStack stack, int x, int y) {
        AggregatedStackComponent comp = stack.get(ModDataComponents.AGGREGATED_STACK);
        if (comp == null || comp.subItems().isEmpty()) return;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        String count = String.valueOf(comp.totalCount());
        int countX = x + 16 - textRenderer.getWidth(count);
        int countY = y + 16 - 10;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.drawText(textRenderer, count, countX, countY, 0xFFFFFF, true);
        context.getMatrices().pop();

        // 耐久条也需要在物品之上？耐久条绘制使用 fill，默认在相同层，但为了安全也可提升
        ItemStack primary = comp.getPrimary();
        if (primary.isDamageable()) {
            int damage = primary.getDamage();
            int maxDamage = primary.getMaxDamage();
            if (damage > 0) {
                int barWidth = Math.round(13.0F - (float) damage * 13.0F / (float) maxDamage);
                int barColor = 0xFFFF0000;
                context.fill(x + 2, y + 13, x + 2 + barWidth, y + 14, barColor);
            }
        }
    }
}