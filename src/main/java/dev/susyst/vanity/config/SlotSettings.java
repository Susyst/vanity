package dev.susyst.vanity.config;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

/** Settings for one cosmetic slot. Public fields are saved to config/vanity.json by Gson. */
public final class SlotSettings {
	/** Item id, e.g. "minecraft:glass". Empty means no cosmetic in this slot. */
	public String item = "";
	public FitMode mode = FitMode.STRETCH;
	/** Hide the real armor (or head item) in this slot while a cosmetic is shown. */
	public boolean hideArmor = true;
	/** Offset in pixels (1/16 of a block). +Y is up. */
	public float offsetX;
	public float offsetY;
	public float offsetZ;
	/** Rotation in degrees, applied before fitting, so a rotated model still fills the armor shape. */
	public float rotX;
	public float rotY;
	public float rotZ;
	/** Extra size multiplier on top of the fit. */
	public float scale = 1.0F;

	private transient String resolvedFor;
	private transient ItemStack resolvedStack = ItemStack.EMPTY;

	/** The item stack for {@link #item}, or {@link ItemStack#EMPTY} if the id is blank or unknown. */
	public ItemStack stack() {
		if (!Objects.equals(this.resolvedFor, this.item)) {
			this.resolvedStack = VanityConfig.resolveItem(this.item);
			this.resolvedFor = this.item;
		}

		return this.resolvedStack;
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
