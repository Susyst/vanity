package dev.susyst.vanity;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.ItemSkin;
import dev.susyst.vanity.config.Outfit;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.ItemSkins;
import dev.susyst.vanity.render.RuneParticles;
import dev.susyst.vanity.render.VanityCosmetics;
import dev.susyst.vanity.render.VanityLayer;
import dev.susyst.vanity.render.VanityRenderData;
import dev.susyst.vanity.screen.VanityScreen;

public final class VanityClient implements ClientModInitializer {
	public static final String MOD_ID = "vanity";
	public static final RenderStateDataKey<VanityRenderData> RENDER_DATA = RenderStateDataKey.create(() -> "vanity cosmetics");

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
	private static VanityConfig config = new VanityConfig();
	private static KeyMapping openKey;
	private static KeyMapping nextOutfitKey;
	private static KeyMapping previousOutfitKey;

	/** Slim (Alex) player models have 3-pixel-wide arms. */
	private static boolean isSlim(PlayerModel model) {
		if (model.leftArm.isEmpty()) return false;
		ModelPart.Cube arm = model.leftArm.getRandomCube(RandomSource.create(0L));
		return arm.maxX - arm.minX < 3.5F;
	}

	public static VanityConfig config() {
		return config;
	}

	public static KeyMapping nextOutfitKey() {
		return nextOutfitKey;
	}

	/** The key bound to Next outfit, as shown in Controls. */
	public static String nextOutfitKeyName() {
		return nextOutfitKey == null ? "N" : nextOutfitKey.getTranslatedKeyMessage().getString();
	}

	/** Wears the next or previous saved outfit and says which one above the hotbar. */
	public static void switchOutfit(Minecraft client, int direction) {
		if (client.player == null) return;
		Outfit outfit = config.wearNextOutfit(direction);
		Component message;

		if (outfit == null) {
			message = Component.literal("No saved outfits yet: press ").append(openKey.getTranslatedKeyMessage()).append(", then Outfits → Save new");
		} else {
			config.save();
			message = Component.literal("Outfit: ").withColor(0x8FA3B8)
					.append(Component.literal(outfit.name).withColor(0x5BD2FF))
					.append(Component.literal("  (" + (config.outfit + 1) + "/" + config.outfits.size() + ")").withColor(0x8FA3B8));
		}

		client.gui.hud.setOverlayMessage(message, false);
	}

	@Override
	public void onInitializeClient() {
		config = VanityConfig.load();

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanity.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, CATEGORY));
		nextOutfitKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanity.next_outfit", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_N, CATEGORY));
		previousOutfitKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanity.previous_outfit", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RuneParticles.tick(client);

			while (openKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new VanityScreen());
				}
			}

			while (nextOutfitKey.consumeClick()) switchOutfit(client, 1);
			while (previousOutfitKey.consumeClick()) switchOutfit(client, -1);
		});

		// The real item keeps its name in tooltips; add a line saying what it looks like.
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
			CosmeticSlot slot = VanityCosmetics.wornSlotWithLook(stack);
			Component line = null;

			if (slot != null) {
				line = Component.literal("Vanity look: ").append(config.get(slot).stack().getHoverName()).withStyle(ChatFormatting.GRAY);
			} else if (ItemSkins.isOwn(stack)) {
				ItemSkin skin = ItemSkins.find(stack);
				if (skin != null) line = Component.literal("Vanity skin: ").append(skin.look().getHoverName()).withStyle(ChatFormatting.GRAY);
			}

			if (line != null) lines.add(Math.min(1, lines.size()), line);
		});

		// Put on the outfit picked for this server or world
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(() -> AutoOutfits.onJoin(client)));

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
			if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
				// Same armor models the vanilla armor layer uses (slim or wide arms), for drawing trim designs
				boolean slim = isSlim(avatarRenderer.getModel());
				ArmorModelSet<PlayerModel> armorModels = ArmorModelSet.bake(
						slim ? ModelLayers.PLAYER_SLIM_ARMOR : ModelLayers.PLAYER_ARMOR,
						context.getModelSet(),
						part -> new PlayerModel(part, slim));
				registrationHelper.register(new VanityLayer(avatarRenderer, armorModels, context.getEquipmentAssets()));
			}
		});
	}
}
