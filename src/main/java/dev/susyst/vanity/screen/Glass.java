package dev.susyst.vanity.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colors and drawing helpers for the frosted-glass wardrobe look. */
public final class Glass {
	/** Accent color (cyan). */
	public static final int ACCENT = 0xFF5BD2FF;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFB4BCC8;
	public static final int TEXT_OFF = 0x66FFFFFF;
	/** Dark, mostly opaque glass panels. */
	public static final int PANEL = 0xC00E1118;
	public static final int PANEL_BORDER = 0x38FFFFFF;
	public static final int PANEL_SHINE = 0x14FFFFFF;
	/** Translucent white controls on the dark glass. */
	public static final int CONTROL = 0x1EFFFFFF;
	public static final int CONTROL_HOVER = 0x36FFFFFF;
	public static final int CONTROL_BORDER = 0x26FFFFFF;

	private Glass() {
	}

	public static int alpha(int rgb, int alpha) {
		return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
	}

	/** A filled rectangle with 1px rounded corners. */
	public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		if (w <= 2 || h <= 2) {
			g.fill(x, y, x + w, y + h, color);
			return;
		}

		g.fill(x + 1, y, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** A 1px outline with rounded corners. */
	public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
		g.fill(x + 1, y, x + w - 1, y + 1, color);
		g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		g.fill(x, y + 1, x + 1, y + h - 1, color);
		g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/** A glass panel: dark translucent body, light border and a faint shine along the top. */
	public static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		int w = x1 - x0;
		int h = y1 - y0;
		fill(g, x0, y0, w, h, PANEL);
		g.fillGradient(x0 + 1, y0 + 1, x1 - 1, y0 + Math.min(40, h - 2), PANEL_SHINE, 0x00FFFFFF);
		border(g, x0, y0, w, h, PANEL_BORDER);
	}
}
