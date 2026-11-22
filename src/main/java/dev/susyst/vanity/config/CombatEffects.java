package dev.susyst.vanity.config;

/** Hit and kill effects (only you see them). */
public final class CombatEffects {
	public HitEffect hit = HitEffect.NONE;
	public KillEffect kill = KillEffect.NONE;
	/** Color for Sparks and Firework. */
	public int color = 0xFF3355;
	/** 0.5 to 2. */
	public float size = 1.0F;
	/** 0.25 to 3, how many particles. */
	public float amount = 1.0F;
	public boolean sounds = true;
	/** Also when you hit or kill players. */
	public boolean onPlayers = true;

	void sanitize() {
		if (this.hit == null) this.hit = HitEffect.NONE;
		if (this.kill == null) this.kill = KillEffect.NONE;
		this.color &= 0xFFFFFF;
		if (!Float.isFinite(this.size)) this.size = 1.0F;
		this.size = Math.max(0.5F, Math.min(2.0F, this.size));
		if (!Float.isFinite(this.amount)) this.amount = 1.0F;
		this.amount = Math.max(0.25F, Math.min(3.0F, this.amount));
	}
}
