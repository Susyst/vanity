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
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.util.Util;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.block.AbstractSkullBlock;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.FitMode;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.Trims;
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
	 * vanilla armor renderer, so they look exactly like worn armor, texture packs included.
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

	/** True for leather-style armor that can be dyed. */
	public static boolean isDyeable(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.assetId().filter(id -> id.equals(EquipmentAssets.LEATHER)).isPresent();
	}

	public static void extract(VanityRenderData data, AvatarRenderState state, LivingEntity entity, ItemModelResolver resolver) {
		VanityConfig config = VanityClient.config();
		long now = Util.getMillis();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			int i = slot.ordinal();
			data.active[i] = false;
			data.items[i].clear();
			data.designs[i] = NO_DESIGN;
			data.armor[i] = ItemStack.EMPTY;
			data.opacity[i] = 1.0F;
			data.glow[i] = 0.0F;
			data.trimGlow[i] = 0.0F;
			data.glowTint[i] = -1;
			if (!config.enabled) continue;

			SlotSettings settings = config.get(slot);
			extractSlot(slot, data, state, entity, resolver, settings);

			// Whatever armor ends up shown in this slot can get a custom design, glow and see-through look
			ItemStack shown = equipment(slot, state);
			boolean armorShown = !settings.invisible && HumanoidArmorLayer.shouldRender(shown, slot.equipmentSlot) && !shown.has(DataComponents.GLIDER);
			if (!armorShown) continue;

			if (settings.hasDesign()) data.designs[i] = enabledLayers(settings, now);
			data.opacity[i] = settings.opacity;
			// Animated glow: strength (breathe, flicker) and color (rainbow) for this frame
			float strength = settings.glowAnimation.strength(settings.dyeSpeed, now);
			data.glow[i] = settings.glow * strength;
			data.trimGlow[i] = settings.trimGlowAmount * strength;
			data.glowTint[i] = settings.glowAnimation.tint(settings.dyeSpeed, now);

			if (settings.hasCustomArmorRendering()) {
				data.armor[i] = shown;

				// See-through armor is drawn entirely by Vanity, so the game must not draw the solid version
				if (settings.opacity < 0.995F) setEquipment(slot, state, ItemStack.EMPTY);
			}
		}
	}

	private static final TrimLayer[] NO_DESIGN = new TrimLayer[0];

	/** The design's shown layers, with their colors animated for this frame (each layer a bit out of step). */
	private static TrimLayer[] enabledLayers(SlotSettings settings, long now) {
		int count = 0;
		for (TrimLayer layer : settings.design) if (layer.enabled) count++;
		TrimLayer[] layers = new TrimLayer[count];
		int n = 0;

		for (TrimLayer layer : settings.design) {
			if (!layer.enabled) continue;
			TrimLayer copy = layer.copy();
			long offset = (long) (n * 2000.0F / settings.dyeSpeed);
			copy.color = settings.designAnimation.color(layer.color, settings.dyeSpeed, now + offset);
			layers[n++] = copy;
		}

		return layers;
	}

	private static void extractSlot(CosmeticSlot slot, VanityRenderData data, AvatarRenderState state, LivingEntity entity, ItemModelResolver resolver, SlotSettings settings) {
		int i = slot.ordinal();

		ItemStack worn = entity.getItemBySlot(slot.equipmentSlot);

		if (settings.invisible) {
			hideRealArmor(slot, state);
			return;
		}

		ItemStack look = settings.stack();

		if (look.isEmpty()) {
			// No look: still apply glint, dye and trim options to your real armor
			ItemStack real = equipment(slot, state);

			if (settings.hasStyleOverrides() && HumanoidArmorLayer.shouldRender(real, slot.equipmentSlot)) {
				setEquipment(slot, state, styled(real, real, settings));
			}

			return;
		}

		if (settings.onlyWhenWorn && worn.isEmpty()) return;

		if (isWornHead(slot, look) && look.getItem() instanceof BlockItem blockItem
				&& blockItem.getBlock() instanceof AbstractSkullBlock skull) {
			// Player and mob heads: shown like a worn head, with the head's own skin.
			state.headEquipment = ItemStack.EMPTY;
			state.headItem.clear();
			state.wornHeadType = skull.getType();
			state.wornHeadProfile = look.get(DataComponents.PROFILE);
			return;
		}

		if (isArmorLook(slot, look)) {
			// Keep elytra wings visible: the chest look would replace them.
			if (slot == CosmeticSlot.CHEST && worn.has(DataComponents.GLIDER)) return;

			// Swap the armor the game draws: wearing diamond, shown as netherite.
			setEquipment(slot, state, styled(look, equipment(slot, state), settings));

			if (slot == CosmeticSlot.HEAD) {
				state.headItem.clear();
				state.wornHeadType = null;
			}

			return;
		}

		// Any other item or block: draw it fitted to the armor shape.
		ItemDisplayContext context = settings.mode == FitMode.FREE ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE;
		resolver.updateForLiving(data.items[i], look, context, entity);
		data.settings[i] = settings.copy();
		data.active[i] = !data.items[i].isEmpty();

		if (data.active[i] && settings.hideArmor) {
			hideRealArmor(slot, state);
		}
	}

	/**
	 * The slot whose look applies to this exact item: the item must be the one you are wearing in that slot (not a copy
	 * elsewhere in the inventory) and the slot must have a visible look. Null otherwise.
	 */
	public static CosmeticSlot wornSlotWithLook(ItemStack stack) {
		if (stack.isEmpty()) return null;
		VanityConfig config = VanityClient.config();
		if (!config.enabled) return null;
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) return null;

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			if (player.getItemBySlot(slot.equipmentSlot) == stack) {
				SlotSettings settings = config.get(slot);
				if (settings.invisible) return null;
				return !settings.stack().isEmpty() || settings.hasStyleOverrides() ? slot : null;
			}
		}

		return null;
	}

	/** The icon to draw in the inventory for this item: its look if you're wearing it in a slot with a look. */
	public static ItemStack inventoryIcon(ItemStack stack) {
		if (!VanityClient.config().showInInventory) return stack;
		CosmeticSlot slot = wornSlotWithLook(stack);
		if (slot == null) return ItemSkins.find(stack) != null && ItemSkins.isOwn(stack) ? ItemSkins.skinned(stack) : stack;

		SlotSettings settings = VanityClient.config().get(slot);
		ItemStack look = settings.stack();
		if (look.isEmpty()) return HumanoidArmorLayer.shouldRender(stack, slot.equipmentSlot) ? styled(stack, stack, settings) : stack;
		return isArmorLook(slot, look) ? styled(look, stack, settings) : look;
	}

	/** Applies the slot's glint, trim and dye options to an armor look. */
	public static ItemStack styled(ItemStack look, ItemStack real, SlotSettings settings) {
		ItemStack copy = look.copy();

		switch (settings.glint) {
			case AUTO -> {
				if (!real.isEmpty() && real.hasFoil()) copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			case ON -> copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			case OFF -> copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		}

		switch (settings.trim) {
			case REAL -> {
				ArmorTrim trim = real.isEmpty() ? null : real.get(DataComponents.TRIM);
				if (trim != null) copy.set(DataComponents.TRIM, trim);
			}
			case NONE -> copy.remove(DataComponents.TRIM);
			case PICK -> {
				ArmorTrim trim = Trims.create(settings.trimPattern, settings.trimMaterial);
				if (trim != null) copy.set(DataComponents.TRIM, trim);
			}
		}

		if (settings.customDye && isDyeable(copy)) {
			copy.set(DataComponents.DYED_COLOR, new DyedItemColor(settings.dyeColorNow(Util.getMillis())));
		}

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
