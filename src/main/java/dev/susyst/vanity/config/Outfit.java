package dev.susyst.vanity.config;

/** A saved set of all four slots' settings, to switch between with a key. */
public final class Outfit {
	public String name = "Outfit";
	public SlotSettings head = new SlotSettings();
	public SlotSettings chest = new SlotSettings();
	public SlotSettings legs = new SlotSettings();
	public SlotSettings feet = new SlotSettings();

	/** An outfit holding a copy of what the config shows right now. */
	public static Outfit capture(String name, VanityConfig config) {
		Outfit outfit = new Outfit();
		outfit.name = name;
		outfit.copyFrom(config);
		return outfit;
	}

	public void copyFrom(VanityConfig config) {
		this.head = config.head.copy();
		this.chest = config.chest.copy();
		this.legs = config.legs.copy();
		this.feet = config.feet.copy();
	}

	/** Puts a copy of this outfit's settings into the config. */
	public void wear(VanityConfig config) {
		config.head = this.head.copy();
		config.chest = this.chest.copy();
		config.legs = this.legs.copy();
		config.feet = this.feet.copy();
	}

	void sanitize() {
		if (this.name == null || this.name.isBlank()) this.name = "Outfit";
		if (this.name.length() > 32) this.name = this.name.substring(0, 32);
		if (this.head == null) this.head = new SlotSettings();
		if (this.chest == null) this.chest = new SlotSettings();
		if (this.legs == null) this.legs = new SlotSettings();
		if (this.feet == null) this.feet = new SlotSettings();
		this.head.sanitize();
		this.chest.sanitize();
		this.legs.sanitize();
		this.feet.sanitize();
	}
}
