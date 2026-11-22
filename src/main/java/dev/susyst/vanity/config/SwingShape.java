package dev.susyst.vanity.config;

/** Which way a swing trail sweeps across in front of you. */
public enum SwingShape {
	DIAGONAL("Diagonal"),
	FLAT("Flat"),
	OVERHEAD("Overhead"),
	/** Diagonal, flipping direction every swing. */
	COMBO("Combo");

	public final String label;

	SwingShape(String label) {
		this.label = label;
	}
}
