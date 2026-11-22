package dev.susyst.vanity.config;

import java.util.Objects;

import com.google.gson.JsonElement;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** A held-item skin: while you hold an item matching {@link #item} (and {@link #skyblockId}), it looks like the look item. */
public final class ItemSkin {
	public boolean enabled = true;
	/** Item id of the item you hold, e.g. "minecraft:diamond_sword". */
	public String item = "";
	/** SkyBlock item id (from the item's custom data) the held item must have, or empty to match any item of that type. */
	public String skyblockId = "";
	/** Name of the held item when the skin was made, for the list. */
	public String name = "";
	/** Item id of the look. */
	public String lookItem = "";
	/** The exact look item with all its data, or null for a plain one from {@link #lookItem}. */
	public JsonElement lookStack;
	public GlintMode glint = GlintMode.AUTO;

	private transient String resolvedItem;
	private transient JsonElement resolvedJson;
	private transient HolderLookup.Provider resolvedRegistries;
	private transient ItemStack resolvedStack = ItemStack.EMPTY;

	/** The look to show, or EMPTY if none is set or the id is unknown. */
	public ItemStack look() {
		HolderLookup.Provider registries = this.lookStack != null ? VanityConfig.registries() : null;

		if (!Objects.equals(this.resolvedItem, this.lookItem) || this.resolvedJson != this.lookStack || this.resolvedRegistries != registries) {
			ItemStack stack = ItemStack.EMPTY;
			if (registries != null) stack = VanityConfig.decodeStack(this.lookStack, registries);
			// A typed SkyBlock id is made on the item type you hold, like the real one
			if (stack.isEmpty()) stack = VanityConfig.resolveLook(this.lookItem, VanityConfig.resolveItem(this.item).getItem());
			this.resolvedStack = stack;
			this.resolvedItem = this.lookItem;
			this.resolvedJson = this.lookStack;
			this.resolvedRegistries = registries;
		}

		return this.resolvedStack;
	}

	public void setLook(ItemStack stack, HolderLookup.Provider registries) {
		if (stack.isEmpty()) {
			this.setLook("");
			return;
		}

		ItemStack single = stack.copyWithCount(1);
		this.lookItem = BuiltInRegistries.ITEM.getKey(single.getItem()).toString();
		this.lookStack = VanityConfig.encodeStack(single, registries);
	}

	public void setLook(String itemId) {
		this.lookItem = itemId;
		this.lookStack = null;
	}

	void sanitize() {
		if (this.item == null) this.item = "";
		if (this.skyblockId == null) this.skyblockId = "";
		if (this.name == null) this.name = "";
		if (this.lookItem == null) this.lookItem = "";
		if (this.lookStack != null && this.lookStack.isJsonNull()) this.lookStack = null;
		if (this.glint == null) this.glint = GlintMode.AUTO;
	}
}
