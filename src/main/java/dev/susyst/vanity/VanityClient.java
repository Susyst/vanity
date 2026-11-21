package dev.susyst.vanity;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
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
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.VanityConfig;
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

	/** Slim (Alex) player models have 3-pixel-wide arms. */
	private static boolean isSlim(PlayerModel model) {
		if (model.leftArm.isEmpty()) return false;
		ModelPart.Cube arm = model.leftArm.getRandomCube(RandomSource.create(0L));
		return arm.maxX - arm.minX < 3.5F;
	}

	public static VanityConfig config() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		config = VanityConfig.load();

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanity.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			RuneParticles.tick(client);

			while (openKey.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new VanityScreen());
				}
			}
		});

		// The real item keeps its name in tooltips; add a line saying what it looks like.
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
			CosmeticSlot slot = VanityCosmetics.wornSlotWithLook(stack);
			if (slot == null) return;
			Component line = Component.literal("Vanity look: ").append(config.get(slot).stack().getHoverName()).withStyle(ChatFormatting.GRAY);
			lines.add(Math.min(1, lines.size()), line);
		});

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
