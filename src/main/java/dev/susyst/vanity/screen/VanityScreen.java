package dev.susyst.vanity.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.ArmorSet;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.Rune;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.TrimMode;
import dev.susyst.vanity.config.Trims;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.VanityCosmetics;

/**
 * The wardrobe, in a frosted-glass style. Left: live preview. Right: pick a slot, then a page:
 * Look (what it looks like), Style (glint, dye), Trim (vanilla trims), Design (custom trims), Fit (non-armor items).
 */
public final class VanityScreen extends Screen {
	private static final int ROW = 20;
	private static final int GAP = 4;
	private static final int GRAY = Glass.TEXT_DIM;
	private static final int GREEN = 0xFF7CF5B0;
	private static final int RED = 0xFFFF7A7A;
	/** The 16 dye colors, for the quick color rows. */
	private static final List<Integer> DYE_COLORS = Trims.PALETTE.subList(0, 16);

	public enum Page {
		LOOK("Look"),
		STYLE("Style"),
		TRIM("Trim"),
		DESIGN("Design"),
		RUNE("Rune"),
		FIT("Fit");

		final String label;

		Page(String label) {
			this.label = label;
		}
	}

	private CosmeticSlot selected;
	private Page page;
	private int designLayer;
	private float previewYaw;

	private final List<GlassButton> slotTabs = new ArrayList<>();
	private final Map<Page, GlassButton> pageTabs = new EnumMap<>(Page.class);
	private final Map<Page, List<AbstractWidget>> pageWidgets = new EnumMap<>(Page.class);
	/** Actions run by {@link #refresh()} to show the current settings on the widgets. */
	private final List<Runnable> syncers = new ArrayList<>();

	private EditBox itemBox;
	private boolean syncing;
	private boolean initialized;
	private int previewRight;
	private int panelX;
	private int panelW;
	private int contentY;
	/** Where each page's hint text goes: just below its last row. */
	private final Map<Page, Integer> hintY = new EnumMap<>(Page.class);

	public VanityScreen() {
		this(CosmeticSlot.HEAD, Page.LOOK);
	}

	public VanityScreen(CosmeticSlot slot) {
		this(slot, Page.LOOK);
	}

	public VanityScreen(CosmeticSlot slot, Page page) {
		super(Component.translatable("screen.vanity.title"));
		this.selected = slot;
		this.page = page;
	}

	private static VanityConfig config() {
		return VanityClient.config();
	}

	private SlotSettings current() {
		return config().get(this.selected);
	}

	private TrimLayer layer() {
		return this.current().design[this.designLayer];
	}

	private static Tooltip tip(String text) {
		return Tooltip.create(Component.literal(text));
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}

	private static MutableComponent swatch(int rgb) {
		return Component.literal("██").withColor(rgb & 0xFFFFFF);
	}

	// ---- widget helpers ----

	private <T extends AbstractWidget> T on(Page page, T widget) {
		this.addRenderableWidget(widget);
		this.pageWidgets.computeIfAbsent(page, p -> new ArrayList<>()).add(widget);
		return widget;
	}

	/** A button that runs the action, then refreshes the screen. Page null means always visible. */
	private GlassButton button(Page page, int x, int y, int w, String label, Runnable action) {
		return this.button(page, x, y, w, label, GlassButton.Style.NORMAL, action);
	}

	private GlassButton button(Page page, int x, int y, int w, String label, GlassButton.Style style, Runnable action) {
		GlassButton button = new GlassButton(x, y, w, ROW, Component.literal(label), b -> {
			action.run();
			this.refresh();
		}, style);
		return page == null ? this.addRenderableWidget(button) : this.on(page, button);
	}

	private void sync(Runnable syncer) {
		this.syncers.add(syncer);
	}

	/** A row of dye-color swatches; the current color is outlined. */
	private void colorRow(Page page, int y, IntSupplier current, IntConsumer onPick) {
		int count = DYE_COLORS.size();
		int w = Math.max(8, (this.panelW - (count - 1) * 2) / count);

		for (int i = 0; i < count; i++) {
			int color = DYE_COLORS.get(i);
			GlassButton swatchButton = this.button(page, this.panelX + i * (w + 2), y, w, "", GlassButton.Style.SWATCH, () -> onPick.accept(color));
			swatchButton.swatchColor = color;
			swatchButton.setTooltip(tip(String.format(Locale.ROOT, "#%06X", color)));
			this.sync(() -> swatchButton.selected = (current.getAsInt() & 0xFFFFFF) == color);
		}
	}

	/** Red, green and blue sliders all editing one color. */
	private void colorSliders(Page page, int y, int thirdW, IntSupplier get, IntConsumer set) {
		String[] names = {"Red", "Green", "Blue"};
		int[] shifts = {16, 8, 0};

		for (int c = 0; c < 3; c++) {
			int shift = shifts[c];
			ValueSlider slider = this.on(page, new ValueSlider(this.panelX + c * (thirdW + GAP), y, thirdW, ROW, names[c], 0, 255, 1, "%.0f", 0, v -> {
				if (this.syncing) return;
				int rgb = get.getAsInt();
				int channel = (int) Math.round(v) & 0xFF;
				set.accept((rgb & ~(0xFF << shift)) | (channel << shift));
				this.refresh();
			}));
			this.sync(() -> slider.setRealValue((get.getAsInt() >> shift) & 0xFF));
		}
	}

	/** A "#RRGGBB" box editing one color. */
	private void hexBox(Page page, int x, int y, int w, IntSupplier get, IntConsumer set) {
		EditBox box = this.on(page, new GlassEditBox(this.font, x, y, w, ROW, Component.literal("Hex color")));
		box.setMaxLength(7);
		box.setHint(Component.literal("#RRGGBB"));
		box.setResponder(text -> {
			if (this.syncing) return;
			String hex = text.startsWith("#") ? text.substring(1) : text;

			if (hex.matches("[0-9a-fA-F]{6}")) {
				set.accept(Integer.parseInt(hex, 16));
				this.refresh();
			}
		});
		this.sync(() -> {
			if (!box.isFocused()) box.setValue(String.format(Locale.ROOT, "#%06X", get.getAsInt() & 0xFFFFFF));
		});
	}

	private void applyToAll(Consumer<SlotSettings> copy) {
		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (slot != this.selected) copy.accept(config().get(slot));
		}
	}

	private interface FloatGetter {
		float get(SlotSettings settings);
	}

	private interface FloatSetter {
		void set(SlotSettings settings, float value);
	}

	private void fitSlider(int x, int y, int w, String label, double min, double max, double step, String format, FloatGetter get, FloatSetter set) {
		this.slider(Page.FIT, x, y, w, label, min, max, step, format, get, set);
	}

	private void slider(Page page, int x, int y, int w, String label, double min, double max, double step, String format, FloatGetter get, FloatSetter set) {
		ValueSlider slider = this.on(page, new ValueSlider(x, y, w, ROW, label, min, max, step, format, 0, v -> {
			if (this.syncing) return;
			set.set(this.current(), (float) v);
			this.refresh();
		}));
		this.sync(() -> slider.setRealValue(get.get(this.current())));
	}

	private Rune rune() {
		return this.current().rune;
	}

	// ---- layout ----

	@Override
	protected void init() {
		this.initialized = false;
		this.slotTabs.clear();
		this.pageTabs.clear();
		this.pageWidgets.clear();
		this.syncers.clear();

		this.previewRight = Math.max(110, (int) (this.width * 0.36F));
		this.panelX = this.previewRight + 14;
		this.panelW = Math.max(200, Math.min(this.width - this.panelX - 16, 300));
		int halfW = (this.panelW - GAP) / 2;
		int thirdW = (this.panelW - GAP * 2) / 3;
		int x2 = this.panelX + thirdW + GAP;
		int x3 = this.panelX + 2 * (thirdW + GAP);
		int arrowW = 20;
		int labelW = this.panelW - 2 * (arrowW + GAP);
		int step = ROW + GAP;

		// Slot tabs (segmented control)
		int y = 32;
		CosmeticSlot[] slots = CosmeticSlot.values();
		int tabW = (this.panelW - 2 * 3) / 4;

		for (int i = 0; i < slots.length; i++) {
			CosmeticSlot slot = slots[i];
			this.slotTabs.add(this.button(null, this.panelX + i * (tabW + 2), y, tabW, slot.label, GlassButton.Style.SEGMENT, () -> this.select(slot)));
		}

		// Page tabs (underlined text tabs)
		y += step;
		Page[] pages = Page.values();
		int pageW = this.panelW / pages.length;

		for (int i = 0; i < pages.length; i++) {
			Page p = pages[i];
			this.pageTabs.put(p, this.button(null, this.panelX + i * pageW, y, pageW, p.label, GlassButton.Style.TAB, () -> this.page = p));
		}

		this.contentY = y + ROW + 10;
		int r0 = this.contentY;
		int r1 = r0 + step;
		int r2 = r1 + step;
		int r3 = r2 + step;
		int r4 = r3 + step;
		this.hintY.put(Page.LOOK, r4 + 2);
		this.hintY.put(Page.TRIM, r4 + 2);
		this.hintY.put(Page.DESIGN, r4 + step + 2);
		this.hintY.put(Page.RUNE, r4 + step + 2);
		this.hintY.put(Page.STYLE, r4 + step + 2);
		this.hintY.put(Page.FIT, r4 + 2);

		// ---- Look ----
		Button set = this.button(Page.LOOK, this.panelX, r0, this.panelW, "", this::cycleArmorSet);
		set.setTooltip(tip("Change how all four slots look at once. Your real armor stays the same; only the look changes."));
		this.sync(() -> {
			ArmorSet armorSet = ArmorSet.wornBy(config());
			String label = armorSet != null ? armorSet.label : ArmorSet.noneWorn(config()) ? "Real armor" : "Custom";
			set.setMessage(Component.literal("All slots: " + label));
		});

		int smallW = 36;
		int boxW = this.panelW - 3 * (smallW + GAP);
		this.itemBox = this.on(Page.LOOK, new GlassEditBox(this.font, this.panelX, r1, boxW, ROW, Component.literal("Item ID")));
		this.itemBox.setMaxLength(128);
		this.itemBox.setHint(Component.literal("netherite_chestplate"));
		this.itemBox.setResponder(value -> {
			if (this.syncing) return;
			this.current().setLook(value.trim());
			this.refresh();
		});
		this.button(Page.LOOK, this.panelX + boxW + GAP, r1, smallW, "Held", this::useHeldItem)
				.setTooltip(tip("Use the exact item in your main hand, including its name, dye, head skin and SkyBlock data"));
		this.button(Page.LOOK, this.panelX + boxW + GAP + (smallW + GAP), r1, smallW, "Worn", this::useWornItem)
				.setTooltip(tip("Save the look of what you're wearing in this slot now (e.g. a SkyBlock piece), then wear anything else"));
		this.button(Page.LOOK, this.panelX + boxW + GAP + 2 * (smallW + GAP), r1, smallW, "Clear", () -> {
			this.current().clearLook();
			this.syncItemBox();
		}).setTooltip(tip("Go back to your real armor in this slot"));

		// r2 holds the status line, toggles go below it
		Button invisible = this.button(Page.LOOK, this.panelX, r3, halfW, "", () -> this.current().invisible = !this.current().invisible);
		invisible.setTooltip(tip("Show nothing in this slot, not even your real armor"));
		this.sync(() -> invisible.setMessage(Component.literal("Invisible: " + onOff(this.current().invisible))));

		Button wornOnly = this.button(Page.LOOK, this.panelX + halfW + GAP, r3, halfW, "", () -> this.current().onlyWhenWorn = !this.current().onlyWhenWorn);
		wornOnly.setTooltip(tip("Hide: the look disappears when you take the armor off. Show: it stays with nothing equipped."));
		this.sync(() -> wornOnly.setMessage(Component.literal("No armor: " + (this.current().onlyWhenWorn ? "Hide" : "Show"))));

		// ---- Style ----
		Button glint = this.button(Page.STYLE, this.panelX, r0, halfW, "", () -> this.current().glint = this.current().glint.next());
		glint.setTooltip(tip("Enchantment shine. Like real: shine if the armor you're wearing is enchanted."));
		this.sync(() -> glint.setMessage(Component.literal("Glint: " + this.current().glint.label)));

		Button dye = this.button(Page.STYLE, this.panelX + halfW + GAP, r0, halfW, "", () -> this.current().customDye = !this.current().customDye);
		dye.setTooltip(tip("Recolor leather armor with a color below"));
		this.sync(() -> {
			MutableComponent label = Component.literal("Dye: " + onOff(this.current().customDye));
			if (this.current().customDye) label.append(" ").append(swatch(this.current().dyeColor));
			dye.setMessage(label);
		});

		IntConsumer setDye = color -> {
			this.current().dyeColor = color;
			this.current().customDye = true;
		};
		this.colorRow(Page.STYLE, r1, () -> this.current().customDye ? this.current().dyeColor : -1, setDye);
		this.colorSliders(Page.STYLE, r2, thirdW, () -> this.current().dyeColor, setDye);
		this.hexBox(Page.STYLE, this.panelX, r3, thirdW, () -> this.current().dyeColor, setDye);
		this.button(Page.STYLE, x2, r3, thirdW, "Reset", () -> this.current().resetStyle());
		this.button(Page.STYLE, x3, r3, thirdW, "All slots", () -> this.applyToAll(s -> s.copyStyleFrom(this.current())))
				.setTooltip(tip("Copy this slot's glint, dye, opacity and glow to the other three slots"));

		this.slider(Page.STYLE, this.panelX, r4, thirdW, "Opacity", 10, 100, 5, "%.0f%%", s -> s.opacity * 100.0F, (s, v) -> s.opacity = v / 100.0F);
		this.slider(Page.STYLE, x2, r4, thirdW, "Glow", 0, 100, 5, "%.0f%%", s -> s.glow * 100.0F, (s, v) -> s.glow = v / 100.0F);
		GlassButton trimGlow = this.button(Page.STYLE, x3, r4, thirdW, "", () -> this.current().trimGlow = !this.current().trimGlow);
		trimGlow.setTooltip(tip("Make this slot's trim and custom design glow in the dark"));
		this.sync(() -> trimGlow.setMessage(Component.literal("Trim glow: " + onOff(this.current().trimGlow))));

		// ---- Trim ----
		Button trimMode = this.button(Page.TRIM, this.panelX, r0, this.panelW, "", () -> this.current().trim = this.current().trim.next());
		trimMode.setTooltip(tip("Your armor's: keep the trim of what you're wearing. None: no trim. Pick: choose a pattern and material below."));
		this.sync(() -> trimMode.setMessage(Component.literal("Trim: " + this.current().trim.label)));

		this.button(Page.TRIM, this.panelX, r1, arrowW, "<", () -> this.pickTrim(Trims.step(Trims.PATTERNS, this.current().trimPattern, -1), null));
		Button pattern = this.button(Page.TRIM, this.panelX + arrowW + GAP, r1, labelW, "", () -> this.pickTrim(Trims.step(Trims.PATTERNS, this.current().trimPattern, 1), null));
		this.button(Page.TRIM, this.panelX + this.panelW - arrowW, r1, arrowW, ">", () -> this.pickTrim(Trims.step(Trims.PATTERNS, this.current().trimPattern, 1), null));
		this.sync(() -> pattern.setMessage(Component.literal("Pattern: " + Trims.displayName(this.current().trimPattern))));

		this.button(Page.TRIM, this.panelX, r2, arrowW, "<", () -> this.pickTrim(null, Trims.step(Trims.MATERIALS, this.current().trimMaterial, -1)));
		Button material = this.button(Page.TRIM, this.panelX + arrowW + GAP, r2, labelW, "", () -> this.pickTrim(null, Trims.step(Trims.MATERIALS, this.current().trimMaterial, 1)));
		this.button(Page.TRIM, this.panelX + this.panelW - arrowW, r2, arrowW, ">", () -> this.pickTrim(null, Trims.step(Trims.MATERIALS, this.current().trimMaterial, 1)));
		this.sync(() -> material.setMessage(Component.literal("Material: " + Trims.displayName(this.current().trimMaterial))));

		this.button(Page.TRIM, this.panelX, r3, halfW, "Reset", () -> this.current().resetTrim());
		this.button(Page.TRIM, this.panelX + halfW + GAP, r3, halfW, "All slots", () -> this.applyToAll(s -> s.copyTrimFrom(this.current())))
				.setTooltip(tip("Copy this slot's trim to the other three slots"));

		// ---- Design ----
		int layerW = (this.panelW - GAP * 3) / 4;

		for (int k = 0; k < SlotSettings.DESIGN_LAYERS; k++) {
			int index = k;
			GlassButton tab = this.button(Page.DESIGN, this.panelX + k * (layerW + GAP), r0, layerW, "", GlassButton.Style.SEGMENT, () -> this.designLayer = index);
			this.sync(() -> {
				TrimLayer l = this.current().design[index];
				MutableComponent label = Component.literal("Layer " + (index + 1));
				if (l.enabled) label.append(" ").append(Component.literal("\u2588").withColor(l.color & 0xFFFFFF));
				tab.setMessage(label);
				tab.selected = this.designLayer == index;
			});
		}

		Button layerOn = this.button(Page.DESIGN, this.panelX + 3 * (layerW + GAP), r0, layerW, "", () -> this.layer().enabled = !this.layer().enabled);
		layerOn.setTooltip(tip("Show or hide this layer"));
		this.sync(() -> layerOn.setMessage(Component.literal(this.layer().enabled ? "Shown" : "Hidden")));

		this.button(Page.DESIGN, this.panelX, r1, arrowW, "<", () -> this.pickLayerPattern(-1));
		Button layerPattern = this.button(Page.DESIGN, this.panelX + arrowW + GAP, r1, labelW, "", () -> this.pickLayerPattern(1));
		this.button(Page.DESIGN, this.panelX + this.panelW - arrowW, r1, arrowW, ">", () -> this.pickLayerPattern(1));
		this.sync(() -> layerPattern.setMessage(Component.literal("Pattern: " + Trims.displayName(this.layer().pattern))));

		IntConsumer setLayerColor = color -> {
			this.layer().color = color;
			this.layer().enabled = true;
		};
		this.colorRow(Page.DESIGN, r2, () -> this.layer().color, setLayerColor);
		this.colorSliders(Page.DESIGN, r3, thirdW, () -> this.layer().color, setLayerColor);
		this.hexBox(Page.DESIGN, this.panelX, r4, thirdW, () -> this.layer().color, setLayerColor);
		this.button(Page.DESIGN, x2, r4, thirdW, "Clear", () -> this.current().resetDesign());
		this.button(Page.DESIGN, x3, r4, thirdW, "All slots", () -> this.applyToAll(s -> s.copyDesignFrom(this.current())))
				.setTooltip(tip("Copy this slot's design to the other three slots"));

		// ---- Rune ----
		int runeOnW = 64;
		int effectLabelW = this.panelW - runeOnW - 2 * arrowW - 3 * GAP;
		GlassButton runeOn = this.button(Page.RUNE, this.panelX, r0, runeOnW, "", GlassButton.Style.SEGMENT, () -> this.rune().enabled = !this.rune().enabled);
		this.sync(() -> {
			runeOn.setMessage(Component.literal(this.rune().enabled ? "Rune ON" : "Rune OFF"));
			runeOn.selected = this.rune().enabled;
		});
		int effectX = this.panelX + runeOnW + GAP;
		this.button(Page.RUNE, effectX, r0, arrowW, "<", () -> this.pickRuneEffect(-1));
		GlassButton effect = this.button(Page.RUNE, effectX + arrowW + GAP, r0, effectLabelW, "", () -> this.pickRuneEffect(1));
		this.button(Page.RUNE, this.panelX + this.panelW - arrowW, r0, arrowW, ">", () -> this.pickRuneEffect(1));
		this.sync(() -> effect.setMessage(Component.literal(this.rune().effect.label)));

		this.button(Page.RUNE, this.panelX, r1, arrowW, "<", () -> this.pickRuneShape(-1));
		GlassButton shape = this.button(Page.RUNE, this.panelX + arrowW + GAP, r1, labelW, "", () -> this.pickRuneShape(1));
		this.button(Page.RUNE, this.panelX + this.panelW - arrowW, r1, arrowW, ">", () -> this.pickRuneShape(1));
		this.sync(() -> shape.setMessage(Component.literal("Shape: " + this.rune().shapeFor(this.selected).label)));

		IntConsumer setRuneColor = color -> {
			Rune rune = this.rune();
			rune.color = color;
			rune.enabled = true;
			if (!rune.effect.colorable) rune.effect = dev.susyst.vanity.config.RuneEffect.DUST;
		};
		this.colorRow(Page.RUNE, r2, () -> this.rune().effect.colorable ? this.rune().color : -1, setRuneColor);
		this.slider(Page.RUNE, this.panelX, r3, thirdW, "Size", 0.5, 4.0, 0.25, "%.2f", s -> s.rune.size, (s, v) -> s.rune.size = v);
		this.slider(Page.RUNE, x2, r3, thirdW, "Amount", 1, 12, 1, "%.0f", s -> s.rune.amount, (s, v) -> s.rune.amount = Math.round(v));
		this.slider(Page.RUNE, x3, r3, thirdW, "Speed", 0.25, 3.0, 0.25, "%.2f", s -> s.rune.speed, (s, v) -> s.rune.speed = v);
		this.hexBox(Page.RUNE, this.panelX, r4, thirdW, () -> this.rune().color, setRuneColor);
		this.button(Page.RUNE, x2, r4, thirdW, "Reset", () -> {
			boolean wasOn = this.rune().enabled;
			this.current().rune = new Rune();
			this.current().rune.enabled = wasOn;
		});
		GlassButton firstPerson = this.button(Page.RUNE, x3, r4, thirdW, "", () -> config().runesInFirstPerson = !config().runesInFirstPerson);
		firstPerson.setTooltip(tip("Also show runes in first person (they can get in your way)"));
		this.sync(() -> firstPerson.setMessage(Component.literal("1st person: " + onOff(config().runesInFirstPerson))));

		// ---- Fit (non-armor items) ----
		Button mode = this.button(Page.FIT, this.panelX, r0, thirdW, "", () -> this.current().mode = this.current().mode.next());
		this.sync(() -> {
			mode.setMessage(Component.literal("Fit: " + this.current().mode.label));
			mode.setTooltip(tip(this.current().mode.description));
		});
		Button hide = this.button(Page.FIT, x2, r0, thirdW, "", () -> this.current().hideArmor = !this.current().hideArmor);
		hide.setTooltip(tip("Hide your real armor in this slot while the item is shown"));
		this.sync(() -> hide.setMessage(Component.literal("Hide armor: " + onOff(this.current().hideArmor))));
		this.button(Page.FIT, x3, r0, thirdW, "Reset", () -> this.current().resetTransform());

		this.fitSlider(this.panelX, r1, thirdW, "Move X", -16, 16, 0.5, "%.1f", s -> s.offsetX, (s, v) -> s.offsetX = v);
		this.fitSlider(x2, r1, thirdW, "Move Y", -16, 16, 0.5, "%.1f", s -> s.offsetY, (s, v) -> s.offsetY = v);
		this.fitSlider(x3, r1, thirdW, "Move Z", -16, 16, 0.5, "%.1f", s -> s.offsetZ, (s, v) -> s.offsetZ = v);
		this.fitSlider(this.panelX, r2, thirdW, "Turn X", -180, 180, 5, "%.0f", s -> s.rotX, (s, v) -> s.rotX = v);
		this.fitSlider(x2, r2, thirdW, "Turn Y", -180, 180, 5, "%.0f", s -> s.rotY, (s, v) -> s.rotY = v);
		this.fitSlider(x3, r2, thirdW, "Turn Z", -180, 180, 5, "%.0f", s -> s.rotZ, (s, v) -> s.rotZ = v);
		this.fitSlider(this.panelX, r3, this.panelW, "Size", 0.25, 3.0, 0.05, "%.2f", s -> s.scale, (s, v) -> s.scale = v);

		// ---- Bottom row ----
		int bottomY = this.height - 36;
		this.addRenderableWidget(new ValueSlider(18, bottomY, this.previewRight - 32, ROW, "Turn view", 0, 360, 5, "%.0f", this.previewYaw, v -> this.previewYaw = (float) v));

		Button enabled = this.button(null, this.panelX, bottomY, thirdW, "", () -> config().enabled = !config().enabled);
		enabled.setTooltip(tip("Turn all Vanity looks on or off"));
		this.sync(() -> enabled.setMessage(Component.literal("Vanity: " + onOff(config().enabled))));

		Button icons = this.button(null, x2, bottomY, thirdW, "", () -> config().showInInventory = !config().showInInventory);
		icons.setTooltip(tip("Also show the looks on your worn armor's icons in the inventory"));
		this.sync(() -> icons.setMessage(Component.literal("Inv icons: " + onOff(config().showInInventory))));

		this.button(null, x3, bottomY, thirdW, "Done", GlassButton.Style.PRIMARY, this::onClose);

		this.syncItemBox();
		this.initialized = true;
		this.refresh();
	}

	// ---- actions ----

	private void pickTrim(String patternId, String materialId) {
		SlotSettings s = this.current();
		if (patternId != null) s.trimPattern = patternId;
		if (materialId != null) s.trimMaterial = materialId;
		s.trim = TrimMode.PICK;
	}

	private void pickRuneEffect(int direction) {
		this.rune().effect = this.rune().effect.step(direction);
		this.rune().enabled = true;
	}

	private void pickRuneShape(int direction) {
		this.rune().shape = this.rune().shapeFor(this.selected).step(direction);
		this.rune().enabled = true;
	}

	private void pickLayerPattern(int direction) {
		this.layer().pattern = Trims.step(Trims.PATTERNS, this.layer().pattern, direction);
		this.layer().enabled = true;
	}

	private void select(CosmeticSlot slot) {
		this.selected = slot;
		this.syncItemBox();
		this.refresh();
	}

	/** Real armor, then each armor material in turn, applied to all four slots. */
	private void cycleArmorSet() {
		ArmorSet current = ArmorSet.wornBy(config());
		ArmorSet[] sets = ArmorSet.values();
		ArmorSet next;

		if (current == null) {
			next = ArmorSet.noneWorn(config()) ? sets[0] : null;
		} else {
			next = current.ordinal() + 1 < sets.length ? sets[current.ordinal() + 1] : null;
		}

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			config().get(slot).setLook(next == null ? "" : next.itemId(slot));
		}

		this.syncItemBox();
	}

	private void useHeldItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.current().setLook(player.getMainHandItem(), player.registryAccess());
		this.syncItemBox();
	}

	private void useWornItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.current().setLook(player.getItemBySlot(this.selected.equipmentSlot), player.registryAccess());
		this.syncItemBox();
	}

	/** Shows the selected slot's item id in the box without treating it as typing. */
	private void syncItemBox() {
		this.syncing = true;
		this.itemBox.setValue(this.current().item);
		this.itemBox.moveCursorToStart(false);
		this.syncing = false;
	}

	/** Non-armor items are fitted to the armor shape and use the Fit page instead of Style and Trim. */
	private boolean fitted() {
		ItemStack stack = this.current().stack();
		return !stack.isEmpty() && !VanityCosmetics.usesVanillaLook(this.selected, stack);
	}

	private boolean available(Page p) {
		boolean fitted = this.fitted();
		return switch (p) {
			case LOOK, DESIGN, RUNE -> true;
			case STYLE, TRIM -> !fitted;
			case FIT -> fitted;
		};
	}

	/** Syncs every widget with the selected slot and page. */
	private void refresh() {
		if (!this.initialized) return;
		this.syncing = true;

		if (!this.available(this.page)) this.page = Page.LOOK;

		CosmeticSlot[] slots = CosmeticSlot.values();

		for (int i = 0; i < this.slotTabs.size(); i++) {
			this.slotTabs.get(i).selected = slots[i] == this.selected;
		}

		for (Map.Entry<Page, GlassButton> entry : this.pageTabs.entrySet()) {
			entry.getValue().selected = entry.getKey() == this.page;
			entry.getValue().active = this.available(entry.getKey());
		}

		for (Map.Entry<Page, List<AbstractWidget>> entry : this.pageWidgets.entrySet()) {
			boolean visible = entry.getKey() == this.page;
			for (AbstractWidget widget : entry.getValue()) widget.visible = visible;
		}

		for (Runnable syncer : this.syncers) syncer.run();

		this.syncing = false;
	}

	/** Blur the world behind the wardrobe and darken it slightly. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.extractBlurredBackground(graphics);
		graphics.fillGradient(0, 0, this.width, this.height, 0x40050812, 0x90050812);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		// Glass panels: preview on the left, controls on the right
		Glass.panel(graphics, 8, 8, this.previewRight, this.height - 8);
		Glass.panel(graphics, this.panelX - 8, 8, this.panelX + this.panelW + 8, this.height - 8);
		int x0 = 12;
		int y0 = 12;
		int x1 = this.previewRight - 4;
		int y1 = this.height - 42;

		// Header
		graphics.text(this.font, Component.literal("VANITY").withStyle(ChatFormatting.BOLD), this.panelX, 16, Glass.ACCENT);
		graphics.text(this.font, Component.literal("wardrobe"), this.panelX + this.font.width("VANITY") + 8, 16, Glass.TEXT_DIM);
		// Line under the page tabs
		graphics.fill(this.panelX, this.contentY - 6, this.panelX + this.panelW, this.contentY - 5, Glass.CONTROL_BORDER);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		switch (this.page) {
			case LOOK -> this.lookStatus(graphics, this.current());
			case STYLE -> this.hint(graphics, "Also styles your real armor when no look is set.");
			case TRIM -> this.hint(graphics, "Also on inventory icons. Texture packs apply.");
			case DESIGN -> this.hint(graphics, "Up to 3 patterns in any color, over your armor.");
			case RUNE -> this.hint(graphics, "Particles only you see. Press F5 to look at them.");
			case FIT -> this.hint(graphics, "Items and blocks, sized to the armor shape.");
		}

		LocalPlayer player = this.minecraft.player;

		if (player != null) {
			int size = (int) Math.min((y1 - y0) / 2.6F, (x1 - x0) / 1.4F);
			this.extractPreview(graphics, x0, y0, x1, y1, size, mouseX, mouseY, player);
		}
	}

	/** Gray help text under the page, wrapped to the panel width. */
	private void hint(GuiGraphicsExtractor graphics, String text) {
		int y = this.hintY.getOrDefault(this.page, this.contentY);

		for (FormattedCharSequence line : this.font.split(Component.literal(text), this.panelW)) {
			if (y > this.height - 48) break;
			graphics.text(this.font, line, this.panelX, y, GRAY);
			y += this.font.lineHeight + 1;
		}
	}

	private void lookStatus(GuiGraphicsExtractor graphics, SlotSettings s) {
		int y = this.contentY + 2 * (ROW + GAP) + 6;
		ItemStack stack = s.stack();
		Component text;
		int color;

		if (s.invisible) {
			text = Component.literal(this.selected.label + " is invisible.");
			color = GRAY;
		} else if (s.item.isBlank()) {
			text = Component.literal("Showing your real armor.");
			color = GRAY;
		} else if (stack.isEmpty()) {
			text = Component.literal("Unknown item id");
			color = RED;
		} else if (VanityCosmetics.usesVanillaLook(this.selected, stack)) {
			text = Component.literal("Looks like ").append(stack.getHoverName());
			color = GREEN;
		} else {
			text = Component.literal(this.selected.label + ": ").append(stack.getHoverName()).append(" (fitted)");
			color = GREEN;
		}

		graphics.text(this.font, text, this.panelX, y, color);
	}

	/** Same as the inventory's player preview (follows the mouse), plus a turntable angle. */
	private void extractPreview(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float mouseX, float mouseY, LocalPlayer player) {
		float centerX = (x0 + x1) / 2.0F;
		float centerY = (y0 + y1) / 2.0F;
		float xAngle = (float) Math.atan((centerX - mouseX) / 40.0F);
		float yAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX((float) Math.toRadians(yAngle * 20.0F));
		rotation.mul(xRotation);

		var renderer = this.minecraft.getEntityRenderDispatcher().getRenderer(player);
		EntityRenderState renderState = renderer.createRenderState(player, 1.0F);
		renderState.shadowPieces.clear();
		renderState.outlineColor = 0;

		if (renderState instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + xAngle * 20.0F + this.previewYaw;
			living.yRot = xAngle * 20.0F;
			living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20.0F : 0.0F;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1.0F;
		}

		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.entity(renderState, (float) size, translation, rotation, xRotation, x0, y0, x1, y1);
	}

	@Override
	public void removed() {
		config().save();
		super.removed();
	}
}
