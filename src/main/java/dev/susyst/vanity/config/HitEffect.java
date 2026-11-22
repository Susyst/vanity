package dev.susyst.vanity.config;

/** Particles when you hit something. */
public enum HitEffect {
	NONE("None"),
	SPARKS("Sparks"),
	BLOOD("Blood"),
	HEARTS("Hearts"),
	FLAME("Flame"),
	SOUL("Soul fire"),
	RAINBOW("Rainbow"),
	LIGHTNING("Lightning"),
	MAGIC("Magic"),
	NOTES("Notes");

	public final String label;

	HitEffect(String label) {
		this.label = label;
	}

	/** Effects that use the color picker. */
	public boolean colored() {
		return this == SPARKS;
	}
}
