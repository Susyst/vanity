package dev.susyst.vanity.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.block.AbstractSkullBlock;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.FitMode;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;

/** Fills in {@link VanityRenderData} while the player's render state is being extracted. */
public final class VanityCosmetics {
	private VanityCosmetics() {
	}

	/** Cosmetics are client-side and only shown on your own player. */
	public static boolean appliesTo(LivingEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && (Object) entity == minecraft.player;
	}

	/**
	 * True if the item is real armor for this slot (netherite chestplate in Chest, etc.). Those are drawn by the
	 * vanilla armor renderer, so they look exactly like worn armor.
	 */
	public static boolean isArmorLook(CosmeticSlot slot, ItemStack stack) {
		return !stack.isEmpty() && HumanoidArmorLayer.shouldRender(stack, slot.equipmentSlot);
	}

	/** True if the item is a mob or player head (like most SkyBlock helmets) in the Head slot. */
	public static boolean isWornHead(CosmeticSlot slot, ItemStack stack) {
		return slot == CosmeticSlot.HEAD
				&& stack.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof AbstractSkullBlock;
	}

	/** True if the item is drawn exactly the way the game draws it when worn, so fitting doesn't apply. */
	public static boolean usesVanillaLook(CosmeticSlot slot, ItemStack stack) {
		return isArmorLook(slot, stack) || isWornHead(slot, stack);
	}

	public static void extract(VanityRenderData data, AvatarRenderState state, LivingEntity entity, ItemModelResolver resolver) {
		VanityConfig config = VanityClient.config();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			int i = slot.ordinal();
			SlotSettings settings = config.get(slot);
			ItemStack stack = config.enabled ? settings.stack() : ItemStack.EMPTY;

			if (stack.isEmpty()) {
				data.active[i] = false;
				data.items[i].clear();
				continue;
			}

			if (isWornHead(slot, stack) && stack.getItem() instanceof BlockItem blockItem
					&& blockItem.getBlock() instanceof AbstractSkullBlock skull) {
				// Player and mob heads: shown like a worn head, with the head's own skin.
				data.active[i] = false;
				data.items[i].clear();
				state.headEquipment = ItemStack.EMPTY;
				state.headItem.clear();
				state.wornHeadType = skull.getType();
				state.wornHeadProfile = stack.get(DataComponents.PROFILE);
				continue;
			}

			if (isArmorLook(slot, stack)) {
				// Swap the armor the game draws: wearing diamond, shown as netherite.
				data.active[i] = false;
				data.items[i].clear();
				ItemStack real = equipment(slot, state);
				setEquipment(slot, state, withLookOf(stack, real));

				if (slot == CosmeticSlot.HEAD) {
					state.headItem.clear();
					state.wornHeadType = null;
				}

				continue;
			}

			// Any other item or block: draw it fitted to the armor shape.
			ItemDisplayContext context = settings.mode == FitMode.FREE ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE;
			resolver.updateForLiving(data.items[i], stack, context, entity);
			data.settings[i] = settings.copy();
			data.active[i] = !data.items[i].isEmpty();

			if (data.active[i] && settings.hideArmor) {
				hideRealArmor(slot, state);
			}
		}
	}

	/**
	 * The slot whose look applies to this exact item: the item must be the one you are wearing in that slot (not a copy
	 * elsewhere in the inventory) and the slot must have a look. Null otherwise.
	 */
	public static CosmeticSlot wornSlotWithLook(ItemStack stack) {
		if (stack.isEmpty()) return null;
		VanityConfig config = VanityClient.config();
		if (!config.enabled) return null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return null;

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (player.getItemBySlot(slot.equipmentSlot) == stack) {
				return config.get(slot).stack().isEmpty() ? null : slot;
			}
		}

		return null;
	}

	/** The icon to draw in the inventory for this item: its look if you're wearing it in a slot with a look. */
	public static ItemStack inventoryIcon(ItemStack stack) {
		if (!VanityClient.config().showInInventory) return stack;
		CosmeticSlot slot = wornSlotWithLook(stack);
		if (slot == null) return stack;

		ItemStack look = VanityClient.config().get(slot).stack();
		return isArmorLook(slot, look) ? withLookOf(look, stack) : look;
	}

	/** Keeps the enchantment shine and armor trim of the armor you're really wearing. */
	private static ItemStack withLookOf(ItemStack cosmetic, ItemStack real) {
		if (real.isEmpty()) return cosmetic;

		ArmorTrim trim = real.get(DataComponents.TRIM);
		boolean glint = real.hasFoil();
		if (trim == null && !glint) return cosmetic;

		ItemStack copy = cosmetic.copy();
		if (trim != null) copy.set(DataComponents.TRIM, trim);
		if (glint) copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return copy;
	}

	private static ItemStack equipment(CosmeticSlot slot, AvatarRenderState state) {
		return switch (slot) {
			case HEAD -> state.headEquipment;
			case CHEST -> state.chestEquipment;
			case LEGS -> state.legsEquipment;
			case FEET -> state.feetEquipment;
		};
	}

	private static void setEquipment(CosmeticSlot slot, AvatarRenderState state, ItemStack stack) {
		switch (slot) {
			case HEAD -> state.headEquipment = stack;
			case CHEST -> state.chestEquipment = stack;
			case LEGS -> state.legsEquipment = stack;
			case FEET -> state.feetEquipment = stack;
		}
	}

	private static void hideRealArmor(CosmeticSlot slot, AvatarRenderState state) {
		switch (slot) {
			case HEAD -> {
				state.headEquipment = ItemStack.EMPTY;
				state.headItem.clear();
				state.wornHeadType = null;
			}
			case CHEST -> {
				// Keep elytra wings visible; they are not chest armor.
				if (state.chestEquipment.getItem() != Items.ELYTRA) {
					state.chestEquipment = ItemStack.EMPTY;
				}
			}
			case LEGS -> state.legsEquipment = ItemStack.EMPTY;
			case FEET -> state.feetEquipment = ItemStack.EMPTY;
		}
	}
}
