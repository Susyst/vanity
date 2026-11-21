package dev.susyst.vanity.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

			// Fitting uses the raw model (no display transform). Vanilla size uses the item's head transform.
			ItemDisplayContext context = settings.mode == FitMode.FREE ? ItemDisplayContext.HEAD : ItemDisplayContext.NONE;
			resolver.updateForLiving(data.items[i], stack, context, entity);
			data.settings[i] = settings.copy();
			data.active[i] = !data.items[i].isEmpty();

			if (data.active[i] && settings.hideArmor) {
				hideRealArmor(slot, state);
			}
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
