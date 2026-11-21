package dev.susyst.vanity.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A flat, translucent button for the glass wardrobe. */
public final class GlassButton extends Button {
	public enum Style {
		/** Translucent button. */
		NORMAL,
		/** Segmented control: the selected one is filled with the accent color. */
		SEGMENT,
		/** Text tab: the selected one has accent text and an underline. */
		TAB,
		/** Main action, filled with the accent color. */
		PRIMARY,
		/** A color sample. */
		SWATCH
	}

	private final Style style;
	/** Shown as picked (selected tab, segment or swatch). */
	public boolean selected;
	/** RGB for {@link Style#SWATCH}. */
	public int swatchColor;

	public GlassButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.style = style;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();
		boolean hot = this.active && this.isHoveredOrFocused();
		int textColor = this.active ? Glass.TEXT : Glass.TEXT_OFF;

		switch (this.style) {
			case NORMAL -> {
				Glass.fill(g, x, y, w, h, hot ? Glass.CONTROL_HOVER : Glass.CONTROL);
				Glass.border(g, x, y, w, h, hot ? Glass.alpha(Glass.ACCENT, 0xB0) : Glass.CONTROL_BORDER);
			}
			case SEGMENT -> {
				if (this.selected) {
					Glass.fill(g, x, y, w, h, Glass.alpha(Glass.ACCENT, 0xC8));
					textColor = 0xFF0B1220;
				} else {
					Glass.fill(g, x, y, w, h, hot ? Glass.CONTROL_HOVER : Glass.CONTROL);
				}
			}
			case TAB -> {
				if (hot && !this.selected) Glass.fill(g, x, y, w, h, 0x14FFFFFF);
				if (this.selected) {
					g.fill(x + 4, y + h - 2, x + w - 4, y + h, Glass.ACCENT);
					textColor = Glass.ACCENT;
				} else if (this.active) {
					textColor = hot ? Glass.TEXT : Glass.TEXT_DIM;
				}
			}
			case PRIMARY -> {
				Glass.fill(g, x, y, w, h, Glass.alpha(Glass.ACCENT, hot ? 0xF0 : 0xC0));
				textColor = 0xFF0B1220;
			}
			case SWATCH -> {
				Glass.fill(g, x + 1, y + 2, w - 2, h - 4, 0xFF000000 | (this.swatchColor & 0xFFFFFF));
				if (this.selected || hot) Glass.border(g, x, y + 1, w, h - 2, this.selected ? Glass.TEXT : 0xA0FFFFFF);
				return;
			}
		}

		Font font = Minecraft.getInstance().font;
		Component message = this.getMessage();
		g.centeredText(font, message, x + w / 2, y + (h - 8) / 2, textColor);
	}
}
