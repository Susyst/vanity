package dev.susyst.vanity.render;

import java.util.Arrays;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;

/** Cosmetic models and settings attached to a player's render state for one frame. */
public final class VanityRenderData {
	public final ItemStackRenderState[] items = new ItemStackRenderState[CosmeticSlot.values().length];
	public final SlotSettings[] settings = new SlotSettings[CosmeticSlot.values().length];
	public final boolean[] active = new boolean[CosmeticSlot.values().length];
	/** Custom trim design layers to draw over each slot's armor, or empty. */
	public final TrimLayer[][] designs = new TrimLayer[CosmeticSlot.values().length][0];
	/** Armor Vanity draws itself (see-through or glowing), or EMPTY. */
	public final ItemStack[] armor = new ItemStack[CosmeticSlot.values().length];
	public final float[] opacity = new float[CosmeticSlot.values().length];
	public final float[] glow = new float[CosmeticSlot.values().length];
	public final float[] trimGlow = new float[CosmeticSlot.values().length];
	/** RGB color the glow is tinted with right now (rainbow glow), or -1 for the armor's own colors. */
	public final int[] glowTint = new int[CosmeticSlot.values().length];

	public VanityRenderData() {
		Arrays.fill(this.armor, ItemStack.EMPTY);
		Arrays.fill(this.opacity, 1.0F);
		Arrays.fill(this.glowTint, -1);

		for (int i = 0; i < this.items.length; i++) {
			this.items[i] = new ItemStackRenderState();
			this.settings[i] = new SlotSettings();
		}
	}

	public void clear() {
		Arrays.fill(this.active, false);
		Arrays.fill(this.designs, new TrimLayer[0]);
		Arrays.fill(this.armor, ItemStack.EMPTY);
		Arrays.fill(this.opacity, 1.0F);
		Arrays.fill(this.glow, 0.0F);
		Arrays.fill(this.trimGlow, 0.0F);
		Arrays.fill(this.glowTint, -1);

		for (ItemStackRenderState item : this.items) {
			item.clear();
		}
	}
}
