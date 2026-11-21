package dev.susyst.vanity.config;

/** The shape a rune's particles make around you. */
public enum RuneShape {
	HALO("Halo"),
	ORBIT("Orbit rings"),
	SPIRAL("Spiral"),
	WINGS("Wings"),
	AURA("Aura"),
	TRAIL("Trail");

	public final String label;

	RuneShape(String label) {
		this.label = label;
	}

	public RuneShape step(int direction) {
		RuneShape[] values = values();
		return values[Math.floorMod(this.ordinal() + direction, values.length)];
	}

	/** Each armor piece has a natural spot: halo for the helmet, rings for the chest, spiral for legs, trail for boots. */
	public static RuneShape defaultFor(CosmeticSlot slot) {
		return switch (slot) {
			case HEAD -> HALO;
			case CHEST -> ORBIT;
			case LEGS -> SPIRAL;
			case FEET -> TRAIL;
		};
	}
}
