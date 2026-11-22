package dev.susyst.vanity.config;

/** A trail of particles where your weapon swings (only you see it). */
public final class SwingTrail {
	public TrailStyle style = TrailStyle.NONE;
	public SwingShape shape = SwingShape.DIAGONAL;
	/** Color for the Color style. */
	public int color = 0x5BD2FF;
	/** 0.5 to 2: how wide the arc is and how big its particles are. */
	public float size = 1.0F;
	/** 0.25 to 3, how many particles. */
	public float amount = 1.0F;
	/** Only for swords, axes, tridents, maces and SkyBlock swords and daggers. */
	public boolean weaponsOnly = true;
	/** A soft whoosh with each trail. */
	public boolean sounds = false;

	void sanitize() {
		if (this.style == null) this.style = TrailStyle.NONE;
		if (this.shape == null) this.shape = SwingShape.DIAGONAL;
		this.color &= 0xFFFFFF;
		if (!Float.isFinite(this.size)) this.size = 1.0F;
		this.size = Math.max(0.5F, Math.min(2.0F, this.size));
		if (!Float.isFinite(this.amount)) this.amount = 1.0F;
		this.amount = Math.max(0.25F, Math.min(3.0F, this.amount));
	}
}
