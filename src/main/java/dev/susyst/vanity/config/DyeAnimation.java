package dev.susyst.vanity.config;

import net.minecraft.util.Mth;

/** How a dyed look's color moves over time. */
public enum DyeAnimation {
	/** The dye color, not moving. */
	SOLID("Solid"),
	/** Cycles through every color. */
	RAINBOW("Rainbow"),
	/** The dye color fading darker and back. */
	PULSE("Pulse"),
	/** The dye color's hue swaying a little either way. */
	SHIMMER("Shimmer");

	public final String label;

	DyeAnimation(String label) {
		this.label = label;
	}

	/** The color at {@code millis} for a base RGB color and a speed multiplier. */
	public int color(int base, float speed, long millis) {
		double t = millis / 1000.0 * speed;

		return switch (this) {
			case SOLID -> base & 0xFFFFFF;
			case RAINBOW -> Mth.hsvToRgb((float) ((t / 6.0) % 1.0), 0.75F, 0.95F) & 0xFFFFFF;
			case PULSE -> {
				double wave = 0.5 + 0.5 * Math.cos(Math.PI * 2 * t / 2.0);
				yield scale(base, (float) (0.4 + 0.6 * wave));
			}
			case SHIMMER -> {
				float[] hsv = toHsv(base);
				float hue = (float) (hsv[0] + 0.07 * Math.sin(Math.PI * 2 * t / 3.0));
				float value = (float) Math.min(1.0, hsv[2] * (0.9 + 0.15 * Math.sin(Math.PI * 2 * t / 1.5)));
				yield Mth.hsvToRgb(hue - (float) Math.floor(hue), Math.max(hsv[1], 0.35F), Math.max(value, 0.25F)) & 0xFFFFFF;
			}
		};
	}

	private static int scale(int rgb, float f) {
		int r = Math.round(((rgb >> 16) & 0xFF) * f);
		int g = Math.round(((rgb >> 8) & 0xFF) * f);
		int b = Math.round((rgb & 0xFF) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static float[] toHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255.0F;
		float g = ((rgb >> 8) & 0xFF) / 255.0F;
		float b = (rgb & 0xFF) / 255.0F;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;

		if (d == 0.0F) h = 0.0F;
		else if (max == r) h = ((g - b) / d) / 6.0F;
		else if (max == g) h = ((b - r) / d + 2.0F) / 6.0F;
		else h = ((r - g) / d + 4.0F) / 6.0F;

		if (h < 0.0F) h += 1.0F;
		return new float[] {h, max == 0.0F ? 0.0F : d / max, max};
	}
}
