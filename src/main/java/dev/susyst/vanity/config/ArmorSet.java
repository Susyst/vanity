package dev.susyst.vanity.config;

/** Vanilla armor materials, for quickly making all four slots look like one armor set. */
public enum ArmorSet {
	LEATHER("Leather", "leather"),
	CHAINMAIL("Chainmail", "chainmail"),
	IRON("Iron", "iron"),
	GOLD("Gold", "golden"),
	COPPER("Copper", "copper"),
	DIAMOND("Diamond", "diamond"),
	NETHERITE("Netherite", "netherite");

	public final String label;
	private final String prefix;

	ArmorSet(String label, String prefix) {
		this.label = label;
		this.prefix = prefix;
	}

	/** e.g. "minecraft:netherite_chestplate". */
	public String itemId(CosmeticSlot slot) {
		return "minecraft:" + this.prefix + "_" + slot.armorPiece;
	}

	/** The set all four slots are currently showing, or null if they are empty or mixed. */
	public static ArmorSet wornBy(VanityConfig config) {
		for (ArmorSet set : values()) {
			boolean all = true;

			for (CosmeticSlot slot : CosmeticSlot.values()) {
				if (!set.itemId(slot).equals(config.get(slot).item)) {
					all = false;
					break;
				}
			}

			if (all) return set;
		}

		return null;
	}

	/** True if no slot has a cosmetic. */
	public static boolean noneWorn(VanityConfig config) {
		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (!config.get(slot).item.isBlank()) return false;
		}

		return true;
	}
}
