package dev.susyst.vanity.screen;

import java.util.Locale;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider over a real-number range, snapped to a step, drawn as a slim glass bar with an accent fill. */
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
	public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		int x = this.getX();
		int y = this.getY();
		int w = this.getWidth();
		int h = this.getHeight();
		boolean hot = this.active && this.isHoveredOrFocused();

		Glass.fill(g, x, y, w, h, hot ? Glass.CONTROL_HOVER : Glass.CONTROL);
		int filled = (int) Math.round(this.value * (w - 2));

		if (this.active && filled > 0) {
			Glass.fill(g, x + 1, y + 1, filled, h - 2, Glass.alpha(Glass.ACCENT, hot ? 0x78 : 0x55));
		}

		int knob = x + 1 + Math.max(0, Math.min(w - 4, filled - 1));
		g.fill(knob, y + 3, knob + 2, y + h - 3, this.active ? Glass.TEXT : Glass.TEXT_OFF);
		Glass.border(g, x, y, w, h, hot ? Glass.alpha(Glass.ACCENT, 0xB0) : Glass.CONTROL_BORDER);
		g.centeredText(Minecraft.getInstance().font, this.getMessage(), x + w / 2, y + (h - 8) / 2, this.active ? Glass.TEXT : Glass.TEXT_OFF);
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
