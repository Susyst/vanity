package dev.susyst.vanity.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A borderless text box on a translucent field. The given bounds are the whole field. */
public final class GlassEditBox extends EditBox {
	private static final int PAD_X = 6;

	private final int fieldX;
	private final int fieldY;
	private final int fieldW;
	private final int fieldH;

	public GlassEditBox(Font font, int x, int y, int width, int height, Component narration) {
		super(font, x + PAD_X, y + (height - 8) / 2, width - 2 * PAD_X, 10, narration);
		this.fieldX = x;
		this.fieldY = y;
		this.fieldW = width;
		this.fieldH = height;
		this.setBordered(false);
		this.setTextColor(Glass.TEXT);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		boolean hot = this.isFocused() || this.isMouseOver(mouseX, mouseY);
		Glass.fill(g, this.fieldX, this.fieldY, this.fieldW, this.fieldH, 0x40000000);
		Glass.border(g, this.fieldX, this.fieldY, this.fieldW, this.fieldH, this.isFocused() ? Glass.ACCENT : hot ? 0x60FFFFFF : Glass.CONTROL_BORDER);
		super.extractWidgetRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return this.visible && mouseX >= this.fieldX && mouseY >= this.fieldY && mouseX < this.fieldX + this.fieldW && mouseY < this.fieldY + this.fieldH;
	}
}
