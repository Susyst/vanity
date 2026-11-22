package dev.susyst.vanity.render;

import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.ItemSkin;
import dev.susyst.vanity.config.VanityConfig;

/** Finds the skin for a held or carried item and builds the stack to draw instead. */
public final class ItemSkins {
	/** SkyBlock ids read from custom data, by custom data instance, so big item data isn't copied every frame. */
	private static final Cache<CustomData, String> SKYBLOCK_IDS = CacheBuilder.newBuilder().weakKeys().expireAfterAccess(5, TimeUnit.MINUTES).build();

	private ItemSkins() {
	}

	/** The SkyBlock item id in the item's custom data ("HYPERION"), or "" if it has none. */
	public static String skyblockId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) return "";
		String id = SKYBLOCK_IDS.getIfPresent(data);

		if (id == null) {
			id = data.copyTag().getStringOr("id", "");
			SKYBLOCK_IDS.put(data, id);
		}

		return id;
	}

	/** The enabled skin with a look that matches this item, or null. */
	public static ItemSkin find(ItemStack stack) {
		VanityConfig config = VanityClient.config();
		if (!config.enabled || config.itemSkins.isEmpty() || stack.isEmpty()) return null;
		String itemId = null;
		String skyblockId = null;

		for (ItemSkin skin : config.itemSkins) {
			if (!skin.enabled || skin.look().isEmpty()) continue;
			if (itemId == null) itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if (!skin.item.equals(itemId)) continue;

			if (!skin.skyblockId.isEmpty()) {
				if (skyblockId == null) skyblockId = skyblockId(stack);
				if (!skin.skyblockId.equals(skyblockId)) continue;
			}

			return skin;
		}

		return null;
	}

	/** What to draw for this item, or the item itself if it has no skin. */
	public static ItemStack skinned(ItemStack stack) {
		ItemSkin skin = find(stack);
		if (skin == null) return stack;

		ItemStack look = skin.look().copyWithCount(Math.max(1, stack.getCount()));

		switch (skin.glint) {
			case AUTO -> look.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, stack.hasFoil());
			case ON -> look.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			case OFF -> look.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
		}

		return look;
	}

	/** Third person (and the wardrobe preview): draw the skins of the items in your hands. */
	public static void applyToHands(ArmedEntityRenderState state, LivingEntity entity, ItemModelResolver resolver) {
		if (VanityClient.config().itemSkins.isEmpty()) return;

		for (HumanoidArm arm : HumanoidArm.values()) {
			ItemStack held = entity.getItemHeldByArm(arm);
			ItemStack look = skinned(held);
			if (look == held) continue;
			boolean right = arm == HumanoidArm.RIGHT;
			resolver.updateForLiving(right ? state.rightHandItemState : state.leftHandItemState, look,
					right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
		}
	}

	/** True if this exact stack is in your own inventory (hotbar, main, armor or off hand) or on your cursor. */
	public static boolean isOwn(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || stack.isEmpty()) return false;
		// The item you picked up in an inventory and are moving around
		if (player.containerMenu.getCarried() == stack) return true;
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot) == stack) return true;
		}

		return false;
	}

	/** A new skin for the item you're holding, matching its type and (if it has one) its SkyBlock id. */
	public static ItemSkin create(ItemStack held) {
		ItemSkin skin = new ItemSkin();
		skin.item = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
		skin.skyblockId = skyblockId(held);
		skin.name = held.getHoverName().getString();
		return skin;
	}
}
