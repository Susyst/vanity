package dev.susyst.vanity.render;

import java.util.Arrays;

import net.minecraft.client.renderer.item.ItemStackRenderState;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;

/** Cosmetic models and settings attached to a player's render state for one frame. */
public final class VanityRenderData {
	public final ItemStackRenderState[] items = new ItemStackRenderState[CosmeticSlot.values().length];
	public final SlotSettings[] settings = new SlotSettings[CosmeticSlot.values().length];
	public final boolean[] active = new boolean[CosmeticSlot.values().length];

	public VanityRenderData() {
		for (int i = 0; i < this.items.length; i++) {
			this.items[i] = new ItemStackRenderState();
			this.settings[i] = new SlotSettings();
		}
	}

	public void clear() {
		Arrays.fill(this.active, false);

		for (ItemStackRenderState item : this.items) {
			item.clear();
		}
	}
}
