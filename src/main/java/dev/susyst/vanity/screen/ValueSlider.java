package dev.susyst.vanity.screen;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over a real-number range, snapped to a step. */
public final class ValueSlider extends AbstractSliderButton {
	private final String label;
	private final double min;
	private final double max;
	private final double step;
	private final String format;
	private final DoubleConsumer onChange;

	public ValueSlider(int x, int y, int width, int height, String label, double min, double max, double step, String format, double current, DoubleConsumer onChange) {
		super(x, y, width, height, Component.empty(), 0.0);
		this.label = label;
		this.min = min;
		this.max = max;
		this.step = step;
		this.format = format;
		this.onChange = onChange;
		this.setRealValue(current);
	}

	public double realValue() {
		double raw = this.min + this.value * (this.max - this.min);
		double snapped = Math.round(raw / this.step) * this.step;
		return Math.max(this.min, Math.min(this.max, snapped));
	}

	public void setRealValue(double real) {
		double clamped = Math.max(this.min, Math.min(this.max, real));
		this.value = (clamped - this.min) / (this.max - this.min);
		this.updateMessage();
	}

	@Override
	protected void updateMessage() {
		this.setMessage(Component.literal(this.label + ": " + String.format(Locale.ROOT, this.format, this.realValue())));
	}

	@Override
	protected void applyValue() {
		this.onChange.accept(this.realValue());
	}
}
