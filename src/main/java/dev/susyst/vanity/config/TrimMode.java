package dev.susyst.vanity.config;

/** Which armor trim a look shows. */
public enum TrimMode {
	/** The trim of the armor you're really wearing (if any). */
	REAL("Your armor's"),
	NONE("None"),
	/** A vanilla pattern and material picked in the wardrobe. */
	PICK("Pick");

	public final String label;

	TrimMode(String label) {
		this.label = label;
	}

	public TrimMode next() {
		TrimMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
