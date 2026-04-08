package com.oneworldstudio.epicfightcurioscompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

@OnlyIn(Dist.CLIENT)
final class CuriosPositionEditorScreen extends FixedScaleScreen {
    private static final String I18N_PREFIX = "screen.epicfight_curios_compat.";
    private static final int TRANSFORM_CONTROL_COUNT = 7;
    private static final int PANEL_BG = 0xD013171B;
    private static final int PANEL_BG_ALT = 0xCC0B0F12;
    private static final int PANEL_BORDER = 0xA0293136;
    private static final int PANEL_ACCENT = 0xFF1FD47C;
    private static final int PANEL_ACCENT_DARK = 0xFF0D8A56;
    private static final int PANEL_ACCENT_SOFT = 0x5520D27C;
    private static final int TEXT_MUTED = 0xFF92A09A;
    private static final int TEXT_NORMAL = 0xFFF0F6F2;
    private static final int TEXT_DISABLED = 0xFF69716E;
    private static final int TEXT_WARNING = 0xFFE2A88D;
    private static final int SLOT_BG = 0xCC11171A;
    private static final int SLOT_BORDER = 0xFF2E3A3E;
    private static final int SLOT_SELECTED = 0xFF26E68E;
    private static final int SLOT_HOVER = 0xFF5E8D7B;
    private static final int SLOT_EMPTY = 0xAA1D2428;
    private static final int PREVIEW_SCALE = 151;
    private static final int SLOT_COLUMNS = 4;
    private static final int SLOT_TILE_SIZE = 54;
    private static final int SLOT_TILE_GAP = 10;
    private static final int SLOT_INNER_SIZE = 20;
    private static final int BUTTON_BG = 0xE6171D20;
    private static final int BUTTON_BG_HOVER = 0xF01B2529;
    private static final int BUTTON_BG_DISABLED = 0xB015181B;
    private static final int BUTTON_BORDER = 0xFF2F4347;
    private static final int FIELD_BG = 0xEE0B0F11;
    private static final int FIELD_BORDER = 0xFF41585C;
    private static final int SLIDER_TRACK = 0xFF1B2327;
    private static final int SLIDER_FILL = 0xFF12995C;
    private static final int SLIDER_FILL_HOVER = 0xFF1FD47C;
    private static final int SLIDER_KNOB = 0xFFDCF6E7;
    private static final int SLIDER_KNOB_SHADOW = 0xFF0D3120;

    private final Player player;
    private final List<ControlRow> controls = new ArrayList<>();
    private final List<CurioSlotView> slotEntries = new ArrayList<>();
    private CurioSlotView selectedSlot;
    private CurioSlotView hoveredSlot;
    private ThemedButton resetButton;
    private ThemedButton standingButton;
    private ThemedButton sittingButton;
    private ThemedButton saveButton;
    private boolean suppressChanges;
    private boolean editingSitting;
    private boolean draggingPreview;
    private float previewYaw = 180.0F;
    private float previewPitch;
    private double lastPreviewMouseX;
    private double lastPreviewMouseY;
    private int leftPanelX;
    private int leftPanelWidth;
    private int rightPanelX;
    private int rightPanelWidth;
    private int rightPanelBottom;
    private int previewX;
    private int previewY;
    private int previewWidth;
    private int previewHeight;
    private int previewCenterX;
    private int previewBaseY;
    private int slotGridX;
    private int slotGridY;
    private int slotVisibleRows;
    private int slotScrollRows;

    CuriosPositionEditorScreen(Player player) {
        super(tr("position_editor"));
        this.player = player;
    }

    @Override
    protected void init() {
        initFixedScaleLayout();
        this.leftPanelX = 18;
        this.leftPanelWidth = 368;
        this.rightPanelWidth = 294;
        this.rightPanelX = fixedScreenWidth() - this.rightPanelWidth - 18;
        this.rightPanelBottom = fixedScreenHeight() - 18;
        this.previewX = this.leftPanelX + this.leftPanelWidth + 26;
        this.previewY = 18;
        this.previewWidth = Math.max(220, this.rightPanelX - 26 - this.previewX);
        this.previewHeight = fixedScreenHeight() - 36;
        this.previewCenterX = this.previewX + this.previewWidth / 2;
        this.previewBaseY = this.previewY + this.previewHeight / 2 + PREVIEW_SCALE;
        this.slotGridX = this.rightPanelX + 22;
        this.slotGridY = 116;
        int slotAreaHeight = this.rightPanelBottom - 210 - this.slotGridY;
        this.slotVisibleRows = 4;

        this.controls.clear();
        createLeftWidgets();
        createPreviewFooterWidgets();
        rebuildSlotEntries();
        updateModeButtons();
        updateControlState();
        syncControlsFromSelection();
    }

    private void createLeftWidgets() {
        int controlX = this.leftPanelX + 18;
        int modeY = 82;
        int translationY = getTranslationRowY();
        int rotationY = getRotationRowY();
        int scaleY = getScaleRowY();

        this.standingButton = this.addRenderableWidget(new ThemedButton(this.leftPanelX + 18, modeY, 146, 20, tr("standing_offset"), b -> switchEditMode(false)));
        this.sittingButton = this.addRenderableWidget(new ThemedButton(this.leftPanelX + 172, modeY, 164, 20, tr("sitting_offset"), b -> switchEditMode(true)));

        this.controls.add(createControl(tr("control.x"), controlX, translationY, -2.0F, 2.0F, 0.0F));
        this.controls.add(createControl(tr("control.y"), controlX, translationY + 26, -2.0F, 2.0F, 0.0F));
        this.controls.add(createControl(tr("control.z"), controlX, translationY + 52, -2.0F, 2.0F, 0.0F));
        this.controls.add(createControl(tr("control.rot_x"), controlX, rotationY, -360.0F, 360.0F, 0.0F));
        this.controls.add(createControl(tr("control.rot_y"), controlX, rotationY + 26, -360.0F, 360.0F, 0.0F));
        this.controls.add(createControl(tr("control.rot_z"), controlX, rotationY + 52, -360.0F, 360.0F, 0.0F));
        this.controls.add(createControl(tr("control.scale"), controlX, scaleY, 0.10F, 3.00F, 1.0F));
    }

    private void createPreviewFooterWidgets() {
        int footerY = fixedScreenHeight() - 28;
        int spacing = 10;
        int resetWidth = 142;
        int saveWidth = 82;
        int buttonHeight = 18;
        int totalWidth = resetWidth + spacing + saveWidth;
        int startX = this.previewCenterX - totalWidth / 2;

        this.resetButton = this.addRenderableWidget(new ThemedButton(startX, footerY, resetWidth, buttonHeight, tr("reset_current_mode"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new CuriosResetConfirmScreen(this));
            }
        }));
        this.saveButton = this.addRenderableWidget(new ThemedButton(startX + resetWidth + spacing, footerY, saveWidth, buttonHeight, tr("save"), b -> onClose()));
    }

    private void rebuildSlotEntries() {
        List<CurioSlotView> entries = collectCurioSlots();
        this.slotEntries.clear();
        this.slotEntries.addAll(entries);
        this.slotScrollRows = Mth.clamp(this.slotScrollRows, 0, Math.max(0, getTotalSlotRows() - this.slotVisibleRows));
        if (this.slotEntries.isEmpty()) {
            this.selectedSlot = null;
            return;
        }

        CurioSlotView fallback = this.selectedSlot == null ? this.slotEntries.get(0) : this.selectedSlot;
        boolean matched = this.selectedSlot == null;
        for (CurioSlotView entry : this.slotEntries) {
            if (entry.sameIdentity(fallback)) {
                fallback = entry;
                matched = true;
                break;
            }
        }
        if (!matched) {
            fallback = this.slotEntries.get(0);
        }
        setSelectedSlot(fallback);
    }

    private List<CurioSlotView> collectCurioSlots() {
        List<CurioSlotView> entries = new ArrayList<>();
        CuriosApi.getCuriosInventory(this.player).ifPresent(handler ->
                handler.getCurios().forEach((slotId, stacksHandler) -> {
                    IDynamicStackHandler normalStacks = stacksHandler.getStacks();
                    IDynamicStackHandler cosmeticStacks = stacksHandler.getCosmeticStacks();
                    NonNullList<Boolean> renders = stacksHandler.getRenders();

                    for (int index = 0; index < normalStacks.getSlots(); index++) {
                        ItemStack cosmeticStack = cosmeticStacks.getStackInSlot(index);
                        ItemStack normalStack = normalStacks.getStackInSlot(index);
                        ItemStack displayStack = ClientCuriosCompat.getEditorCurioStack(normalStack, cosmeticStack);
                        boolean cosmetic = !cosmeticStack.isEmpty();
                        boolean renderable = renders.size() > index && renders.get(index);
                        entries.add(new CurioSlotView(slotId, index, displayStack, cosmetic, renderable));
                    }
                })
        );

        entries.sort(Comparator
                .comparing((CurioSlotView entry) -> ClientCuriosCompat.SlotRule.normalizeSlotId(entry.slotId))
                .thenComparingInt(entry -> entry.index));
        return entries;
    }

    private ControlRow createControl(Component label, int x, int y, float min, float max, float defaultValue) {
        ControlSlider slider = this.addRenderableWidget(new ControlSlider(x + 64, y, 168, 18, label, min, max, defaultValue));
        ThemedEditBox field = this.addRenderableWidget(new ThemedEditBox(this.font, x + 240, y, 78, 18, label));
        field.setMaxLength(12);
        field.setFilter(CuriosPositionEditorScreen::isValidNumericInput);

        ControlRow row = new ControlRow(slider, field, min, max);
        slider.attach(row);
        field.attach(row);
        field.setResponder(text -> {
            if (this.suppressChanges) {
                return;
            }

            Float parsed = tryParseFloat(text);
            if (parsed != null) {
                row.applyTypedValue(parsed);
            }
        });

        boolean previousSuppress = this.suppressChanges;
        this.suppressChanges = true;
        field.setValue(formatValue(defaultValue));
        this.suppressChanges = previousSuppress;
        return row;
    }

    private void switchEditMode(boolean sitting) {
        this.editingSitting = sitting;
        updateModeButtons();
        syncControlsFromSelection();
        updateControlState();
    }

    private void updateModeButtons() {
        if (this.standingButton != null) {
            this.standingButton.active = true;
            this.standingButton.setSelected(!this.editingSitting);
            this.standingButton.setMessage(tr("standing_offset"));
        }
        if (this.sittingButton != null) {
            this.sittingButton.active = true;
            this.sittingButton.setSelected(this.editingSitting);
            this.sittingButton.setMessage(tr("sitting_offset"));
        }
    }

    private void setSelectedSlot(CurioSlotView slot) {
        this.selectedSlot = slot;
        ensureSelectedSlotVisible();
        syncControlsFromSelection();
        updateControlState();
    }

    private void ensureSelectedSlotVisible() {
        if (this.selectedSlot == null) {
            return;
        }

        int index = this.slotEntries.indexOf(this.selectedSlot);
        if (index < 0) {
            return;
        }

        int row = index / SLOT_COLUMNS;
        if (row < this.slotScrollRows) {
            this.slotScrollRows = row;
        } else if (row >= this.slotScrollRows + this.slotVisibleRows) {
            this.slotScrollRows = row - this.slotVisibleRows + 1;
        }
    }

    private void syncControlsFromSelection() {
        this.suppressChanges = true;
        CuriosEditorConfig.TransformOverride override = CuriosEditorConfig.TransformOverride.IDENTITY;
        if (this.selectedSlot != null && this.selectedSlot.isEditable()) {
            override = this.editingSitting
                    ? CuriosEditorConfig.getSittingOverride(this.selectedSlot.slotId, this.selectedSlot.index, this.selectedSlot.stack)
                    : CuriosEditorConfig.getStandingOverride(this.selectedSlot.slotId, this.selectedSlot.index, this.selectedSlot.stack);
        }

        setControlValue(0, override.x);
        setControlValue(1, override.y);
        setControlValue(2, override.z);
        setControlValue(3, override.rotX);
        setControlValue(4, override.rotY);
        setControlValue(5, override.rotZ);
        setControlValue(6, override.scale);
        this.suppressChanges = false;
    }

    private void setControlValue(int index, float value) {
        if (index >= 0 && index < this.controls.size()) {
            this.controls.get(index).setValue(value, false);
        }
    }

    private void updateControlState() {
        boolean active = this.selectedSlot != null && this.selectedSlot.isEditable();
        for (ControlRow row : this.controls) {
            row.slider.active = active;
            row.field.setEditable(active);
            row.field.setTextColor(active ? TEXT_NORMAL : TEXT_DISABLED);
        }
        if (this.resetButton != null) {
            this.resetButton.active = true;
        }
        if (this.saveButton != null) {
            this.saveButton.active = true;
        }
    }

    private void persistSelection() {
        if (this.suppressChanges || this.selectedSlot == null || !this.selectedSlot.isEditable() || this.controls.size() < TRANSFORM_CONTROL_COUNT) {
            return;
        }

        CuriosEditorConfig.TransformOverride override = new CuriosEditorConfig.TransformOverride(
                this.controls.get(0).getValue(),
                this.controls.get(1).getValue(),
                this.controls.get(2).getValue(),
                this.controls.get(3).getValue(),
                this.controls.get(4).getValue(),
                this.controls.get(5).getValue(),
                this.controls.get(6).getValue()
        );

        if (this.editingSitting) {
            CuriosEditorConfig.setSittingOverride(this.selectedSlot.slotId, this.selectedSlot.index, this.selectedSlot.stack, override);
        } else {
            CuriosEditorConfig.setStandingOverride(this.selectedSlot.slotId, this.selectedSlot.index, this.selectedSlot.stack, override);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int fixedMouseX = fixedMouseX(mouseX);
        int fixedMouseY = fixedMouseY(mouseY);
        beginFixedScale(guiGraphics);
        guiGraphics.fill(0, 0, fixedScreenWidth(), fixedScreenHeight(), 0xD0000000);
        this.hoveredSlot = null;

        drawLeftPanel(guiGraphics);
        drawCenterStage(guiGraphics);
        drawRightPanel(guiGraphics, fixedMouseX, fixedMouseY);
        super.render(guiGraphics, fixedMouseX, fixedMouseY, partialTick);
        endFixedScale(guiGraphics);
    }

    private void drawLeftPanel(GuiGraphics guiGraphics) {
        drawPanel(guiGraphics, this.leftPanelX, 18, this.leftPanelWidth, fixedScreenHeight() - 36);
        drawSectionHeader(guiGraphics, this.leftPanelX + 10, 28, this.leftPanelWidth - 20, tr("position_editor"));
        guiGraphics.drawString(this.font, tr("editor_description"), this.leftPanelX + 18, 60, TEXT_MUTED, false);
        guiGraphics.drawString(this.font, tr("mode"), this.leftPanelX + 18, 70, PANEL_ACCENT, false);
        drawSectionHeader(guiGraphics, this.leftPanelX + 10, getSelectionHeaderY(), this.leftPanelWidth - 20, this.editingSitting ? tr("sitting_offset") : tr("standing_offset"));
        drawSelectionInfo(guiGraphics, this.leftPanelX + 18, getSelectionInfoY());
        drawSectionHeader(guiGraphics, this.leftPanelX + 10, getTranslationHeaderY(), this.leftPanelWidth - 20, tr("translation"));
        drawSectionHeader(guiGraphics, this.leftPanelX + 10, getRotationHeaderY(), this.leftPanelWidth - 20, tr("rotation"));
        drawSectionHeader(guiGraphics, this.leftPanelX + 10, getScaleHeaderY(), this.leftPanelWidth - 20, tr("scale"));
        guiGraphics.drawString(this.font, this.editingSitting ? tr("sitting_hint") : tr("standing_hint"), this.leftPanelX + 18, getFooterButtonY() - 16, TEXT_MUTED, false);
    }

    private void drawCenterStage(GuiGraphics guiGraphics) {
        int left = this.previewX;
        int top = this.previewY;
        int right = this.previewX + this.previewWidth;
        int bottom = this.previewY + this.previewHeight;
        guiGraphics.fillGradient(left, top, right, bottom, 0x18000000, 0x38000000);
        guiGraphics.fill(left + 12, top + 12, right - 12, top + 14, PANEL_ACCENT_SOFT);
        guiGraphics.fill(left + 12, bottom - 14, right - 12, bottom - 12, PANEL_ACCENT_SOFT);
        guiGraphics.drawCenteredString(this.font, tr("preview").withStyle(ChatFormatting.BOLD), this.previewCenterX, top + 16, TEXT_NORMAL);
        guiGraphics.drawCenteredString(this.font, tr("preview_hint"), this.previewCenterX, top + 30, TEXT_MUTED);
        renderPreviewEntity(guiGraphics);
    }

    private void drawRightPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        drawPanel(guiGraphics, this.rightPanelX, 18, this.rightPanelWidth, fixedScreenHeight() - 36);
        drawSectionHeader(guiGraphics, this.rightPanelX + 10, 28, this.rightPanelWidth - 20, tr("curios_slots"));
        guiGraphics.drawString(this.font, tr("slot_hint"), this.rightPanelX + 18, 60, TEXT_MUTED, false);
        drawSlotGrid(guiGraphics, mouseX, mouseY);
        drawRightFooter(guiGraphics);
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fillGradient(x, y, x + width, y + height, PANEL_BG, PANEL_BG_ALT);
        guiGraphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        guiGraphics.fill(x, y, x + 1, y + height, PANEL_BORDER);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);
    }

    private void drawSectionHeader(GuiGraphics guiGraphics, int x, int y, int width, Component label) {
        guiGraphics.fillGradient(x, y, x + width, y + 22, PANEL_ACCENT_DARK, PANEL_ACCENT);
        guiGraphics.fill(x + width - 28, y, x + width, y + 22, 0xAA0A0D0F);
        guiGraphics.drawString(this.font, label, x + 10, y + 7, 0xFF061109, false);
    }

    private void drawSelectionInfo(GuiGraphics guiGraphics, int x, int y) {
        if (this.selectedSlot == null) {
            guiGraphics.drawString(this.font, tr("no_curios_slots"), x, y, TEXT_DISABLED, false);
            return;
        }

        guiGraphics.drawString(this.font, tr("selected", this.selectedSlot.prettySlotName()), x, y, TEXT_NORMAL, false);
        guiGraphics.drawString(this.font, abbreviate(this.selectedSlot.describeStack(), this.leftPanelWidth - 36), x, y + 14, this.selectedSlot.isEditable() ? TEXT_MUTED : TEXT_WARNING, false);
    }

    private void drawSlotGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int startIndex = this.slotScrollRows * SLOT_COLUMNS;
        int visibleCapacity = this.slotVisibleRows * SLOT_COLUMNS;
        int endIndex = Math.min(this.slotEntries.size(), startIndex + visibleCapacity);
        for (int visibleIndex = 0; visibleIndex < visibleCapacity; visibleIndex++) {
            int entryIndex = startIndex + visibleIndex;
            CurioSlotView slot = entryIndex < endIndex ? this.slotEntries.get(entryIndex) : null;
            int row = visibleIndex / SLOT_COLUMNS;
            int column = visibleIndex % SLOT_COLUMNS;
            int x = this.slotGridX + column * (SLOT_TILE_SIZE + SLOT_TILE_GAP);
            int y = this.slotGridY + row * (SLOT_TILE_SIZE + SLOT_TILE_GAP);
            boolean hovered = isInside(mouseX, mouseY, x, y, SLOT_TILE_SIZE, SLOT_TILE_SIZE);
            boolean selected = slot != null && slot.sameIdentity(this.selectedSlot);
            int border = selected ? SLOT_SELECTED : hovered ? SLOT_HOVER : SLOT_BORDER;

            guiGraphics.fill(x, y, x + SLOT_TILE_SIZE, y + SLOT_TILE_SIZE, SLOT_BG);
            guiGraphics.fill(x, y, x + SLOT_TILE_SIZE, y + 1, border);
            guiGraphics.fill(x, y + SLOT_TILE_SIZE - 1, x + SLOT_TILE_SIZE, y + SLOT_TILE_SIZE, border);
            guiGraphics.fill(x, y, x + 1, y + SLOT_TILE_SIZE, border);
            guiGraphics.fill(x + SLOT_TILE_SIZE - 1, y, x + SLOT_TILE_SIZE, y + SLOT_TILE_SIZE, border);

            int innerX = x + (SLOT_TILE_SIZE - SLOT_INNER_SIZE) / 2;
            int innerY = y + 16;
            guiGraphics.fill(innerX - 3, innerY - 3, innerX + SLOT_INNER_SIZE + 3, innerY + SLOT_INNER_SIZE + 3, slot == null || slot.stack.isEmpty() ? SLOT_EMPTY : 0xFF1B2428);
            guiGraphics.drawCenteredString(this.font, slot == null ? "---" : slot.slotBadge(), x + SLOT_TILE_SIZE / 2, y + 5, selected ? SLOT_SELECTED : TEXT_MUTED);
            if (slot != null && !slot.stack.isEmpty()) {
                guiGraphics.renderItem(slot.stack, innerX, innerY);
                guiGraphics.renderItemDecorations(this.font, slot.stack, innerX, innerY);
            } else {
                guiGraphics.drawCenteredString(this.font, "-", x + SLOT_TILE_SIZE / 2, innerY + 6, TEXT_DISABLED);
            }
            guiGraphics.drawCenteredString(this.font, slot == null ? "" : Integer.toString(slot.index), x + SLOT_TILE_SIZE / 2, y + SLOT_TILE_SIZE - 11, TEXT_MUTED);
            if (hovered && slot != null) {
                this.hoveredSlot = slot;
            }
        }

        int totalRows = getTotalSlotRows();
        if (totalRows > this.slotVisibleRows) {
            int scrollX = this.rightPanelX + this.rightPanelWidth - 12;
            int scrollTop = this.slotGridY;
            int scrollHeight = this.slotVisibleRows * (SLOT_TILE_SIZE + SLOT_TILE_GAP) - SLOT_TILE_GAP;
            guiGraphics.fill(scrollX, scrollTop, scrollX + 4, scrollTop + scrollHeight, 0x55232A2E);
            int knobHeight = Math.max(18, scrollHeight * this.slotVisibleRows / totalRows);
            int maxScroll = Math.max(1, totalRows - this.slotVisibleRows);
            int knobY = scrollTop + (scrollHeight - knobHeight) * this.slotScrollRows / maxScroll;
            guiGraphics.fill(scrollX, knobY, scrollX + 4, knobY + knobHeight, PANEL_ACCENT);
        }
    }

    private void drawRightFooter(GuiGraphics guiGraphics) {
        int footerY = this.rightPanelBottom - 108;
        guiGraphics.fill(this.rightPanelX + 12, footerY, this.rightPanelX + this.rightPanelWidth - 12, footerY + 84, 0x9920282D);
        guiGraphics.drawString(this.font, tr("selection"), this.rightPanelX + 20, footerY + 10, PANEL_ACCENT, false);
        CurioSlotView display = this.hoveredSlot != null ? this.hoveredSlot : this.selectedSlot;
        if (display == null) {
            guiGraphics.drawString(this.font, tr("no_slot_selected"), this.rightPanelX + 20, footerY + 28, TEXT_DISABLED, false);
            return;
        }

        guiGraphics.drawString(this.font, display.prettySlotName(), this.rightPanelX + 20, footerY + 28, TEXT_NORMAL, false);
        guiGraphics.drawString(this.font, abbreviate(display.describeStack(), this.rightPanelWidth - 40), this.rightPanelX + 20, footerY + 42, TEXT_MUTED, false);
        guiGraphics.drawString(this.font, display.isEditable() ? tr("editable") : tr("empty_or_hidden"), this.rightPanelX + 20, footerY + 58, display.isEditable() ? PANEL_ACCENT : TEXT_WARNING, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double fixedMouseX = fixedMouseX(mouseX);
        double fixedMouseY = fixedMouseY(mouseY);
        if (super.mouseClicked(fixedMouseX, fixedMouseY, button)) {
            return true;
        }

        if (button == 0) {
            CurioSlotView slot = getSlotAt(fixedMouseX, fixedMouseY);
            if (slot != null) {
                setSelectedSlot(slot);
                return true;
            }

            if (isInsidePreview(fixedMouseX, fixedMouseY)) {
                this.draggingPreview = true;
                this.lastPreviewMouseX = fixedMouseX;
                this.lastPreviewMouseY = fixedMouseY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double fixedMouseX = fixedMouseX(mouseX);
        double fixedMouseY = fixedMouseY(mouseY);
        double fixedDragX = fixedDelta(dragX);
        double fixedDragY = fixedDelta(dragY);
        if (this.draggingPreview && button == 0) {
            this.previewYaw = normalizeYaw(this.previewYaw + (float) ((fixedMouseX - this.lastPreviewMouseX) * 2.0D));
            this.previewPitch = Mth.clamp(this.previewPitch + (float) ((this.lastPreviewMouseY - fixedMouseY) * 1.1D), -50.0F, 50.0F);
            this.lastPreviewMouseX = fixedMouseX;
            this.lastPreviewMouseY = fixedMouseY;
            return true;
        }
        return super.mouseDragged(fixedMouseX, fixedMouseY, button, fixedDragX, fixedDragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingPreview = false;
        }
        return super.mouseReleased(fixedMouseX(mouseX), fixedMouseY(mouseY), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double fixedMouseX = fixedMouseX(mouseX);
        double fixedMouseY = fixedMouseY(mouseY);
        if (isInside(fixedMouseX, fixedMouseY, this.rightPanelX, 18, this.rightPanelWidth, fixedScreenHeight() - 36)) {
            int maxScroll = Math.max(0, getTotalSlotRows() - this.slotVisibleRows);
            if (maxScroll > 0) {
                this.slotScrollRows = Mth.clamp(this.slotScrollRows - (int) Math.signum(delta), 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(fixedMouseX, fixedMouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getTotalSlotRows() {
        return Mth.ceil(this.slotEntries.size() / (float) SLOT_COLUMNS);
    }

    private CurioSlotView getSlotAt(double mouseX, double mouseY) {
        int startIndex = this.slotScrollRows * SLOT_COLUMNS;
        int visibleCapacity = this.slotVisibleRows * SLOT_COLUMNS;
        int endIndex = Math.min(this.slotEntries.size(), startIndex + visibleCapacity);
        for (int visibleIndex = 0; visibleIndex < visibleCapacity; visibleIndex++) {
            int entryIndex = startIndex + visibleIndex;
            int row = visibleIndex / SLOT_COLUMNS;
            int column = visibleIndex % SLOT_COLUMNS;
            int x = this.slotGridX + column * (SLOT_TILE_SIZE + SLOT_TILE_GAP);
            int y = this.slotGridY + row * (SLOT_TILE_SIZE + SLOT_TILE_GAP);
            if (entryIndex < endIndex && isInside(mouseX, mouseY, x, y, SLOT_TILE_SIZE, SLOT_TILE_SIZE)) {
                return this.slotEntries.get(entryIndex);
            }
        }
        return null;
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        int hitboxX = this.previewCenterX - PREVIEW_SCALE;
        int hitboxY = this.previewBaseY - PREVIEW_SCALE * 2;
        return isInside(mouseX, mouseY, hitboxX, hitboxY, PREVIEW_SCALE * 2, PREVIEW_SCALE * 2);
    }

    private void renderPreviewEntity(GuiGraphics guiGraphics) {
        float bodyRot = this.player.yBodyRot;
        float yRot = this.player.getYRot();
        float xRot = this.player.getXRot();
        float yHeadRot = this.player.yHeadRot;
        float yHeadRotO = this.player.yHeadRotO;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-this.previewPitch));

        this.player.yBodyRot = this.previewYaw;
        this.player.setYRot(this.previewYaw);
        this.player.setXRot(this.previewPitch);
        this.player.yHeadRot = this.previewYaw;
        this.player.yHeadRotO = this.previewYaw;

        InventoryScreen.renderEntityInInventory(guiGraphics, this.previewCenterX, this.previewBaseY, PREVIEW_SCALE, pose, camera, this.player);

        this.player.yBodyRot = bodyRot;
        this.player.setYRot(yRot);
        this.player.setXRot(xRot);
        this.player.yHeadRot = yHeadRot;
        this.player.yHeadRotO = yHeadRotO;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(I18N_PREFIX + key, args);
    }

    private String abbreviate(String text, int maxWidth) {
        return text == null ? "" : this.font.plainSubstrByWidth(text, maxWidth);
    }

    private int getFooterButtonY() {
        return fixedScreenHeight() - 28;
    }

    private int getScaleRowY() {
        return 436;
    }

    private int getScaleHeaderY() {
        return 408;
    }

    private int getRotationRowY() {
        return 324;
    }

    private int getRotationHeaderY() {
        return 296;
    }

    private int getTranslationRowY() {
        return 212;
    }

    private int getTranslationHeaderY() {
        return 184;
    }

    private int getSelectionHeaderY() {
        return 116;
    }

    private int getSelectionInfoY() {
        return 148;
    }

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static boolean isValidNumericInput(String text) {
        if (text == null) {
            return false;
        }

        return text.isEmpty() || text.matches("-?(\\d+([\\.,]\\d*)?|[\\.,]\\d*)?");
    }

    private static Float tryParseFloat(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Float.parseFloat(text.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private final class ThemedButton extends Button {
        private boolean selected;

        private ThemedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int background;
            int border;
            int textColor;

            if (!this.active) {
                background = BUTTON_BG_DISABLED;
                border = PANEL_BORDER;
                textColor = TEXT_DISABLED;
            } else if (this.selected) {
                background = PANEL_ACCENT_DARK;
                border = PANEL_ACCENT;
                textColor = TEXT_NORMAL;
            } else {
                background = hovered ? BUTTON_BG_HOVER : BUTTON_BG;
                border = hovered ? PANEL_ACCENT : BUTTON_BORDER;
                textColor = hovered ? TEXT_NORMAL : TEXT_MUTED;
            }

            guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + this.height, background);
            guiGraphics.fill(getX(), getY(), getX() + this.width, getY() + 1, border);
            guiGraphics.fill(getX(), getY() + this.height - 1, getX() + this.width, getY() + this.height, border);
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + this.height, border);
            guiGraphics.fill(getX() + this.width - 1, getY(), getX() + this.width, getY() + this.height, border);
            guiGraphics.drawCenteredString(font, getMessage(), getX() + this.width / 2, getY() + (this.height - 8) / 2, textColor);
        }
    }

    private final class ThemedEditBox extends EditBox {
        private ControlRow owner;

        private ThemedEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
            setBordered(false);
            setTextColor(TEXT_NORMAL);
            setTextColorUneditable(TEXT_DISABLED);
            setCanLoseFocus(true);
        }

        private void attach(ControlRow owner) {
            this.owner = owner;
        }

        @Override
        public void setFocused(boolean focused) {
            boolean wasFocused = isFocused();
            super.setFocused(focused);
            if (wasFocused && !focused && this.owner != null) {
                this.owner.normalizeFieldValue();
            }
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            int border = isFocused() ? PANEL_ACCENT : FIELD_BORDER;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), FIELD_BG);
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
            guiGraphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
            guiGraphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
            guiGraphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private final class ControlRow {
        private final ControlSlider slider;
        private final ThemedEditBox field;
        private final float min;
        private final float max;

        ControlRow(ControlSlider slider, ThemedEditBox field, float min, float max) {
            this.slider = slider;
            this.field = field;
            this.min = min;
            this.max = max;
        }

        float getValue() {
            return this.slider.getActualValue();
        }

        void setValue(float value, boolean persist) {
            float clamped = Mth.clamp(value, this.min, this.max);
            CuriosPositionEditorScreen.this.suppressChanges = true;
            this.slider.setActualValue(clamped);
            this.field.setValue(formatValue(clamped));
            CuriosPositionEditorScreen.this.suppressChanges = false;
            if (persist) {
                persistSelection();
            }
        }

        void applyTypedValue(float value) {
            float clamped = Mth.clamp(value, this.min, this.max);
            CuriosPositionEditorScreen.this.suppressChanges = true;
            this.slider.setActualValue(clamped);
            CuriosPositionEditorScreen.this.suppressChanges = false;
            persistSelection();
        }

        void normalizeFieldValue() {
            Float parsed = tryParseFloat(this.field.getValue());
            if (parsed == null) {
                setValue(getValue(), false);
                return;
            }

            setValue(parsed, true);
        }
    }

    private final class ControlSlider extends AbstractSliderButton {
        private final Component label;
        private final float min;
        private final float max;
        private ControlRow owner;

        ControlSlider(int x, int y, int width, int height, Component label, float min, float max, float defaultValue) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.label = label;
            this.min = min;
            this.max = max;
            setActualValue(defaultValue);
        }

        void attach(ControlRow owner) {
            this.owner = owner;
            updateMessage();
        }

        float getActualValue() {
            return (float) Mth.lerp((float) this.value, this.min, this.max);
        }

        void setActualValue(float actualValue) {
            this.value = Mth.clamp((actualValue - this.min) / (this.max - this.min), 0.0D, 1.0D);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(this.label.getString() + ": " + formatValue(getActualValue())));
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int left = getX();
            int top = getY();
            int right = left + this.width;
            int bottom = top + this.height;
            int fillWidth = (int) (this.width * this.value);
            int border = hovered ? PANEL_ACCENT : BUTTON_BORDER;

            guiGraphics.fill(left, top, right, bottom, SLIDER_TRACK);
            if (fillWidth > 0) {
                guiGraphics.fill(left, top, left + fillWidth, bottom, hovered ? SLIDER_FILL_HOVER : SLIDER_FILL);
            }
            guiGraphics.fill(left, top, right, top + 1, border);
            guiGraphics.fill(left, bottom - 1, right, bottom, border);
            guiGraphics.fill(left, top, left + 1, bottom, border);
            guiGraphics.fill(right - 1, top, right, bottom, border);

            int knobX = left + (int) ((this.width - 8) * this.value);
            guiGraphics.fill(knobX, top + 2, knobX + 8, bottom - 2, SLIDER_KNOB_SHADOW);
            guiGraphics.fill(knobX + 1, top + 3, knobX + 7, bottom - 3, SLIDER_KNOB);
            guiGraphics.drawString(font, this.label, left - 52, top + (this.height - 8) / 2, this.active ? TEXT_NORMAL : TEXT_DISABLED, false);
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (this.owner != null) {
                if (!CuriosPositionEditorScreen.this.suppressChanges) {
                    CuriosPositionEditorScreen.this.suppressChanges = true;
                    this.owner.field.setValue(formatValue(getActualValue()));
                    CuriosPositionEditorScreen.this.suppressChanges = false;
                }
                persistSelection();
            }
        }
    }

    private static final class CurioSlotView {
        private final String slotId;
        private final int index;
        private final ItemStack stack;
        private final boolean cosmetic;
        private final boolean renderable;

        private CurioSlotView(String slotId, int index, ItemStack stack, boolean cosmetic, boolean renderable) {
            this.slotId = slotId;
            this.index = index;
            this.stack = stack;
            this.cosmetic = cosmetic;
            this.renderable = renderable;
        }

        private boolean sameIdentity(CurioSlotView other) {
            return other != null && this.index == other.index && this.slotId.equals(other.slotId) && ItemStack.isSameItemSameTags(this.stack, other.stack);
        }

        private boolean isEditable() {
            return ClientCuriosCompat.isEditableCurio(this.slotId, this.stack);
        }

        private String prettySlotName() {
            return ClientCuriosCompat.SlotRule.normalizeSlotId(this.slotId) + " [" + this.index + "]";
        }

        private String slotBadge() {
            String normalized = ClientCuriosCompat.SlotRule.normalizeSlotId(this.slotId).toUpperCase(Locale.ROOT);
            return normalized.length() <= 3 ? normalized : normalized.substring(0, 3);
        }

        private String describeStack() {
            if (this.stack.isEmpty()) {
                return tr("slot_empty").getString();
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(this.stack.getItem());
            String itemText = this.stack.getHoverName().getString();
            if (itemId != null) {
                itemText += " [" + itemId + "]";
            }
            if (this.cosmetic) {
                itemText += " | " + tr("stack_cosmetic").getString();
            }
            if (!this.renderable) {
                itemText += " | " + tr("stack_hidden").getString();
            }
            return itemText;
        }
    }
}
