package dev.susyst.vanity.config;

/** What a rune is made of. */
public enum RuneEffect {
	DUST("Dust", true),
	RAINBOW("Rainbow", false),
	FLAME("Flame", false),
	SOUL_FLAME("Soul fire", false),
	COPPER_FLAME("Copper fire", false),
	ENCHANT("Glyphs", false),
	SPARKLE("Sparkle", false),
	LIGHTNING("Lightning", false),
	HEARTS("Hearts", false),
	MUSIC("Music", false),
	SNOW("Snow", false),
	PETALS("Petals", false),
	VOID("Void", false),
	TOTEM("Totem", false),
	GLOW("Glow", false),
	SOULS("Souls", false),
	FIREFLIES("Fireflies", false),
	SHINE("Shine", false);

	public final String label;
	/** Uses the rune's color and size (dust). Other effects use their own look. */
	public final boolean colorable;

	RuneEffect(String label, boolean colorable) {
		this.label = label;
		this.colorable = colorable;
	}

	public RuneEffect step(int direction) {
		RuneEffect[] values = values();
		return values[Math.floorMod(this.ordinal() + direction, values.length)];
	}
}
