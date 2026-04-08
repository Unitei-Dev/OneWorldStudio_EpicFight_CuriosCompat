package com.oneworldstudio.epicfightcurioscompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
final class CuriosResetConfirmScreen extends FixedScaleScreen {
    private static final String I18N_PREFIX = "screen.epicfight_curios_compat.reset_confirm.";
    private static final int PANEL_BG = 0xE014181B;
    private static final int PANEL_BG_ALT = 0xD00B0F12;
    private static final int PANEL_BORDER = 0xA0293136;
    private static final int ACCENT = 0xFF1FD47C;
    private static final int ACCENT_DARK = 0xFF0D8A56;
    private static final int DANGER = 0xFFC43D3D;
    private static final int DANGER_DARK = 0xFF7A1F1F;
    private static final int TEXT = 0xFFF0F6F2;
    private static final int MUTED = 0xFF92A09A;
    private final CuriosPositionEditorScreen parent;

    CuriosResetConfirmScreen(CuriosPositionEditorScreen parent) {
        super(tr("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        initFixedScaleLayout();
        int panelWidth = 430;
        int panelHeight = 170;
        int panelX = (fixedScreenWidth() - panelWidth) / 2;
        int panelY = (fixedScreenHeight() - panelHeight) / 2;
        int buttonY = panelY + 116;

        this.addRenderableWidget(new ConfirmButton(panelX + 42, buttonY, 140, 22, tr("confirm"), true, b -> {
            CuriosEditorConfig.clearAll();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }));
        this.addRenderableWidget(new ConfirmButton(panelX + panelWidth - 182, buttonY, 140, 22, tr("cancel"), false, b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
        }));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int fixedMouseX = fixedMouseX(mouseX);
        int fixedMouseY = fixedMouseY(mouseY);
        beginFixedScale(guiGraphics);
        guiGraphics.fill(0, 0, fixedScreenWidth(), fixedScreenHeight(), 0xD0000000);

        int panelWidth = 430;
        int panelHeight = 170;
        int x = (fixedScreenWidth() - panelWidth) / 2;
        int y = (fixedScreenHeight() - panelHeight) / 2;

        guiGraphics.fillGradient(x, y, x + panelWidth, y + panelHeight, PANEL_BG, PANEL_BG_ALT);
        guiGraphics.fill(x, y, x + panelWidth, y + 1, PANEL_BORDER);
        guiGraphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, PANEL_BORDER);
        guiGraphics.fill(x, y, x + 1, y + panelHeight, PANEL_BORDER);
        guiGraphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, PANEL_BORDER);
        guiGraphics.fillGradient(x + 12, y + 12, x + panelWidth - 12, y + 34, ACCENT_DARK, ACCENT);
        guiGraphics.drawString(this.font, tr("title"), x + 24, y + 19, 0xFF061109, false);
        guiGraphics.drawCenteredString(this.font, tr("message"), fixedScreenWidth() / 2, y + 62, TEXT);
        guiGraphics.drawCenteredString(this.font, tr("warning"), fixedScreenWidth() / 2, y + 82, MUTED);

        super.render(guiGraphics, fixedMouseX, fixedMouseY, partialTick);
        endFixedScale(guiGraphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(fixedMouseX(mouseX), fixedMouseY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(fixedMouseX(mouseX), fixedMouseY(mouseY), button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(I18N_PREFIX + key, args);
    }

    private final class ConfirmButton extends Button {
        private final boolean positive;

        private ConfirmButton(int x, int y, int width, int height, Component message, boolean positive, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.positive = positive;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int fill = this.positive
                    ? hovered ? ACCENT : ACCENT_DARK
                    : hovered ? DANGER : DANGER_DARK;
            int border = this.positive ? ACCENT : DANGER;

            guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, fill);
            guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + 1, border);
            guiGraphics.fill(getX(), getY() + this.height - 1, getX() + this.width, getY() + this.height, border);
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + this.height, border);
            guiGraphics.fill(getX() + this.width - 1, getY(), getX() + this.width, getY() + this.height, border);
            guiGraphics.drawCenteredString(font, getMessage(), getX() + this.width / 2, getY() + (this.height - 8) / 2, TEXT);
        }
    }
}
