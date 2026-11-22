package dev.susyst.vanity.config;

/** Particles for swing trails and ability effects. */
public enum TrailStyle {
	NONE("None"),
	COLOR("Color"),
	SPARKLE("Sparkle"),
	FLAME("Flame"),
	SOUL("Soul fire"),
	FROST("Frost"),
	ELECTRIC("Electric"),
	MAGIC("Magic"),
	VOID("Void"),
	RAINBOW("Rainbow");

	public final String label;

	TrailStyle(String label) {
		this.label = label;
	}

	/** Styles that use the color picker. */
	public boolean colored() {
		return this == COLOR;
	}
}
