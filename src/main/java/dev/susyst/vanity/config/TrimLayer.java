package dev.susyst.vanity.config;

/** One layer of a custom trim design: a trim pattern drawn in any color. */
public final class TrimLayer {
	public boolean enabled;
	/** Trim pattern id, e.g. "minecraft:sentry". */
	public String pattern = "minecraft:sentry";
	/** RGB color. */
	public int color = 0xFFD54F;

	public TrimLayer copy() {
		TrimLayer copy = new TrimLayer();
		copy.enabled = this.enabled;
		copy.pattern = this.pattern;
		copy.color = this.color;
		return copy;
	}

	void sanitize() {
		if (this.pattern == null || this.pattern.isBlank()) this.pattern = "minecraft:sentry";
		this.color &= 0xFFFFFF;
	}
}
