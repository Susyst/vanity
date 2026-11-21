package dev.susyst.vanity.config;

/** Enchantment shine on an armor look. */
public enum GlintMode {
	/** Shine if the armor you're really wearing is enchanted. */
	AUTO("Like real"),
	ON("On"),
	OFF("Off");

	public final String label;

	GlintMode(String label) {
		this.label = label;
	}

	public GlintMode next() {
		GlintMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
