package dev.susyst.vanity.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
import dev.susyst.vanity.config.GlintMode;
import dev.susyst.vanity.config.Rune;
import dev.susyst.vanity.config.RuneEffect;
import dev.susyst.vanity.config.RuneShape;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.TrimMode;
import dev.susyst.vanity.config.Trims;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.VanityCosmetics;

/**
 * The wardrobe, in a frosted-glass style. Left: live preview. Right: pick a slot (or All), then a page:
 * Look, Style (glint, dye, opacity, glow), Trim, Design (custom trims) and Rune (particles).
 * Every choice with more than two options is a dropdown.
 */
public final class VanityScreen extends Screen {
	private static final int ROW = 20;
	private static final int GAP = 4;
	private static final int GRAY = Glass.TEXT_DIM;
	private static final int GREEN = 0xFF7CF5B0;
	private static final int RED = 0xFFFF7A7A;
	private static final int MENU_ROW = 14;
	private static final int MENU_MAX_ROWS = 10;
	/** The 16 dye colors, for the quick color rows. */
	private static final List<Integer> DYE_COLORS = Trims.PALETTE.subList(0, 16);

	public enum Page {
		LOOK("Look"),
		STYLE("Style"),
		TRIM("Trim"),
		DESIGN("Design"),
		RUNE("Rune");

		final String label;

		Page(String label) {
			this.label = label;
		}
	}

	/** One entry of a dropdown: a label and an optional color sample (-1 for none). */
	private record Option(String label, int swatch) {
		static Option of(String label) {
			return new Option(label, -1);
		}
	}

	/** The dropdown that is open, under (or above) the button that opened it. */
	private record Menu(int x, int y, int w, int anchorY, List<Option> options, int selected, IntConsumer onPick) {
	}

	private CosmeticSlot selected;
	/** Editing all four slots at once: the Head slot is the template and changes are copied to the others. */
	private boolean all;
	private Page page;
	private int designLayer;
	private float previewYaw;
	private Menu menu;
	private int menuScroll;

	private final List<GlassButton> slotTabs = new ArrayList<>();
	private GlassButton allTab;
	private final Map<Page, GlassButton> pageTabs = new EnumMap<>(Page.class);
	private final Map<Page, List<AbstractWidget>> pageWidgets = new EnumMap<>(Page.class);
	/** Widgets only shown when editing one slot. */
	private final List<AbstractWidget> singleSlotWidgets = new ArrayList<>();
	/** Actions run by {@link #refresh()} to show the current settings on the widgets. */
	private final List<Runnable> syncers = new ArrayList<>();

	private EditBox itemBox;
	private boolean syncing;
	private boolean initialized;
	private int previewRight;
	private int panelX;
	private int panelW;
	private int contentY;
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

	/** Opens the wardrobe editing all four slots at once. */
	public static VanityScreen allSlots(Page page) {
		VanityScreen screen = new VanityScreen(CosmeticSlot.HEAD, page);
		screen.all = true;
		return screen;
	}

	private static VanityConfig config() {
		return VanityClient.config();
	}

	/** The slot being edited; in All mode, the Head slot acts as the template. */
	private SlotSettings current() {
		return config().get(this.all ? CosmeticSlot.HEAD : this.selected);
	}

	private TrimLayer layer() {
		return this.current().design[this.designLayer];
	}

	private Rune rune() {
		return this.current().rune;
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

	// ---- editing ----

	/** After any edit: in All mode, copy the edited page's settings from the template to the other slots. */
	private void changed() {
		if (!this.all) return;
		SlotSettings source = config().head;

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (slot == CosmeticSlot.HEAD) continue;
			SlotSettings target = config().get(slot);

			switch (this.page) {
				case LOOK -> {
					target.invisible = source.invisible;
					target.onlyWhenWorn = source.onlyWhenWorn;
				}
				case STYLE -> target.copyStyleFrom(source);
				case TRIM -> target.copyTrimFrom(source);
				case DESIGN -> target.copyDesignFrom(source);
				case RUNE -> target.rune = source.rune.copy();
			}
		}
	}

	private void applyToAll(Consumer<SlotSettings> copy) {
		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (slot != this.selected) copy.accept(config().get(slot));
		}
	}

	// ---- widget helpers ----

	private <T extends AbstractWidget> T on(Page page, T widget) {
		this.addRenderableWidget(widget);
		this.pageWidgets.computeIfAbsent(page, p -> new ArrayList<>()).add(widget);
		return widget;
	}

	/** A page button that edits settings: runs the action, applies it to all slots in All mode, refreshes. */
	private GlassButton button(Page page, int x, int y, int w, String label, Runnable action) {
		return this.button(page, x, y, w, label, GlassButton.Style.NORMAL, action);
	}

	private GlassButton button(Page page, int x, int y, int w, String label, GlassButton.Style style, Runnable action) {
		GlassButton button = new GlassButton(x, y, w, ROW, Component.literal(label), b -> {
			action.run();
			if (page != null) this.changed();
			this.refresh();
		}, style);
		return page == null ? this.addRenderableWidget(button) : this.on(page, button);
	}

	private void sync(Runnable syncer) {
		this.syncers.add(syncer);
	}

	/** A dropdown: a button showing the current choice; clicking it opens a list of options. */
	private GlassButton dropdown(Page page, int x, int y, int w, String title, Supplier<List<Option>> options, IntSupplier current, IntConsumer onPick) {
		GlassButton button = new GlassButton(x, y, w, ROW, Component.empty(), b -> {
			List<Option> list = options.get();
			this.menu = new Menu(x, y + ROW + 1, w, y, list, current.getAsInt(), index -> {
				onPick.accept(index);
				this.changed();
			});
			int selectedIndex = Math.max(0, current.getAsInt());
			this.menuScroll = Math.max(0, Math.min(selectedIndex - MENU_MAX_ROWS / 2, list.size() - MENU_MAX_ROWS));
		}, GlassButton.Style.NORMAL);
		this.on(page, button);
		this.sync(() -> {
			List<Option> list = options.get();
			int index = current.getAsInt();
			String value = index >= 0 && index < list.size() ? list.get(index).label() : "Custom";
			MutableComponent label = Component.literal(title.isEmpty() ? value : title + ": " + value);
			if (index >= 0 && index < list.size() && list.get(index).swatch() >= 0) label.append(" ").append(swatch(list.get(index).swatch()));
			button.setMessage(label.append(Component.literal("  ▾").withColor(0x8FA3B8)));
		});
		return button;
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
				this.changed();
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
				this.changed();
				this.refresh();
			}
		});
		this.sync(() -> {
			if (!box.isFocused()) box.setValue(String.format(Locale.ROOT, "#%06X", get.getAsInt() & 0xFFFFFF));
		});
	}

	private interface FloatGetter {
		float get(SlotSettings settings);
	}

	private interface FloatSetter {
		void set(SlotSettings settings, float value);
	}

	private void slider(Page page, int x, int y, int w, String label, double min, double max, double step, String format, FloatGetter get, FloatSetter set) {
		ValueSlider slider = this.on(page, new ValueSlider(x, y, w, ROW, label, min, max, step, format, 0, v -> {
			if (this.syncing) return;
			set.set(this.current(), (float) v);
			this.changed();
			this.refresh();
		}));
		this.sync(() -> slider.setRealValue(get.get(this.current())));
	}

	/** "Copy to the other three slots", shown only when editing one slot. */
	private void copyToAllButton(Page page, int x, int y, int w, String what, Consumer<SlotSettings> copy) {
		GlassButton button = this.button(page, x, y, w, "All slots", () -> this.applyToAll(copy));
		button.setTooltip(tip("Copy this slot's " + what + " to the other three slots"));
		this.singleSlotWidgets.add(button);
	}

	// ---- option lists ----

	private static List<Option> armorSetOptions() {
		List<Option> options = new ArrayList<>();
		options.add(Option.of("Real armor"));
		for (ArmorSet set : ArmorSet.values()) options.add(Option.of(set.label));
		return options;
	}

	private static int armorSetIndex() {
		ArmorSet set = ArmorSet.wornBy(config());
		if (set != null) return set.ordinal() + 1;
		return ArmorSet.noneWorn(config()) ? 0 : -1;
	}

	private static List<Option> patternOptions() {
		List<Option> options = new ArrayList<>();
		for (String name : Trims.PATTERNS) options.add(Option.of(Trims.displayName(name)));
		return options;
	}

	private static int patternIndex(String id) {
		return Trims.PATTERNS.indexOf(id.startsWith("minecraft:") ? id.substring(10) : id);
	}

	private static List<Option> materialOptions() {
		List<Option> options = new ArrayList<>();
		for (String name : Trims.MATERIALS) options.add(new Option(Trims.displayName(name), Trims.materialColor(name)));
		return options;
	}

	private static <E extends Enum<E>> List<Option> enumOptions(E[] values, java.util.function.Function<E, String> label) {
		List<Option> options = new ArrayList<>();
		for (E value : values) options.add(Option.of(label.apply(value)));
		return options;
	}

	private List<Option> runeEffectOptions() {
		List<Option> options = new ArrayList<>();
		options.add(Option.of("None"));
		for (RuneEffect effect : RuneEffect.values()) {
			options.add(effect.colorable ? new Option(effect.label, this.rune().color) : Option.of(effect.label));
		}
		return options;
	}

	// ---- layout ----

	@Override
	protected void init() {
		this.initialized = false;
		this.menu = null;
		this.slotTabs.clear();
		this.pageTabs.clear();
		this.pageWidgets.clear();
		this.singleSlotWidgets.clear();
		this.syncers.clear();

		this.previewRight = Math.max(110, (int) (this.width * 0.36F));
		this.panelX = this.previewRight + 14;
		this.panelW = Math.max(200, Math.min(this.width - this.panelX - 16, 300));
		int halfW = (this.panelW - GAP) / 2;
		int thirdW = (this.panelW - GAP * 2) / 3;
		int x2 = this.panelX + thirdW + GAP;
		int x3 = this.panelX + 2 * (thirdW + GAP);
		int step = ROW + GAP;

		// Slot tabs: All, Head, Chest, Legs, Feet
		int y = 32;
		CosmeticSlot[] slots = CosmeticSlot.values();
		int tabW = (this.panelW - 2 * slots.length) / (slots.length + 1);
		this.allTab = this.button(null, this.panelX, y, tabW, "All", GlassButton.Style.SEGMENT, () -> {
			this.all = true;
			this.syncItemBox();
		});
		this.allTab.setTooltip(tip("Change all four armor pieces at once"));

		for (int i = 0; i < slots.length; i++) {
			CosmeticSlot slot = slots[i];
			this.slotTabs.add(this.button(null, this.panelX + (i + 1) * (tabW + 2), y, tabW, slot.label, GlassButton.Style.SEGMENT, () -> this.select(slot)));
		}

		// Page tabs
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
		for (Page p : pages) this.hintY.put(p, r4 + step + 2);
		this.hintY.put(Page.TRIM, r4 + 2);

		// ---- Look ----
		this.dropdown(Page.LOOK, this.panelX, r0, this.panelW, "All slots look like", VanityScreen::armorSetOptions, VanityScreen::armorSetIndex, index -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				config().get(slot).setLook(index == 0 ? "" : ArmorSet.values()[index - 1].itemId(slot));
			}
			this.syncItemBox();
		}).setTooltip(tip("Change how all four slots look. Your real armor stays the same; only the look changes."));

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
		this.singleSlotWidgets.add(this.itemBox);

		GlassButton held = this.button(Page.LOOK, this.panelX + boxW + GAP, r1, smallW, "Held", this::useHeldItem);
		held.setTooltip(tip("Use the exact item in your main hand, including its name, dye, head skin and SkyBlock data"));
		GlassButton worn = this.button(Page.LOOK, this.panelX + boxW + GAP + (smallW + GAP), r1, smallW, "Worn", this::useWornItem);
		worn.setTooltip(tip("Save the look of what you're wearing in this slot now (e.g. a SkyBlock piece), then wear anything else"));
		GlassButton clear = this.button(Page.LOOK, this.panelX + boxW + GAP + 2 * (smallW + GAP), r1, smallW, "Clear", () -> {
			this.current().clearLook();
			this.syncItemBox();
		});
		clear.setTooltip(tip("Go back to your real armor in this slot"));
		this.singleSlotWidgets.addAll(List.of(held, worn, clear));

		GlassButton invisible = this.button(Page.LOOK, this.panelX, r3, halfW, "", () -> this.current().invisible = !this.current().invisible);
		invisible.setTooltip(tip("Show nothing in this slot, not even your real armor"));
		this.sync(() -> invisible.setMessage(Component.literal("Invisible: " + onOff(this.current().invisible))));

		GlassButton wornOnly = this.button(Page.LOOK, this.panelX + halfW + GAP, r3, halfW, "", () -> this.current().onlyWhenWorn = !this.current().onlyWhenWorn);
		wornOnly.setTooltip(tip("Hide: the look and rune go away when you take the armor off. Show: they stay with nothing equipped."));
		this.sync(() -> wornOnly.setMessage(Component.literal("No armor: " + (this.current().onlyWhenWorn ? "Hide" : "Show"))));

		// ---- Style ----
		this.dropdown(Page.STYLE, this.panelX, r0, halfW, "Glint", () -> enumOptions(GlintMode.values(), g -> g.label), () -> this.current().glint.ordinal(),
				index -> this.current().glint = GlintMode.values()[index])
				.setTooltip(tip("Enchantment shine. Like real: shine if the armor you're wearing is enchanted."));

		GlassButton dye = this.button(Page.STYLE, this.panelX + halfW + GAP, r0, halfW, "", () -> this.current().customDye = !this.current().customDye);
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
		this.copyToAllButton(Page.STYLE, x3, r3, thirdW, "glint, dye, opacity and glow", s -> s.copyStyleFrom(this.current()));

		this.slider(Page.STYLE, this.panelX, r4, thirdW, "Opacity", 10, 100, 5, "%.0f%%", s -> s.opacity * 100.0F, (s, v) -> s.opacity = v / 100.0F);
		this.slider(Page.STYLE, x2, r4, thirdW, "Glow", 0, 100, 5, "%.0f%%", s -> s.glow * 100.0F, (s, v) -> s.glow = v / 100.0F);
		this.slider(Page.STYLE, x3, r4, thirdW, "Trim glow", 0, 100, 5, "%.0f%%", s -> s.trimGlowAmount * 100.0F, (s, v) -> s.trimGlowAmount = v / 100.0F);

		// ---- Trim ----
		this.dropdown(Page.TRIM, this.panelX, r0, this.panelW, "Trim", () -> enumOptions(TrimMode.values(), m -> m.label), () -> this.current().trim.ordinal(),
				index -> this.current().trim = TrimMode.values()[index])
				.setTooltip(tip("Your armor's: keep the trim of what you're wearing. None: no trim. Pick: the pattern and material below."));
		this.dropdown(Page.TRIM, this.panelX, r1, this.panelW, "Pattern", VanityScreen::patternOptions, () -> patternIndex(this.current().trimPattern), index -> {
			this.current().trimPattern = Trims.id(Trims.PATTERNS.get(index));
			this.current().trim = TrimMode.PICK;
		});
		this.dropdown(Page.TRIM, this.panelX, r2, this.panelW, "Material", VanityScreen::materialOptions, () -> Trims.MATERIALS.indexOf(stripNamespace(this.current().trimMaterial)), index -> {
			this.current().trimMaterial = Trims.id(Trims.MATERIALS.get(index));
			this.current().trim = TrimMode.PICK;
		});
		this.button(Page.TRIM, this.panelX, r3, halfW, "Reset", () -> this.current().resetTrim());
		this.copyToAllButton(Page.TRIM, this.panelX + halfW + GAP, r3, halfW, "trim", s -> s.copyTrimFrom(this.current()));

		// ---- Design ----
		int layerW = (this.panelW - GAP * 3) / 4;

		for (int k = 0; k < SlotSettings.DESIGN_LAYERS; k++) {
			int index = k;
			GlassButton tab = this.button(Page.DESIGN, this.panelX + k * (layerW + GAP), r0, layerW, "", GlassButton.Style.SEGMENT, () -> this.designLayer = index);
			this.sync(() -> {
				TrimLayer l = this.current().design[index];
				MutableComponent label = Component.literal("Layer " + (index + 1));
				if (l.enabled) label.append(" ").append(Component.literal("█").withColor(l.color & 0xFFFFFF));
				tab.setMessage(label);
				tab.selected = this.designLayer == index;
			});
		}

		GlassButton layerOn = this.button(Page.DESIGN, this.panelX + 3 * (layerW + GAP), r0, layerW, "", () -> this.layer().enabled = !this.layer().enabled);
		layerOn.setTooltip(tip("Show or hide this layer"));
		this.sync(() -> layerOn.setMessage(Component.literal(this.layer().enabled ? "Shown" : "Hidden")));

		this.dropdown(Page.DESIGN, this.panelX, r1, this.panelW, "Pattern", VanityScreen::patternOptions, () -> patternIndex(this.layer().pattern), index -> {
			this.layer().pattern = Trims.id(Trims.PATTERNS.get(index));
			this.layer().enabled = true;
		});

		IntConsumer setLayerColor = color -> {
			this.layer().color = color;
			this.layer().enabled = true;
		};
		this.colorRow(Page.DESIGN, r2, () -> this.layer().color, setLayerColor);
		this.colorSliders(Page.DESIGN, r3, thirdW, () -> this.layer().color, setLayerColor);
		this.hexBox(Page.DESIGN, this.panelX, r4, thirdW, () -> this.layer().color, setLayerColor);
		this.button(Page.DESIGN, x2, r4, thirdW, "Clear", () -> this.current().resetDesign());
		this.copyToAllButton(Page.DESIGN, x3, r4, thirdW, "design", s -> s.copyDesignFrom(this.current()));

		// ---- Rune ----
		this.dropdown(Page.RUNE, this.panelX, r0, halfW, "Effect", this::runeEffectOptions, () -> this.rune().enabled ? this.rune().effect.ordinal() + 1 : 0, index -> {
			this.rune().enabled = index > 0;
			if (index > 0) this.rune().effect = RuneEffect.values()[index - 1];
		});
		this.dropdown(Page.RUNE, this.panelX + halfW + GAP, r0, halfW, "Shape", () -> enumOptions(RuneShape.values(), s -> s.label),
				() -> this.rune().shapeFor(this.all ? CosmeticSlot.HEAD : this.selected).ordinal(),
				index -> this.rune().shape = RuneShape.values()[index]);

		IntConsumer setRuneColor = color -> {
			Rune rune = this.rune();
			rune.color = color;
			if (!rune.enabled || !rune.effect.colorable) {
				rune.enabled = true;
				rune.effect = RuneEffect.DUST;
			}
		};
		this.colorRow(Page.RUNE, r1, () -> this.rune().enabled && this.rune().effect.colorable ? this.rune().color : -1, setRuneColor);
		this.slider(Page.RUNE, this.panelX, r2, thirdW, "Size", 0.5, 3.0, 0.1, "%.1f", s -> s.rune.size, (s, v) -> s.rune.size = v);
		this.slider(Page.RUNE, x2, r2, thirdW, "Detail", 6, 40, 1, "%.0f", s -> s.rune.detail, (s, v) -> s.rune.detail = Math.round(v));
		this.slider(Page.RUNE, x3, r2, thirdW, "Length", 4, 40, 1, "%.0f", s -> s.rune.length, (s, v) -> s.rune.length = Math.round(v));
		this.hexBox(Page.RUNE, this.panelX, r3, thirdW, () -> this.rune().color, setRuneColor);
		this.slider(Page.RUNE, x2, r3, thirdW, "Speed", 0.25, 3.0, 0.25, "%.2f", s -> s.rune.speed, (s, v) -> s.rune.speed = v);
		GlassButton firstPerson = this.button(Page.RUNE, x3, r3, thirdW, "", () -> config().runesInFirstPerson = !config().runesInFirstPerson);
		firstPerson.setTooltip(tip("Also show runes in first person (they can get in your way)"));
		this.sync(() -> firstPerson.setMessage(Component.literal("1st person: " + onOff(config().runesInFirstPerson))));
		this.button(Page.RUNE, this.panelX, r4, halfW, "Reset", () -> {
			boolean wasOn = this.rune().enabled;
			RuneEffect effect = this.rune().effect;
			this.current().rune = new Rune();
			this.current().rune.enabled = wasOn;
			this.current().rune.effect = effect;
		}).setTooltip(tip("Reset shape, color and sliders"));
		this.copyToAllButton(Page.RUNE, this.panelX + halfW + GAP, r4, halfW, "rune", s -> s.rune = this.rune().copy());

		// ---- Bottom row ----
		int bottomY = this.height - 36;
		this.addRenderableWidget(new ValueSlider(18, bottomY, this.previewRight - 32, ROW, "Turn view", 0, 360, 5, "%.0f", this.previewYaw, v -> this.previewYaw = (float) v));

		GlassButton enabled = this.button(null, this.panelX, bottomY, thirdW, "", () -> config().enabled = !config().enabled);
		enabled.setTooltip(tip("Turn all Vanity looks on or off"));
		this.sync(() -> enabled.setMessage(Component.literal("Vanity: " + onOff(config().enabled))));

		GlassButton icons = this.button(null, x2, bottomY, thirdW, "", () -> config().showInInventory = !config().showInInventory);
		icons.setTooltip(tip("Also show the looks on your worn armor's icons in the inventory"));
		this.sync(() -> icons.setMessage(Component.literal("Inv icons: " + onOff(config().showInInventory))));

		this.button(null, x3, bottomY, thirdW, "Done", GlassButton.Style.PRIMARY, this::onClose);

		this.syncItemBox();
		this.initialized = true;
		this.refresh();
	}

	private static String stripNamespace(String id) {
		return id.startsWith("minecraft:") ? id.substring(10) : id;
	}

	// ---- actions ----

	private void select(CosmeticSlot slot) {
		this.all = false;
		this.selected = slot;
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
		this.itemBox.setValue(this.all ? "" : this.current().item);
		this.itemBox.moveCursorToStart(false);
		this.syncing = false;
	}

	/** Syncs every widget with the selected slot and page. */
	private void refresh() {
		if (!this.initialized) return;
		this.syncing = true;

		CosmeticSlot[] slots = CosmeticSlot.values();
		this.allTab.selected = this.all;

		for (int i = 0; i < this.slotTabs.size(); i++) {
			this.slotTabs.get(i).selected = !this.all && slots[i] == this.selected;
		}

		for (Map.Entry<Page, GlassButton> entry : this.pageTabs.entrySet()) {
			entry.getValue().selected = entry.getKey() == this.page;
		}

		for (Map.Entry<Page, List<AbstractWidget>> entry : this.pageWidgets.entrySet()) {
			boolean visible = entry.getKey() == this.page;
			for (AbstractWidget widget : entry.getValue()) widget.visible = visible;
		}

		if (this.all) {
			for (AbstractWidget widget : this.singleSlotWidgets) widget.visible = false;
		}

		for (Runnable syncer : this.syncers) syncer.run();

		this.syncing = false;
	}

	// ---- dropdown menu ----

	private int menuRows() {
		return Math.min(MENU_MAX_ROWS, this.menu.options().size());
	}

	/** Top of the open menu: below its button, or above it if there's no room. */
	private int menuTop() {
		int h = this.menuRows() * MENU_ROW + 4;
		return this.menu.y() + h > this.height - 4 ? this.menu.anchorY() - h - 1 : this.menu.y();
	}

	private boolean inMenu(double mouseX, double mouseY) {
		if (this.menu == null) return false;
		int top = this.menuTop();
		return mouseX >= this.menu.x() && mouseX < this.menu.x() + this.menu.w() && mouseY >= top && mouseY < top + this.menuRows() * MENU_ROW + 4;
	}

	private int menuIndexAt(double mouseX, double mouseY) {
		if (!this.inMenu(mouseX, mouseY)) return -1;
		int row = (int) ((mouseY - this.menuTop() - 2) / MENU_ROW);
		int index = this.menuScroll + row;
		return row >= 0 && row < this.menuRows() && index < this.menu.options().size() ? index : -1;
	}

	private void extractMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Menu m = this.menu;
		int rows = this.menuRows();
		int top = this.menuTop();
		int h = rows * MENU_ROW + 4;
		g.nextStratum();
		Glass.fill(g, m.x(), top, m.w(), h, 0xF2121720);
		Glass.border(g, m.x(), top, m.w(), h, Glass.alpha(Glass.ACCENT, 0x90));
		int hovered = this.menuIndexAt(mouseX, mouseY);

		for (int r = 0; r < rows; r++) {
			int index = this.menuScroll + r;
			Option option = m.options().get(index);
			int rowY = top + 2 + r * MENU_ROW;

			if (index == m.selected()) {
				g.fill(m.x() + 2, rowY, m.x() + m.w() - 2, rowY + MENU_ROW, Glass.alpha(Glass.ACCENT, 0x45));
			} else if (index == hovered) {
				g.fill(m.x() + 2, rowY, m.x() + m.w() - 2, rowY + MENU_ROW, 0x26FFFFFF);
			}

			int textX = m.x() + 8;

			if (option.swatch() >= 0) {
				Glass.fill(g, textX, rowY + 3, 8, 8, 0xFF000000 | option.swatch());
				textX += 13;
			}

			int color = index == m.selected() ? Glass.ACCENT : index == hovered ? Glass.TEXT : Glass.TEXT_DIM;
			g.text(this.font, Component.literal(option.label()), textX, rowY + 3, color, false);
		}

		// Scroll bar for long lists
		if (m.options().size() > rows) {
			int trackH = h - 4;
			int barH = Math.max(8, trackH * rows / m.options().size());
			int barY = top + 2 + (trackH - barH) * this.menuScroll / (m.options().size() - rows);
			g.fill(m.x() + m.w() - 4, barY, m.x() + m.w() - 2, barY + barH, 0x80FFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.menu != null) {
			int index = this.menuIndexAt(event.x(), event.y());
			Menu m = this.menu;
			this.menu = null;

			if (index >= 0) {
				m.onPick().accept(index);
				this.refresh();
			}

			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (this.menu != null) {
			int max = Math.max(0, this.menu.options().size() - this.menuRows());
			this.menuScroll = Math.max(0, Math.min(max, this.menuScroll - (int) Math.signum(scrollY)));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.menu != null && event.isEscape()) {
			this.menu = null;
			return true;
		}

		return super.keyPressed(event);
	}

	// ---- drawing ----

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

		graphics.text(this.font, Component.literal("VANITY").withStyle(ChatFormatting.BOLD), this.panelX, 16, Glass.ACCENT);
		graphics.text(this.font, Component.literal("wardrobe"), this.panelX + this.font.width("VANITY") + 8, 16, Glass.TEXT_DIM);
		graphics.fill(this.panelX, this.contentY - 6, this.panelX + this.panelW, this.contentY - 5, Glass.CONTROL_BORDER);

		// While a menu is open, widgets under it shouldn't light up
		boolean overMenu = this.inMenu(mouseX, mouseY);
		super.extractRenderState(graphics, overMenu ? -1 : mouseX, overMenu ? -1 : mouseY, a);

		switch (this.page) {
			case LOOK -> this.lookStatus(graphics, this.current());
			case STYLE -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Also styles your real armor when no look is set.");
			case TRIM -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Also on inventory icons. Texture packs apply.");
			case DESIGN -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Up to 3 patterns in any color, over your armor.");
			case RUNE -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Particles only you see. Press F5 to look at them.");
		}

		LocalPlayer player = this.minecraft.player;

		if (player != null) {
			int size = (int) Math.min((y1 - y0) / 2.6F, (x1 - x0) / 1.4F);
			this.extractPreview(graphics, x0, y0, x1, y1, size, mouseX, mouseY, player);
		}

		if (this.menu != null) this.extractMenu(graphics, mouseX, mouseY);
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

		if (this.all) {
			graphics.text(this.font, Component.literal("Pick a slot above to set its own item."), this.panelX, this.contentY + ROW + GAP + 6, GRAY);
			return;
		}

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
			text = Component.literal(this.selected.label + ": ").append(stack.getHoverName());
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
