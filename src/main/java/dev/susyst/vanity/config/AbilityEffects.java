package dev.susyst.vanity.config;

/**
 * SkyBlock item ability effects (only you see them): a trail along teleports (Aspect of the End / Void, etherwarp,
 * Hyperion's Wither Impact) and a ring when you use any other ability. An ability counts as used when its mana cost
 * shows above your hotbar ("-50 Mana (Instant Transmission)").
 */
public final class AbilityEffects {
	public TrailStyle style = TrailStyle.NONE;
	/** Color for the Color style. */
	public int color = 0xB26BFF;
	/** 0.5 to 2. */
	public float size = 1.0F;
	/** 0.25 to 3, how many particles. */
	public float amount = 1.0F;
	/** A trail from where you were to where you teleported. */
	public boolean teleports = true;
	/** A ring around you for every other ability. */
	public boolean others = true;
	public boolean sounds = false;

	void sanitize() {
		if (this.style == null) this.style = TrailStyle.NONE;
		this.color &= 0xFFFFFF;
		if (!Float.isFinite(this.size)) this.size = 1.0F;
		this.size = Math.max(0.5F, Math.min(2.0F, this.size));
		if (!Float.isFinite(this.amount)) this.amount = 1.0F;
		this.amount = Math.max(0.25F, Math.min(3.0F, this.amount));
	}
}
