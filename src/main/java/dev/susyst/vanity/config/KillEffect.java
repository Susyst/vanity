package dev.susyst.vanity.config;

/** What happens where something you hit dies. */
public enum KillEffect {
	NONE("None"),
	SOUL_BURST("Soul burst"),
	LIGHTNING("Lightning"),
	FIREWORK("Firework"),
	BLOOD("Blood"),
	HEARTS("Hearts"),
	TOTEM("Totem"),
	EXPLOSION("Explosion"),
	RAINBOW_RING("Rainbow ring"),
	VOID("Void");

	public final String label;

	KillEffect(String label) {
		this.label = label;
	}

	/** Effects that use the color picker. */
	public boolean colored() {
		return this == FIREWORK;
	}
}
