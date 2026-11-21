package dev.susyst.vanity.config;

/** A particle effect worn on an armor piece, like a SkyBlock rune but bigger. */
public final class Rune {
	public boolean enabled;
	public RuneEffect effect = RuneEffect.DUST;
	/** Null means the slot's default shape. */
	public RuneShape shape;
	/** RGB color for dust. */
	public int color = 0x5BD2FF;
	/** Dust particle size, 0.5 to 3. */
	public float size = 1.2F;
	/** Particles per tick, 1 to 8. */
	public int amount = 2;
	/** How long each particle lives in ticks, so how long the trail behind the shape is. */
	public int length = 14;
	/** Animation speed. */
	public float speed = 1.0F;

	public RuneShape shapeFor(CosmeticSlot slot) {
		return this.shape != null ? this.shape : RuneShape.defaultFor(slot);
	}

	public Rune copy() {
		Rune copy = new Rune();
		copy.enabled = this.enabled;
		copy.effect = this.effect;
		copy.shape = this.shape;
		copy.color = this.color;
		copy.size = this.size;
		copy.amount = this.amount;
		copy.length = this.length;
		copy.speed = this.speed;
		return copy;
	}

	void sanitize() {
		if (this.effect == null) this.effect = RuneEffect.DUST;
		this.color &= 0xFFFFFF;
		if (!Float.isFinite(this.size)) this.size = 1.2F;
		this.size = Math.max(0.5F, Math.min(3.0F, this.size));
		this.amount = Math.max(1, Math.min(8, this.amount));
		if (this.length <= 0) this.length = 14;
		this.length = Math.max(4, Math.min(40, this.length));
		if (!Float.isFinite(this.speed)) this.speed = 1.0F;
		this.speed = Math.max(0.25F, Math.min(3.0F, this.speed));
	}
}
