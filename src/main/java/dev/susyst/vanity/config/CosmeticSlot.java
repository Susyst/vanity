package dev.susyst.vanity.config;

/** The four armor slots a cosmetic can be worn in. */
public enum CosmeticSlot {
	HEAD("Head"),
	CHEST("Chest"),
	LEGS("Legs"),
	FEET("Feet");

	public final String label;

	CosmeticSlot(String label) {
		this.label = label;
	}
}
