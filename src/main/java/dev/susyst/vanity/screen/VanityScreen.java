package dev.susyst.vanity.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.ArmorSet;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.GlintMode;
import dev.susyst.vanity.config.Rune;
import dev.susyst.vanity.config.RuneEffect;
import dev.susyst.vanity.config.RuneShape;
import dev.susyst.vanity.AutoOutfits;
import dev.susyst.vanity.config.DyeAnimation;
import dev.susyst.vanity.config.HitEffect;
import dev.susyst.vanity.config.KillEffect;
import dev.susyst.vanity.config.GlowAnimation;
import dev.susyst.vanity.config.ItemSkin;
import dev.susyst.vanity.config.Outfit;
import dev.susyst.vanity.config.RuneReaction;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.TrimMode;
import dev.susyst.vanity.config.Trims;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.CombatParticles;
import dev.susyst.vanity.render.CustomArmors;
import dev.susyst.vanity.render.ItemSkins;
import dev.susyst.vanity.render.VanityCosmetics;

/**
 * The wardrobe, in a frosted-glass style. Left: live preview. Right: pick a slot (or All), then a page:
 * Look, Style (glint, dye, opacity, glow), Trim, Design (custom trims), Rune (particles) and Outfits.
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
		RUNE("Rune"),
		OUTFITS("Outfits"),
		/** Held-item skins; opened with the Hand tab, not a page tab. */
		HAND("Item skins"),
		/** Hit and kill effects, the Hand tab's second page. */
		EFFECTS("Hit & kill");

		final String label;

		Page(String label) {
			this.label = label;
		}

		boolean hasTab() {
			return !this.isHand();
		}

		boolean isHand() {
			return this == HAND || this == EFFECTS;
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
	// Item picker (Pick buttons): grabbed items and your own items, drawn over the wardrobe
	private static final int CELL = 18;
	private Consumer<ItemStack> pickerAction;
	private String pickerTitle = "";
	private List<ItemStack> pickerGrabbed = List.of();
	private List<ItemStack> pickerOwn = List.of();
	private int pickerScroll;

	/** The page to go back to when leaving the Hand tab. */
	private Page lastPage = Page.LOOK;
	private int skinIndex;

	private final List<GlassButton> slotTabs = new ArrayList<>();
	private GlassButton allTab;
	private GlassButton handTab;
	private final List<GlassButton> handPageTabs = new ArrayList<>();
	private final Map<Page, GlassButton> pageTabs = new EnumMap<>(Page.class);
	private final Map<Page, List<AbstractWidget>> pageWidgets = new EnumMap<>(Page.class);
	/** Widgets only shown when editing one slot. */
	private final List<AbstractWidget> singleSlotWidgets = new ArrayList<>();
	/** Actions run by {@link #refresh()} to show the current settings on the widgets. */
	private final List<Runnable> syncers = new ArrayList<>();

	private EditBox itemBox;
	private EditBox outfitNameBox;
	private EditBox skinLookBox;
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

	/** Opens the wardrobe on the Hand tab (held-item skins). */
	public static VanityScreen hand() {
		VanityScreen screen = new VanityScreen(CosmeticSlot.HEAD, Page.HAND);
		return screen;
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

	/** The held-item skin picked on the Hand tab, or null. */
	private ItemSkin skin() {
		List<ItemSkin> skins = config().itemSkins;
		if (skins.isEmpty()) return null;
		this.skinIndex = Math.max(0, Math.min(this.skinIndex, skins.size() - 1));
		return skins.get(this.skinIndex);
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
				case OUTFITS, HAND, EFFECTS -> {
				}
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
		return this.dropdown(page, x, y, w, title, "Custom", options, current, onPick);
	}

	private GlassButton dropdown(Page page, int x, int y, int w, String title, String fallback, Supplier<List<Option>> options, IntSupplier current, IntConsumer onPick) {
		GlassButton button = new GlassButton(x, y, w, ROW, Component.empty(), b -> {
			List<Option> list = options.get();
			this.menu = new Menu(x, y + ROW + 1, w, y, list, current.getAsInt(), index -> {
				onPick.accept(index);
				this.changed();
			});
			int selectedIndex = Math.max(0, current.getAsInt());
			this.menuScroll = 0;
			this.menuScroll = this.clampScroll(selectedIndex - this.menuRows() / 2);
		}, GlassButton.Style.NORMAL);
		this.on(page, button);
		this.sync(() -> {
			List<Option> list = options.get();
			int index = current.getAsInt();
			String value = index >= 0 && index < list.size() ? list.get(index).label() : fallback;
			MutableComponent label = Component.literal(title.isEmpty() ? value : title + ": " + value);
			if (index >= 0 && index < list.size() && list.get(index).swatch() >= 0) label.append(" ").append(swatch(list.get(index).swatch()));
			button.setMessage(label.append(Component.literal(" ▾").withColor(0x8FA3B8)));
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

	private ValueSlider slider(Page page, int x, int y, int w, String label, double min, double max, double step, String format, FloatGetter get, FloatSetter set) {
		ValueSlider slider = this.on(page, new ValueSlider(x, y, w, ROW, label, min, max, step, format, 0, v -> {
			if (this.syncing) return;
			set.set(this.current(), (float) v);
			this.changed();
			this.refresh();
		}));
		this.sync(() -> slider.setRealValue(get.get(this.current())));
		return slider;
	}

	/** "Copy to the other three slots", shown only when editing one slot. */
	private void copyToAllButton(Page page, int x, int y, int w, String what, Consumer<SlotSettings> copy) {
		this.copyToAllButton(page, x, y, w, "All slots", what, copy);
	}

	private void copyToAllButton(Page page, int x, int y, int w, String label, String what, Consumer<SlotSettings> copy) {
		GlassButton button = this.button(page, x, y, w, label, () -> this.applyToAll(copy));
		button.setTooltip(tip("Copy this slot's " + what + " to the other three slots"));
		this.singleSlotWidgets.add(button);
	}

	// ---- option lists ----

	private static List<Option> armorSetOptions() {
		List<Option> options = new ArrayList<>();
		options.add(Option.of("Real armor"));
		for (ArmorSet set : ArmorSet.values()) options.add(Option.of(set.label));
		for (CustomArmors.Armor armor : CustomArmors.all()) options.add(new Option(armor.name() + (armor.user() ? " (yours)" : ""), armor.color()));
		options.add(Option.of("+ Your own armor..."));
		return options;
	}

	private static int armorSetIndex() {
		ArmorSet set = ArmorSet.wornBy(config());
		if (set != null) return set.ordinal() + 1;
		List<CustomArmors.Armor> custom = CustomArmors.all();

		for (int i = 0; i < custom.size(); i++) {
			String id = custom.get(i).lookId();
			boolean all = true;
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				SlotSettings s = config().get(slot);
				if (!id.equalsIgnoreCase(s.item) || s.itemStack != null) all = false;
			}
			if (all) return 1 + ArmorSet.values().length + i;
		}

		return ArmorSet.noneWorn(config()) ? 0 : -1;
	}

	/** Opens config/vanity/armors in your file browser, with a note on what to put there. */
	private void openArmorFolder() {
		CustomArmors.reloadUserArmors(this.minecraft);
		com.mojang.blaze3d.Blaze3D.openPath(CustomArmors.folder());
		net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(this.minecraft.gui.toastManager(), new net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId(6000L),
				Component.literal("Your armor folder"), Component.literal("Add <name>.png (64x32 armor texture), see README.txt. Reopen the wardrobe to load it."));
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

	private List<Option> dyeOptions() {
		List<Option> options = new ArrayList<>();
		options.add(Option.of("Off"));

		for (DyeAnimation animation : DyeAnimation.values()) {
			options.add(animation == DyeAnimation.SOLID ? new Option(animation.label, this.current().dyeColor) : Option.of(animation.label));
		}

		return options;
	}

	private List<Option> skinOptions() {
		List<Option> options = new ArrayList<>();
		if (config().itemSkins.isEmpty()) options.add(Option.of("None yet"));

		for (ItemSkin skin : config().itemSkins) {
			String look = skin.look().isEmpty() ? "?" : skin.look().getHoverName().getString();
			options.add(Option.of(skin.name + " → " + look));
		}

		return options;
	}

	private List<Option> outfitOptions() {
		List<Option> options = new ArrayList<>();
		if (config().outfits.isEmpty()) options.add(Option.of("None saved"));
		for (Outfit outfit : config().outfits) options.add(Option.of(outfit.name));
		return options;
	}

	/** First option keeps each piece's own shape (halo on the head, orbit on the chest, ...). */
	private List<Option> runeShapeOptions() {
		List<Option> list = new ArrayList<>();
		list.add(Option.of(this.all ? "Per piece" : "Auto (" + RuneShape.defaultFor(this.selected).label + ")"));
		for (RuneShape shape : RuneShape.values()) list.add(Option.of(shape.label));
		return list;
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
		// Pick up armor PNGs added to config/vanity/armors since last time
		CustomArmors.reloadUserArmors(this.minecraft);
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
		int tabW = (this.panelW - 2 * (slots.length + 1)) / (slots.length + 2);
		this.allTab = this.button(null, this.panelX, y, tabW, "All", GlassButton.Style.SEGMENT, () -> {
			this.leaveHand();
			this.all = true;
			this.syncItemBox();
		});
		this.allTab.setTooltip(tip("Change all four armor pieces at once"));

		for (int i = 0; i < slots.length; i++) {
			CosmeticSlot slot = slots[i];
			this.slotTabs.add(this.button(null, this.panelX + (i + 1) * (tabW + 2), y, tabW, slot.label, GlassButton.Style.SEGMENT, () -> this.select(slot)));
		}

		this.handTab = this.button(null, this.panelX + (slots.length + 1) * (tabW + 2), y, this.panelX + this.panelW - (this.panelX + (slots.length + 1) * (tabW + 2)), "Hand", GlassButton.Style.SEGMENT, () -> {
			if (!this.page.isHand()) {
				this.lastPage = this.page;
				this.page = Page.HAND;
			}
			this.all = false;
		});
		this.handTab.setTooltip(tip("Item skins for what you hold, and hit & kill effects"));

		// Page tabs
		y += step;
		Page[] pages = java.util.Arrays.stream(Page.values()).filter(Page::hasTab).toArray(Page[]::new);
		int pageW = this.panelW / pages.length;

		for (int i = 0; i < pages.length; i++) {
			Page p = pages[i];
			this.pageTabs.put(p, this.button(null, this.panelX + i * pageW, y, pageW, p.label, GlassButton.Style.TAB, () -> this.page = p));
		}

		// On the Hand tab the page tabs are Item skins and Hit & kill
		this.handPageTabs.clear();
		Page[] handPages = {Page.HAND, Page.EFFECTS};
		int handW = this.panelW / handPages.length;

		for (int i = 0; i < handPages.length; i++) {
			Page p = handPages[i];
			GlassButton tab = this.button(null, this.panelX + i * handW, y, handW, p.label, GlassButton.Style.TAB, () -> this.page = p);
			this.handPageTabs.add(tab);
		}

		this.contentY = y + ROW + 10;
		int r0 = this.contentY;
		int r1 = r0 + step;
		int r2 = r1 + step;
		int r3 = r2 + step;
		int r4 = r3 + step;
		for (Page p : Page.values()) this.hintY.put(p, r4 + step + 2);
		// The Look page has no fifth row, so its hint goes there
		this.hintY.put(Page.LOOK, r4 + 2);
		this.hintY.put(Page.TRIM, r4 + 2);

		// ---- Look ----
		this.dropdown(Page.LOOK, this.panelX, r0, this.panelW, "All slots look like", VanityScreen::armorSetOptions, VanityScreen::armorSetIndex, index -> {
			int vanilla = ArmorSet.values().length;
			List<CustomArmors.Armor> custom = CustomArmors.all();

			if (index == 1 + vanilla + custom.size()) {
				this.openArmorFolder();
				return;
			}

			for (CosmeticSlot slot : CosmeticSlot.values()) {
				String look;
				if (index == 0) look = "";
				else if (index <= vanilla) look = ArmorSet.values()[index - 1].itemId(slot);
				else look = custom.get(index - 1 - vanilla).lookId();
				config().get(slot).setLook(look);
			}
			this.syncItemBox();
		}).setTooltip(tip("Change how all four slots look: vanilla armor, Vanity's own designs, or armor you draw yourself. Your real armor stays the same."));

		int smallW = 36;
		int boxW = this.panelW - 3 * (smallW + GAP);
		this.itemBox = this.on(Page.LOOK, new GlassEditBox(this.font, this.panelX, r1, boxW, ROW, Component.literal("Item ID")));
		this.itemBox.setMaxLength(128);
		this.itemBox.setHint(Component.literal("netherite_chestplate or sb:ID"));
		this.itemBox.setResponder(value -> {
			if (this.syncing) return;
			this.current().setLook(value.trim());
			this.refresh();
		});
		this.singleSlotWidgets.add(this.itemBox);

		GlassButton held = this.button(Page.LOOK, this.panelX + boxW + GAP, r1, smallW, "Held", this::useHeldItem);
		held.setTooltip(tip("Use the exact item in your main hand, including its name, dye, head skin and SkyBlock data"));
		GlassButton worn = this.button(Page.LOOK, this.panelX + boxW + GAP + (smallW + GAP), r1, smallW, "Pick", () -> this.openPicker("Pick a look for " + this.selected.label, stack -> {
			this.current().setLook(stack, this.minecraft.player.registryAccess());
			this.syncItemBox();
		}));
		worn.setTooltip(tip("Pick the look from your items (what you wear is first) or items you grabbed with " + VanityClient.grabKeyName() + ". Keeps SkyBlock data and textures."));
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
		this.dropdown(Page.STYLE, this.panelX, r0, thirdW, "Glint", () -> enumOptions(GlintMode.values(), g -> g.label), () -> this.current().glint.ordinal(),
				index -> this.current().glint = GlintMode.values()[index])
				.setTooltip(tip("Enchantment shine. Real: shine if the armor you're wearing is enchanted."));

		this.dropdown(Page.STYLE, x2, r0, thirdW, "Dye", this::dyeOptions, () -> this.current().customDye ? this.current().dyeAnimation.ordinal() + 1 : 0, index -> {
			this.current().customDye = index > 0;
			if (index > 0) this.current().dyeAnimation = DyeAnimation.values()[index - 1];
		}).setTooltip(tip("Recolor leather looks. Solid: the color below. Rainbow cycles every color; Pulse and Shimmer animate the color below."));
		this.slider(Page.STYLE, x3, r0, thirdW, "Speed", 0.25, 4.0, 0.25, "%.2fx", s -> s.dyeSpeed, (s, v) -> s.dyeSpeed = v).setTooltip(tip("Speed of this piece's dye, design color and glow animations"));

		IntConsumer setDye = color -> {
			this.current().dyeColor = color;
			this.current().customDye = true;
			// Rainbow ignores the color, so picking one means you want that color
			if (this.current().dyeAnimation == DyeAnimation.RAINBOW) this.current().dyeAnimation = DyeAnimation.SOLID;
		};
		this.colorRow(Page.STYLE, r1, () -> this.current().customDye ? this.current().dyeColor : -1, setDye);
		this.colorSliders(Page.STYLE, r2, thirdW, () -> this.current().dyeColor, setDye);
		this.hexBox(Page.STYLE, this.panelX, r3, thirdW, () -> this.current().dyeColor, setDye);
		this.dropdown(Page.STYLE, x2, r3, thirdW, "", () -> enumOptions(GlowAnimation.values(), g -> g.label), () -> this.current().glowAnimation.ordinal(),
				index -> this.current().glowAnimation = GlowAnimation.values()[index])
				.setTooltip(tip("How Glow and Trim glow move. Breathe fades up and down, Rainbow glows in shifting colors, Flicker is like a torch. Works on any armor."));
		int sixthW = (thirdW - GAP) / 2;
		this.button(Page.STYLE, x3, r3, sixthW, "Reset", () -> this.current().resetStyle());
		this.copyToAllButton(Page.STYLE, x3 + sixthW + GAP, r3, thirdW - sixthW - GAP, "To all", "glint, dye, opacity and glow", s -> s.copyStyleFrom(this.current()));

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

		this.dropdown(Page.DESIGN, this.panelX, r1, halfW, "Pattern", VanityScreen::patternOptions, () -> patternIndex(this.layer().pattern), index -> {
			this.layer().pattern = Trims.id(Trims.PATTERNS.get(index));
			this.layer().enabled = true;
		});
		this.dropdown(Page.DESIGN, this.panelX + halfW + GAP, r1, halfW, "Colors", () -> enumOptions(DyeAnimation.values(), d -> d.label), () -> this.current().designAnimation.ordinal(),
				index -> this.current().designAnimation = DyeAnimation.values()[index])
				.setTooltip(tip("Animate the design's colors on any armor: Rainbow cycles every color, Pulse and Shimmer move each layer's color. Speed is on the Style page."));

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
		this.dropdown(Page.RUNE, this.panelX + halfW + GAP, r0, halfW, "Shape", this::runeShapeOptions,
				() -> this.rune().shape == null ? 0 : this.rune().shape.ordinal() + 1,
				index -> this.rune().shape = index == 0 ? null : RuneShape.values()[index - 1]);

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
		firstPerson.setTooltip(tip("ON: runes only show in third person (F5). OFF: also in first person, where they can get in your way."));
		this.sync(() -> firstPerson.setMessage(Component.literal("F5 only: " + onOff(!config().runesInFirstPerson))));
		this.button(Page.RUNE, this.panelX, r4, thirdW, "Reset", () -> {
			boolean wasOn = this.rune().enabled;
			RuneEffect effect = this.rune().effect;
			this.current().rune = new Rune();
			this.current().rune.enabled = wasOn;
			this.current().rune.effect = effect;
		}).setTooltip(tip("Reset shape, color and sliders"));
		this.dropdown(Page.RUNE, x2, r4, thirdW, "React", () -> enumOptions(RuneReaction.values(), r -> r.label), () -> this.rune().react.ordinal(),
				index -> this.rune().react = RuneReaction.values()[index])
				.setTooltip(tip("Hit: a burst when you take damage. Sprint: twice as dense, bigger and faster while sprinting."));
		this.copyToAllButton(Page.RUNE, x3, r4, thirdW, "rune", s -> s.rune = this.rune().copy());

		// ---- Outfits ----
		this.dropdown(Page.OUTFITS, this.panelX, r0, this.panelW, "Outfit", "Not saved", this::outfitOptions,
				() -> config().outfits.isEmpty() ? 0 : config().outfit, index -> {
			if (!config().outfits.isEmpty()) config().wearOutfit(index);
			this.syncItemBox();
		}).setTooltip(tip("Pick an outfit to wear it"));

		this.outfitNameBox = this.on(Page.OUTFITS, new GlassEditBox(this.font, this.panelX, r1, this.panelW, ROW, Component.literal("Outfit name")));
		this.outfitNameBox.setMaxLength(32);
		this.outfitNameBox.setHint(Component.literal("Outfit name"));
		this.outfitNameBox.setResponder(text -> {
			if (this.syncing) return;
			Outfit outfit = config().currentOutfit();
			if (outfit != null && !text.isBlank()) outfit.name = text.trim();
		});
		this.sync(() -> {
			Outfit outfit = config().currentOutfit();
			this.outfitNameBox.active = outfit != null;
			if (!this.outfitNameBox.isFocused()) this.outfitNameBox.setValue(outfit != null ? outfit.name : "");
		});

		GlassButton saveNew = this.button(Page.OUTFITS, this.panelX, r2, thirdW, "Save new", () -> {
			if (config().outfits.size() >= VanityConfig.MAX_OUTFITS) return;
			config().outfits.add(Outfit.capture("Outfit " + (config().outfits.size() + 1), config()));
			config().outfit = config().outfits.size() - 1;
		});
		saveNew.setTooltip(tip("Save everything you're showing now (all four slots) as a new outfit"));
		this.sync(() -> saveNew.active = config().outfits.size() < VanityConfig.MAX_OUTFITS);

		GlassButton update = this.button(Page.OUTFITS, x2, r2, thirdW, "Update", () -> {
			Outfit outfit = config().currentOutfit();
			if (outfit != null) outfit.copyFrom(config());
		});
		update.setTooltip(tip("Save your current look over the picked outfit"));

		GlassButton delete = this.button(Page.OUTFITS, x3, r2, thirdW, "Delete", () -> {
			if (config().currentOutfit() == null) return;
			config().outfits.remove(config().outfit);
			config().outfit = config().outfits.isEmpty() ? -1 : Math.min(config().outfit, config().outfits.size() - 1);
		});
		delete.setTooltip(tip("Delete the picked outfit (your current look stays)"));
		this.sync(() -> {
			update.active = config().currentOutfit() != null;
			delete.active = config().currentOutfit() != null;
		});

		this.button(Page.OUTFITS, this.panelX, r3, halfW, "◀ Previous", () -> {
			config().wearNextOutfit(-1);
			this.syncItemBox();
		});
		this.button(Page.OUTFITS, this.panelX + halfW + GAP, r3, halfW, "Next ▶", () -> {
			config().wearNextOutfit(1);
			this.syncItemBox();
		});
		GlassButton auto = this.button(Page.OUTFITS, this.panelX, r4, this.panelW, "", () ->
				config().toggleAutoOutfit(config().currentOutfit(), AutoOutfits.currentKey(this.minecraft)));
		this.sync(() -> {
			Outfit outfit = config().currentOutfit();
			String key = AutoOutfits.currentKey(this.minecraft);
			auto.active = outfit != null && key != null;
			auto.setMessage(Component.literal("Auto-wear on " + AutoOutfits.describe(key) + ": " + onOff(outfit != null && outfit.servers.contains(key))));
			auto.setTooltip(tip("Put this outfit on automatically every time you join " + AutoOutfits.describe(key)));
		});

		// ---- Hit & kill effects (Hand tab) ----
		this.dropdown(Page.EFFECTS, this.panelX, r0, halfW, "Hit", () -> enumOptions(HitEffect.values(), e -> e.label), () -> config().combat.hit.ordinal(),
				index -> config().combat.hit = HitEffect.values()[index]).setTooltip(tip("Particles where you hit something"));
		this.dropdown(Page.EFFECTS, this.panelX + halfW + GAP, r0, halfW, "Kill", () -> enumOptions(KillEffect.values(), e -> e.label), () -> config().combat.kill.ordinal(),
				index -> config().combat.kill = KillEffect.values()[index]).setTooltip(tip("What happens where something you hit dies"));
		this.colorRow(Page.EFFECTS, r1, () -> config().combat.color, color -> config().combat.color = color);
		this.slider(Page.EFFECTS, this.panelX, r2, thirdW, "Size", 0.5, 2.0, 0.1, "%.1fx", s -> config().combat.size, (s, v) -> config().combat.size = v);
		this.slider(Page.EFFECTS, x2, r2, thirdW, "Amount", 0.25, 3.0, 0.25, "%.2fx", s -> config().combat.amount, (s, v) -> config().combat.amount = v);
		GlassButton sounds = this.button(Page.EFFECTS, x3, r2, thirdW, "", () -> config().combat.sounds = !config().combat.sounds);
		sounds.setTooltip(tip("Sounds for kill effects (thunder, firework, totem...)"));
		this.sync(() -> sounds.setMessage(Component.literal("Sounds: " + onOff(config().combat.sounds))));
		this.hexBox(Page.EFFECTS, this.panelX, r3, thirdW, () -> config().combat.color, color -> config().combat.color = color);
		GlassButton players = this.button(Page.EFFECTS, x2, r3, thirdW, "", () -> config().combat.onPlayers = !config().combat.onPlayers);
		players.setTooltip(tip("Also play the effects when you hit or kill players"));
		this.sync(() -> players.setMessage(Component.literal("Players: " + onOff(config().combat.onPlayers))));
		GlassButton tryIt = this.button(Page.EFFECTS, x3, r3, thirdW, "Try it", GlassButton.Style.PRIMARY, () -> {
			this.onClose();
			CombatParticles.preview(this.minecraft);
		});
		tryIt.setTooltip(tip("Close the wardrobe and play the hit, then the kill effect, in front of you"));
		this.hintY.put(Page.EFFECTS, r4 + 2);

		// ---- Hand (held-item skins) ----
		this.dropdown(Page.HAND, this.panelX, r0, this.panelW, "Skin", "None yet", this::skinOptions, () -> config().itemSkins.isEmpty() ? 0 : this.skinIndex,
				index -> this.skinIndex = index).setTooltip(tip("Your item skins: pick one to edit it"));

		GlassButton newSkin = this.button(Page.HAND, this.panelX, r1, halfW, "New skin...", () -> this.openPicker("Pick the item to give a skin", this::newSkinFor));
		newSkin.setTooltip(tip("Pick one of your items (what you hold is first) to give it a skin. SkyBlock items match by their SkyBlock id, others by item type."));
		GlassButton deleteSkin = this.button(Page.HAND, this.panelX + halfW + GAP, r1, halfW, "Delete", () -> {
			if (this.skin() != null) config().itemSkins.remove(this.skinIndex);
		});
		this.sync(() -> deleteSkin.active = this.skin() != null);

		this.skinLookBox = this.on(Page.HAND, new GlassEditBox(this.font, this.panelX, r2, boxW, ROW, Component.literal("Look item ID")));
		this.skinLookBox.setMaxLength(128);
		this.skinLookBox.setHint(Component.literal("netherite_sword or sb:ID"));
		this.skinLookBox.setResponder(value -> {
			if (this.syncing || this.skin() == null) return;
			this.skin().setLook(value.trim());
			this.refresh();
		});
		this.sync(() -> {
			ItemSkin skin = this.skin();
			this.skinLookBox.active = skin != null;
			if (!this.skinLookBox.isFocused()) {
				this.skinLookBox.setValue(skin != null ? skin.lookItem : "");
				this.skinLookBox.moveCursorToStart(false);
			}
		});

		GlassButton skinHeld = this.button(Page.HAND, this.panelX + boxW + GAP, r2, smallW, "Held", () -> this.setSkinLook(InteractionHand.MAIN_HAND));
		skinHeld.setTooltip(tip("Use the exact item in your main hand as the look (keeps its name, skin and SkyBlock data)"));
		GlassButton skinOff = this.button(Page.HAND, this.panelX + boxW + GAP + (smallW + GAP), r2, smallW, "Pick", () -> {
			ItemSkin skin = this.skin();
			if (skin != null) this.openPicker("Pick a look for " + skin.name, stack -> skin.setLook(stack, this.minecraft.player.registryAccess()));
		});
		skinOff.setTooltip(tip("Pick the look from your items or items you grabbed with " + VanityClient.grabKeyName() + ". Keeps SkyBlock data and textures."));
		GlassButton skinClear = this.button(Page.HAND, this.panelX + boxW + GAP + 2 * (smallW + GAP), r2, smallW, "Clear", () -> {
			if (this.skin() != null) this.skin().setLook("");
		});
		skinClear.setTooltip(tip("No look: the item looks normal again"));
		this.sync(() -> {
			boolean has = this.skin() != null;
			skinHeld.active = has;
			skinOff.active = has;
			skinClear.active = has;
		});

		this.dropdown(Page.HAND, this.panelX, r3, halfW, "Glint", () -> enumOptions(GlintMode.values(), g -> g.label), () -> this.skin() != null ? this.skin().glint.ordinal() : 0, index -> {
			if (this.skin() != null) this.skin().glint = GlintMode.values()[index];
		}).setTooltip(tip("Enchantment shine. Real: shine if the item you're holding is enchanted."));
		GlassButton skinOn = this.button(Page.HAND, this.panelX + halfW + GAP, r3, halfW, "", () -> {
			if (this.skin() != null) this.skin().enabled = !this.skin().enabled;
		});
		this.sync(() -> {
			skinOn.active = this.skin() != null;
			skinOn.setMessage(Component.literal("Skin: " + onOff(this.skin() == null || this.skin().enabled)));
		});

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
		this.leaveHand();
		this.all = false;
		this.selected = slot;
		this.syncItemBox();
	}

	private void leaveHand() {
		if (this.page.isHand()) this.page = this.lastPage;
	}

	private void newSkinFor(ItemStack target) {
		if (target.isEmpty()) return;
		ItemSkin created = ItemSkins.create(target);

		// Already have a skin for this item: edit that one
		for (int i = 0; i < config().itemSkins.size(); i++) {
			ItemSkin skin = config().itemSkins.get(i);
			if (skin.item.equals(created.item) && skin.skyblockId.equals(created.skyblockId)) {
				this.skinIndex = i;
				return;
			}
		}

		config().itemSkins.add(created);
		this.skinIndex = config().itemSkins.size() - 1;
	}

	private void setSkinLook(InteractionHand hand) {
		LocalPlayer player = this.minecraft.player;
		ItemSkin skin = this.skin();
		if (player == null || skin == null) return;
		skin.setLook(player.getItemInHand(hand), player.registryAccess());
	}

	private void useHeldItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.current().setLook(player.getMainHandItem(), player.registryAccess());
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
		boolean hand = this.page.isHand();
		this.allTab.selected = this.all && !hand;
		this.handTab.selected = hand;

		for (int i = 0; i < this.slotTabs.size(); i++) {
			this.slotTabs.get(i).selected = !this.all && !hand && slots[i] == this.selected;
		}

		for (Map.Entry<Page, GlassButton> entry : this.pageTabs.entrySet()) {
			entry.getValue().selected = entry.getKey() == this.page;
			entry.getValue().visible = !hand;
		}

		for (int i = 0; i < this.handPageTabs.size(); i++) {
			this.handPageTabs.get(i).visible = hand;
			this.handPageTabs.get(i).selected = this.page == (i == 0 ? Page.HAND : Page.EFFECTS);
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

	// ---- item picker ----

	private void openPicker(String title, Consumer<ItemStack> onPick) {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.menu = null;
		this.pickerTitle = title;
		this.pickerAction = onPick;
		this.pickerScroll = 0;
		this.pickerGrabbed = config().picks(player.registryAccess());

		// What you hold, then what you wear, off hand, hotbar and the rest of your inventory (copies, so they're drawn as they really are)
		List<ItemStack> own = new ArrayList<>();
		java.util.function.Consumer<ItemStack> add = stack -> {
			if (stack.isEmpty()) return;
			for (ItemStack other : own) if (ItemStack.isSameItemSameComponents(other, stack)) return;
			own.add(stack.copyWithCount(1));
		};
		add.accept(player.getMainHandItem());
		for (CosmeticSlot slot : CosmeticSlot.values()) add.accept(player.getItemBySlot(slot.equipmentSlot));
		add.accept(player.getOffhandItem());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) add.accept(player.getInventory().getItem(i));
		this.pickerOwn = own;
	}

	private void closePicker() {
		this.pickerAction = null;
	}

	private boolean pickerOpen() {
		return this.pickerAction != null;
	}

	private int pickerX0() {
		return this.panelX - 4;
	}

	private int pickerX1() {
		return this.panelX + this.panelW + 4;
	}

	private int pickerY0() {
		return 28;
	}

	private int pickerY1() {
		return this.height - 38;
	}

	private int pickerColumns() {
		return Math.max(4, (this.pickerX1() - this.pickerX0() - 12) / CELL);
	}

	/** Rows of the picker: a label (null cells) or a row of cells; grabbed cells come first. */
	private record PickerRow(String label, List<ItemStack> cells, boolean grabbed) {
	}

	private List<PickerRow> pickerRows() {
		List<PickerRow> rows = new ArrayList<>();
		int cols = this.pickerColumns();
		rows.add(new PickerRow("Grabbed (" + VanityClient.grabKeyName() + " over any item; right-click removes)", null, true));
		if (this.pickerGrabbed.isEmpty()) rows.add(new PickerRow("Nothing yet: open a chest, menu or the Auction House, hover an item, press " + VanityClient.grabKeyName(), null, true));
		for (int i = 0; i < this.pickerGrabbed.size(); i += cols) rows.add(new PickerRow(null, this.pickerGrabbed.subList(i, Math.min(i + cols, this.pickerGrabbed.size())), true));
		rows.add(new PickerRow("Your items", null, false));
		for (int i = 0; i < this.pickerOwn.size(); i += cols) rows.add(new PickerRow(null, this.pickerOwn.subList(i, Math.min(i + cols, this.pickerOwn.size())), false));
		return rows;
	}

	private static int rowHeight(PickerRow row) {
		return row.cells() == null ? 12 : CELL;
	}

	/** The picker cell under the mouse: {grabbed? 1 : 0, index in its list}, or null. */
	private int[] pickerCellAt(double mouseX, double mouseY) {
		int x = this.pickerX0() + 6;
		int y = this.pickerY0() + 20;
		int bottom = this.pickerY1() - 4;
		List<PickerRow> rows = this.pickerRows();
		int cols = this.pickerColumns();
		int grabbedRow = 0;
		int ownRow = 0;

		for (int r = 0; r < rows.size(); r++) {
			PickerRow row = rows.get(r);
			if (row.cells() == null) {
				if (r >= this.pickerScroll) y += rowHeight(row);
				continue;
			}

			int rowIndex = row.grabbed() ? grabbedRow++ : ownRow++;
			if (r < this.pickerScroll) continue;
			if (y + CELL > bottom) break;

			if (mouseY >= y && mouseY < y + CELL && mouseX >= x) {
				int col = (int) ((mouseX - x) / CELL);
				if (col < row.cells().size()) return new int[] {row.grabbed() ? 1 : 0, rowIndex * cols + col};
			}

			y += CELL;
		}

		return null;
	}

	private ItemStack pickerStack(int[] cell) {
		List<ItemStack> list = cell[0] == 1 ? this.pickerGrabbed : this.pickerOwn;
		return cell[1] < list.size() ? list.get(cell[1]) : ItemStack.EMPTY;
	}

	private void extractPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		g.nextStratum();
		g.fill(0, 0, this.width, this.height, 0x66000000);
		int x0 = this.pickerX0();
		int y0 = this.pickerY0();
		int x1 = this.pickerX1();
		int y1 = this.pickerY1();
		Glass.fill(g, x0, y0, x1 - x0, y1 - y0, 0xFF121720);
		Glass.border(g, x0, y0, x1 - x0, y1 - y0, Glass.alpha(Glass.ACCENT, 0x90));
		g.text(this.font, Component.literal(this.pickerTitle).withStyle(ChatFormatting.BOLD), x0 + 6, y0 + 6, Glass.TEXT, false);
		boolean overClose = mouseX >= x1 - 16 && mouseX < x1 - 2 && mouseY >= y0 + 2 && mouseY < y0 + 16;
		g.text(this.font, Component.literal("✕"), x1 - 12, y0 + 6, overClose ? Glass.ACCENT : Glass.TEXT_DIM, false);

		int x = x0 + 6;
		int y = y0 + 20;
		int bottom = y1 - 4;
		List<PickerRow> rows = this.pickerRows();
		int[] hovered = this.pickerCellAt(mouseX, mouseY);
		int grabbedRow = 0;
		int ownRow = 0;
		int cols = this.pickerColumns();
		boolean more = false;

		for (int r = 0; r < rows.size(); r++) {
			PickerRow row = rows.get(r);
			int rowIndex = row.cells() == null ? -1 : row.grabbed() ? grabbedRow++ : ownRow++;
			if (r < this.pickerScroll) continue;

			if (y + rowHeight(row) > bottom) {
				more = true;
				break;
			}

			if (row.cells() == null) {
				g.text(this.font, Component.literal(this.font.plainSubstrByWidth(row.label(), x1 - x - 6)), x, y + 2, row.label().startsWith("Nothing") ? GRAY : Glass.TEXT_DIM, false);
				y += 12;
				continue;
			}

			for (int c = 0; c < row.cells().size(); c++) {
				int cx = x + c * CELL;
				boolean isHovered = hovered != null && hovered[0] == (row.grabbed() ? 1 : 0) && hovered[1] == rowIndex * cols + c;
				g.fill(cx, y, cx + CELL - 1, y + CELL - 1, isHovered ? Glass.alpha(Glass.ACCENT, 0x55) : 0x1EFFFFFF);
				g.item(row.cells().get(c), cx + 1, y + 1);
			}

			y += CELL;
		}

		if (more || this.pickerScroll > 0) g.text(this.font, Component.literal(more ? "scroll for more ▾" : "▴"), x1 - 70, y1 - 12, GRAY, false);
		if (hovered != null) g.setTooltipForNextFrame(this.font, this.pickerStack(hovered), mouseX, mouseY);
	}

	private boolean pickerClicked(MouseButtonEvent event) {
		double mx = event.x();
		double my = event.y();
		boolean inside = mx >= this.pickerX0() && mx < this.pickerX1() && my >= this.pickerY0() && my < this.pickerY1();
		boolean onClose = mx >= this.pickerX1() - 16 && mx < this.pickerX1() - 2 && my >= this.pickerY0() + 2 && my < this.pickerY0() + 16;

		if (!inside || onClose) {
			this.closePicker();
			return true;
		}

		int[] cell = this.pickerCellAt(mx, my);
		if (cell == null) return true;
		ItemStack stack = this.pickerStack(cell);

		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && cell[0] == 1) {
			config().removePick(stack, this.minecraft.player.registryAccess());
			this.pickerGrabbed = config().picks(this.minecraft.player.registryAccess());
			return true;
		}

		Consumer<ItemStack> action = this.pickerAction;
		this.closePicker();
		action.accept(stack.copy());
		this.changed();
		this.refresh();
		return true;
	}

	// ---- test hooks (GUI-scaled coordinates) ----

	public boolean pickerShown() {
		return this.pickerOpen();
	}

	/** Centre of the first picker cell whose item id or SkyBlock id is {@code id}, or null. */
	public double[] pickerCellCenter(String id) {
		if (!this.pickerOpen()) return null;

		for (int scroll = 0; scroll < this.pickerRows().size(); scroll++) {
			this.pickerScroll = scroll;

			for (int y = this.pickerY0() + 20; y < this.pickerY1(); y += 2) {
				for (int x = this.pickerX0() + 6 + CELL / 2; x < this.pickerX1(); x += CELL) {
					int[] cell = this.pickerCellAt(x, y + CELL / 2.0);
					if (cell == null) continue;
					ItemStack stack = this.pickerStack(cell);
					String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
					if (id.equals(itemId) || id.equals(ItemSkins.skyblockId(stack))) return new double[] {x, y + CELL / 2.0};
				}
			}
		}

		this.pickerScroll = 0;
		return null;
	}

	/** Centre of the visible widget whose label starts with {@code prefix}, or null. */
	public double[] widgetCenter(String prefix) {
		for (var child : this.children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible && widget.getMessage().getString().startsWith(prefix)) {
				return new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
			}
		}

		return null;
	}

	/** Centre of the open dropdown's row labelled {@code label} (scrolling it into view), or null. */
	public double[] menuRowCenter(String label) {
		if (this.menu == null) return null;

		for (int i = 0; i < this.menu.options().size(); i++) {
			if (!this.menu.options().get(i).label().equals(label)) continue;
			int rows = this.menuRows();
			if (i < this.menuScroll || i >= this.menuScroll + rows) this.menuScroll = this.clampScroll(i - rows / 2);
			int rowY = this.menuTop() + 2 + (i - this.menuScroll) * MENU_ROW;
			return new double[] {this.menu.x() + this.menu.w() / 2.0, rowY + MENU_ROW / 2.0};
		}

		return null;
	}

	public boolean menuOpen() {
		return this.menu != null;
	}

	// ---- dropdown menu ----

	private int menuRows() {
		int fit = Math.max(3, (this.menuSpace() - 4) / MENU_ROW);
		return Math.min(Math.min(MENU_MAX_ROWS, fit), this.menu.options().size());
	}

	/** Opens below its button when the whole list fits there (or there's more room below than above). */
	private boolean menuBelow() {
		int below = this.height - 4 - this.menu.y();
		int above = this.menu.anchorY() - 5;
		int full = Math.min(MENU_MAX_ROWS, this.menu.options().size()) * MENU_ROW + 4;
		return below >= full || below >= above;
	}

	private int menuSpace() {
		return this.menuBelow() ? this.height - 4 - this.menu.y() : this.menu.anchorY() - 5;
	}

	/** Top of the open menu, always kept on screen. */
	private int menuTop() {
		int h = this.menuRows() * MENU_ROW + 4;
		return this.menuBelow() ? this.menu.y() : Math.max(4, this.menu.anchorY() - 1 - h);
	}

	private int clampScroll(int scroll) {
		return Math.max(0, Math.min(scroll, this.menu.options().size() - this.menuRows()));
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
		this.menuScroll = this.clampScroll(this.menuScroll);
		int rows = this.menuRows();
		int top = this.menuTop();
		int h = rows * MENU_ROW + 4;
		g.nextStratum();
		Glass.fill(g, m.x(), top, m.w(), h, 0xFF121720);
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
		if (this.pickerOpen()) return this.pickerClicked(event);

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
		if (this.pickerOpen()) {
			this.pickerScroll = Math.max(0, Math.min(this.pickerRows().size() - 1, this.pickerScroll - (int) Math.signum(scrollY)));
			return true;
		}

		if (this.menu != null) {
			this.menuScroll = this.clampScroll(this.menuScroll - (int) Math.signum(scrollY));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.pickerOpen()) {
			if (event.isEscape()) this.closePicker();
			return true;
		}

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
		boolean overMenu = this.inMenu(mouseX, mouseY) || this.pickerOpen();
		super.extractRenderState(graphics, overMenu ? -1 : mouseX, overMenu ? -1 : mouseY, a);

		switch (this.page) {
			case LOOK -> {
				this.lookStatus(graphics, this.current());
				if (!this.all) this.hint(graphics, "SkyBlock: type sb:ID. Grab any item: " + VanityClient.grabKeyName());
			}
			case STYLE -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Dye works on leather looks. Also styles your real armor.");
			case TRIM -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Also on inventory icons. Texture packs apply.");
			case DESIGN -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Up to 3 patterns in any color, over your armor.");
			case RUNE -> this.hint(graphics, this.all ? "Editing all four pieces at once." : "Particles only you see. Press F5 to look at them.");
			case OUTFITS -> this.hint(graphics, "Press " + VanityClient.nextOutfitKeyName() + " in game to switch outfits.");
			case HAND -> this.handStatus(graphics);
			case EFFECTS -> this.hint(graphics, "Plays when you hit something, and where it dies if you hit it. Only you see it. Color is for Sparks and Firework.");
		}

		LocalPlayer player = this.minecraft.player;

		if (player != null) {
			int size = (int) Math.min((y1 - y0) / 2.6F, (x1 - x0) / 1.4F);
			this.extractPreview(graphics, x0, y0, x1, y1, size, mouseX, mouseY, player);
		}

		if (this.menu != null) this.extractMenu(graphics, mouseX, mouseY);
		if (this.pickerOpen()) this.extractPicker(graphics, mouseX, mouseY);
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

	/** Hand tab: a title where the page tabs are, what the picked skin does, and how to make one. */
	private void handStatus(GuiGraphicsExtractor graphics) {
		ItemSkin skin = this.skin();
		int y = this.contentY + 4 * (ROW + GAP) + 6;

		if (skin == null) {
			this.hint(graphics, "Hold an item and press New from held. Then type a look id, or hold the look item and press Held.");
			return;
		}

		String target = skin.name + (skin.skyblockId.isEmpty() ? "" : " (" + skin.skyblockId + ")");
		ItemStack look = skin.look();
		Component text = look.isEmpty()
				? Component.literal(target + ": pick a look")
				: Component.literal(target + " looks like ").append(look.getHoverName());
		graphics.text(this.font, this.fit(text), this.panelX, y, look.isEmpty() ? GRAY : GREEN);
		this.hint(graphics, "Works in first and third person, the hotbar and your inventory.");
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
			text = Component.literal("Unknown item id (SkyBlock ids: sb:ID)");
			color = RED;
		} else if (s.itemStack == null && VanityConfig.resolveItem(s.item).isEmpty() && VanityConfig.skyblockIdOf(s.item) != null) {
			text = Component.literal("").append(stack.getHoverName()).append(Component.literal(" · by SkyBlock id"));
			color = GREEN;
		} else if (VanityCosmetics.usesVanillaLook(this.selected, stack)) {
			text = Component.literal("Looks like ").append(stack.getHoverName());
			color = GREEN;
		} else {
			text = Component.literal(this.selected.label + ": ").append(stack.getHoverName());
			color = GREEN;
		}

		graphics.text(this.font, this.fit(text), this.panelX, y, color);
	}

	/** Text cut with "…" to fit the panel width. */
	private net.minecraft.util.FormattedCharSequence fit(Component text) {
		if (this.font.width(text) <= this.panelW) return text.getVisualOrderText();
		return net.minecraft.locale.Language.getInstance().getVisualOrder(net.minecraft.network.chat.FormattedText.composite(
				this.font.substrByWidth(text, this.panelW - this.font.width("…")), net.minecraft.network.chat.FormattedText.of("…")));
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
