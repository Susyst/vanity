package dev.susyst.vanity.config;

import java.util.Objects;

import com.google.gson.JsonElement;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Settings for one cosmetic slot. Public fields are saved to config/vanity.json by Gson. */
public final class SlotSettings {
	/** Item id, e.g. "minecraft:netherite_chestplate". Empty means no cosmetic in this slot. */
	public String item = "";
	/**
	 * The exact item, with all its data (custom name, dye color, player-head skin, SkyBlock id...), saved when the
	 * look was taken from a held or worn item. Null means a plain item made from {@link #item}.
	 */
	public JsonElement itemStack;
	/** Show nothing in this slot: hides your real armor and any look. */
	public boolean invisible;
	/** Only show the look while you're actually wearing something in this slot. */
	public boolean onlyWhenWorn = true;
	/** Enchantment shine on armor looks. */
	public GlintMode glint = GlintMode.AUTO;
	/** Old 0.7 setting, read once and turned into {@link #trim}. */
	public Boolean keepTrim;
	/** Which trim the look shows. */
	public TrimMode trim = TrimMode.REAL;
	/** Vanilla trim pattern and material used when {@link #trim} is PICK. */
	public String trimPattern = "minecraft:sentry";
	public String trimMaterial = "minecraft:gold";
	/** Custom trim design: up to three trim patterns in any color, drawn on top of the armor. */
	public TrimLayer[] design = newDesign();
	/** Recolor dyeable looks (leather) with {@link #dyeColor}. */
	public boolean customDye;
	/** RGB dye color, default is undyed leather. */
	public int dyeColor = 0xA06540;
	/** Armor opacity, 0.1 (ghostly) to 1 (solid). */
	public float opacity = 1.0F;
	/** How much the armor glows in the dark, 0 to 1. */
	public float glow;
	/** Old 0.9 on/off setting, read once and turned into {@link #trimGlowAmount}. */
	public Boolean trimGlow;
	/** How much trims and custom designs glow, 0 to 1. Kept soft: even 1 is a gentle glow. */
	public float trimGlowAmount;
	/** Particle rune worn on this piece. */
	public Rune rune = new Rune();
	public FitMode mode = FitMode.STRETCH;
	/** Hide the real armor (or head item) in this slot while a cosmetic is shown. */
	public boolean hideArmor = true;
	/** Offset in pixels (1/16 of a block). +Y is up, +Z is forward. */
	public float offsetX;
	public float offsetY;
	public float offsetZ;
	/** Rotation in degrees, applied before fitting, so a rotated model still fills the armor shape. */
	public float rotX;
	public float rotY;
	public float rotZ;
	/** Extra size multiplier on top of the fit. */
	public float scale = 1.0F;

	private transient String resolvedItem;
	private transient JsonElement resolvedJson;
	private transient HolderLookup.Provider resolvedRegistries;
	private transient ItemStack resolvedStack = ItemStack.EMPTY;

	/** The item stack to show, or {@link ItemStack#EMPTY} if the slot is empty or the id is unknown. */
	public ItemStack stack() {
		// Exact items can contain data that needs the world's registries (trims, enchantments), so they are decoded
		// once a world is loaded. Until then the plain item id is used.
		HolderLookup.Provider registries = this.itemStack != null ? VanityConfig.registries() : null;

		if (!Objects.equals(this.resolvedItem, this.item) || this.resolvedJson != this.itemStack || this.resolvedRegistries != registries) {
			ItemStack stack = ItemStack.EMPTY;
			if (registries != null) stack = VanityConfig.decodeStack(this.itemStack, registries);
			if (stack.isEmpty()) stack = VanityConfig.resolveItem(this.item);

			this.resolvedStack = stack;
			this.resolvedItem = this.item;
			this.resolvedJson = this.itemStack;
			this.resolvedRegistries = registries;
		}

		return this.resolvedStack;
	}

	/** Uses an existing item (held or worn) as the look, keeping all of its data. */
	public void setLook(ItemStack stack, HolderLookup.Provider registries) {
		if (stack.isEmpty()) {
			this.clearLook();
			return;
		}

		ItemStack single = stack.copyWithCount(1);
		this.item = BuiltInRegistries.ITEM.getKey(single.getItem()).toString();
		this.itemStack = VanityConfig.encodeStack(single, registries);
	}

	/** Uses a plain item by id. */
	public void setLook(String itemId) {
		this.item = itemId;
		this.itemStack = null;
	}

	public void clearLook() {
		this.setLook("");
	}

	/** True if this slot shows an exact saved item rather than a plain one. */
	public boolean isExactItem() {
		return this.itemStack != null;
	}

	public static final int DESIGN_LAYERS = 3;

	private static TrimLayer[] newDesign() {
		TrimLayer[] layers = new TrimLayer[DESIGN_LAYERS];
		for (int i = 0; i < layers.length; i++) layers[i] = new TrimLayer();
		return layers;
	}

	/** True if glint, dye or trim change anything, so even your real armor gets restyled. */
	public boolean hasStyleOverrides() {
		return this.glint != GlintMode.AUTO || this.customDye || this.trim != TrimMode.REAL;
	}

	/** True if any custom trim design layer is on. */
	public boolean hasDesign() {
		for (TrimLayer layer : this.design) {
			if (layer.enabled) return true;
		}

		return false;
	}

	/** Resets glint, dye, opacity and glow. */
	public void resetStyle() {
		this.glint = GlintMode.AUTO;
		this.customDye = false;
		this.dyeColor = 0xA06540;
		this.opacity = 1.0F;
		this.glow = 0.0F;
		this.trimGlowAmount = 0.0F;
	}

	/** True if this slot's armor must be drawn by Vanity (see-through or glowing). */
	public boolean hasCustomArmorRendering() {
		return this.opacity < 0.995F || this.glow > 0.005F || this.trimGlowAmount > 0.005F;
	}

	public void resetTrim() {
		this.trim = TrimMode.REAL;
		this.trimPattern = "minecraft:sentry";
		this.trimMaterial = "minecraft:gold";
	}

	public void resetDesign() {
		this.design = newDesign();
	}

	/** Resets glint, dye, trim and design. */
	public void resetArmorStyle() {
		this.resetStyle();
		this.resetTrim();
		this.resetDesign();
	}

	public void copyStyleFrom(SlotSettings other) {
		this.glint = other.glint;
		this.customDye = other.customDye;
		this.dyeColor = other.dyeColor;
		this.opacity = other.opacity;
		this.glow = other.glow;
		this.trimGlowAmount = other.trimGlowAmount;
	}

	public void copyTrimFrom(SlotSettings other) {
		this.trim = other.trim;
		this.trimPattern = other.trimPattern;
		this.trimMaterial = other.trimMaterial;
	}

	public void copyDesignFrom(SlotSettings other) {
		for (int i = 0; i < DESIGN_LAYERS; i++) this.design[i] = other.design[i].copy();
	}

	public void resetTransform() {
		this.offsetX = 0.0F;
		this.offsetY = 0.0F;
		this.offsetZ = 0.0F;
		this.rotX = 0.0F;
		this.rotY = 0.0F;
		this.rotZ = 0.0F;
		this.scale = 1.0F;
	}

	/** Copies the saved values (not the cached stack) for use by the renderer. */
	public SlotSettings copy() {
		SlotSettings copy = new SlotSettings();
		copy.item = this.item;
		copy.itemStack = this.itemStack;
		copy.invisible = this.invisible;
		copy.onlyWhenWorn = this.onlyWhenWorn;
		copy.glint = this.glint;
		copy.trim = this.trim;
		copy.trimPattern = this.trimPattern;
		copy.trimMaterial = this.trimMaterial;
		copy.resetDesign();
		copy.copyDesignFrom(this);
		copy.customDye = this.customDye;
		copy.dyeColor = this.dyeColor;
		copy.opacity = this.opacity;
		copy.glow = this.glow;
		copy.trimGlowAmount = this.trimGlowAmount;
		copy.rune = this.rune.copy();
		copy.mode = this.mode;
		copy.hideArmor = this.hideArmor;
		copy.offsetX = this.offsetX;
		copy.offsetY = this.offsetY;
		copy.offsetZ = this.offsetZ;
		copy.rotX = this.rotX;
		copy.rotY = this.rotY;
		copy.rotZ = this.rotZ;
		copy.scale = this.scale;
		return copy;
	}

	/** Repairs values that may be missing or invalid in a hand-edited config file. */
	void sanitize() {
		if (this.item == null) this.item = "";
		if (this.itemStack != null && this.itemStack.isJsonNull()) this.itemStack = null;
		if (this.mode == null) this.mode = FitMode.STRETCH;
		if (this.glint == null) this.glint = GlintMode.AUTO;
		if (this.trim == null) this.trim = TrimMode.REAL;
		if (Boolean.FALSE.equals(this.keepTrim) && this.trim == TrimMode.REAL) this.trim = TrimMode.NONE;
		this.keepTrim = null;
		if (this.trimPattern == null || this.trimPattern.isBlank()) this.trimPattern = "minecraft:sentry";
		if (this.trimMaterial == null || this.trimMaterial.isBlank()) this.trimMaterial = "minecraft:gold";

		if (this.design == null || this.design.length != DESIGN_LAYERS) {
			TrimLayer[] fixed = newDesign();
			for (int i = 0; this.design != null && i < Math.min(this.design.length, DESIGN_LAYERS); i++) {
				if (this.design[i] != null) fixed[i] = this.design[i];
			}
			this.design = fixed;
		}

		for (int i = 0; i < DESIGN_LAYERS; i++) {
			if (this.design[i] == null) this.design[i] = new TrimLayer();
			this.design[i].sanitize();
		}
		this.dyeColor &= 0xFFFFFF;
		if (!Float.isFinite(this.opacity)) this.opacity = 1.0F;
		this.opacity = Math.max(0.1F, Math.min(1.0F, this.opacity));
		if (!Float.isFinite(this.glow)) this.glow = 0.0F;
		this.glow = Math.max(0.0F, Math.min(1.0F, this.glow));
		if (Boolean.TRUE.equals(this.trimGlow) && this.trimGlowAmount <= 0.0F) this.trimGlowAmount = 0.4F;
		this.trimGlow = null;
		if (!Float.isFinite(this.trimGlowAmount)) this.trimGlowAmount = 0.0F;
		this.trimGlowAmount = Math.max(0.0F, Math.min(1.0F, this.trimGlowAmount));
		if (this.rune == null) this.rune = new Rune();
		this.rune.sanitize();
		if (!Float.isFinite(this.scale) || this.scale <= 0.0F) this.scale = 1.0F;
		if (!Float.isFinite(this.offsetX)) this.offsetX = 0.0F;
		if (!Float.isFinite(this.offsetY)) this.offsetY = 0.0F;
		if (!Float.isFinite(this.offsetZ)) this.offsetZ = 0.0F;
		if (!Float.isFinite(this.rotX)) this.rotX = 0.0F;
		if (!Float.isFinite(this.rotY)) this.rotY = 0.0F;
		if (!Float.isFinite(this.rotZ)) this.rotZ = 0.0F;
	}
}
