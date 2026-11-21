package dev.susyst.vanity.config;

/** How a cosmetic model is sized onto the armor shape of its slot. */
public enum FitMode {
	/** Stretch the model on each axis so it fills the armor box exactly. A glass block becomes a glass helmet. */
	STRETCH("Armor shape", "Stretches the model to fill the armor shape exactly. Best for blocks: glass becomes a see-through helmet, chestplate, leggings or boots."),
	/** Scale evenly until the model fits inside the armor box. Keeps the model's proportions. */
	CONTAIN("Fit inside", "Scales the model evenly until it fits inside the armor shape, keeping its proportions, and rests it on the front of the armor. Best for flat items like swords and for odd-shaped models."),
	/** Normal size, like a pumpkin on your head. */
	FREE("Vanilla size", "Shows the model at vanilla head-item size, centered on the body part, like a pumpkin on your head.");

	public final String label;
	public final String description;

	FitMode(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public FitMode next() {
		FitMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
