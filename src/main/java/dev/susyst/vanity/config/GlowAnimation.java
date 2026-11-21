package dev.susyst.vanity.config;

import net.minecraft.util.Mth;

/** How armor glow and trim glow move over time. */
public enum GlowAnimation {
	STEADY("Steady glow"),
	/** Fades softly up and down. */
	BREATHE("Breathe"),
	/** Glows in shifting rainbow colors. */
	RAINBOW("Rainbow glow"),
	/** Flickers like a torch. */
	FLICKER("Flicker");

	public final String label;

	GlowAnimation(String label) {
		this.label = label;
	}

	/** How strong the glow is right now, 0 to 1, as a share of the glow slider. */
	public float strength(float speed, long millis) {
		double t = millis / 1000.0 * speed;

		return switch (this) {
			case STEADY, RAINBOW -> 1.0F;
			case BREATHE -> (float) (0.3 + 0.7 * (0.5 + 0.5 * Math.sin(Math.PI * 2 * t / 2.5)));
			case FLICKER -> {
				// A few out-of-step waves, with short dips now and then
				double n = 0.5 * Math.sin(t * 11.3) + 0.3 * Math.sin(t * 23.7 + 1.3) + 0.2 * Math.sin(t * 5.1 + 2.1);
				yield (float) Mth.clamp(0.75 + 0.35 * n, 0.25, 1.0);
			}
		};
	}

	/** RGB color to tint the glow with, or -1 to keep the armor's own colors. */
	public int tint(float speed, long millis) {
		if (this != RAINBOW) return -1;
		double t = millis / 1000.0 * speed;
		return Mth.hsvToRgb((float) ((t / 5.0) % 1.0), 0.8F, 1.0F) & 0xFFFFFF;
	}
}
