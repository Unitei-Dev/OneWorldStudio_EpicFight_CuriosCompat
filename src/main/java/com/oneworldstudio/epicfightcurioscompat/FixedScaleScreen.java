package com.oneworldstudio.epicfightcurioscompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
abstract class FixedScaleScreen extends Screen {
    private static final double TARGET_GUI_SCALE = 2.0D;
    private double mouseToFixedScale = 1.0D;
    private double renderScale = 1.0D;
    private int fixedScreenWidth;
    private int fixedScreenHeight;

    protected FixedScaleScreen(Component title) {
        super(title);
    }

    protected final void initFixedScaleLayout() {
        double actualGuiScale = 1.0D;
        if (this.minecraft != null) {
            actualGuiScale = Math.max(1.0D, this.minecraft.getWindow().getGuiScale());
        }

        double targetGuiScale = Math.max(1.0D, TARGET_GUI_SCALE);
        this.mouseToFixedScale = actualGuiScale / targetGuiScale;
        this.renderScale = targetGuiScale / actualGuiScale;
        this.fixedScreenWidth = Math.max(1, (int) Math.round(this.width * this.mouseToFixedScale));
        this.fixedScreenHeight = Math.max(1, (int) Math.round(this.height * this.mouseToFixedScale));
    }

    protected final int fixedScreenWidth() {
        return this.fixedScreenWidth > 0 ? this.fixedScreenWidth : this.width;
    }

    protected final int fixedScreenHeight() {
        return this.fixedScreenHeight > 0 ? this.fixedScreenHeight : this.height;
    }

    protected final int fixedMouseX(double mouseX) {
        return (int) Math.round(mouseX * this.mouseToFixedScale);
    }

    protected final int fixedMouseY(double mouseY) {
        return (int) Math.round(mouseY * this.mouseToFixedScale);
    }

    protected final double fixedDelta(double delta) {
        return delta * this.mouseToFixedScale;
    }

    protected final void beginFixedScale(GuiGraphics guiGraphics) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale((float) this.renderScale, (float) this.renderScale, 1.0F);
    }

    protected final void endFixedScale(GuiGraphics guiGraphics) {
        guiGraphics.pose().popPose();
    }
}
