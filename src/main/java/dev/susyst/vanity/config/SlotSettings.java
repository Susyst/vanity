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
		if (!Float.isFinite(this.scale) || this.scale <= 0.0F) this.scale = 1.0F;
		if (!Float.isFinite(this.offsetX)) this.offsetX = 0.0F;
		if (!Float.isFinite(this.offsetY)) this.offsetY = 0.0F;
		if (!Float.isFinite(this.offsetZ)) this.offsetZ = 0.0F;
		if (!Float.isFinite(this.rotX)) this.rotX = 0.0F;
		if (!Float.isFinite(this.rotY)) this.rotY = 0.0F;
		if (!Float.isFinite(this.rotZ)) this.rotZ = 0.0F;
	}
}
