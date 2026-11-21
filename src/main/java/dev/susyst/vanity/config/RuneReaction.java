package dev.susyst.vanity.config;

/** What a rune reacts to: a burst when you get hit, and/or stronger while sprinting. */
public enum RuneReaction {
	OFF("Off", false, false),
	HIT("Hit", true, false),
	SPRINT("Sprint", false, true),
	BOTH("Both", true, true);

	public final String label;
	public final boolean hit;
	public final boolean sprint;

	RuneReaction(String label, boolean hit, boolean sprint) {
		this.label = label;
		this.hit = hit;
		this.sprint = sprint;
	}
}
