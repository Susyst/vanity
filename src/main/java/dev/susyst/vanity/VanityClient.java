package dev.susyst.vanity;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.VanityConfig;
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

	public static VanityConfig config() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		config = VanityConfig.load();

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.vanity.open", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_V, CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
				registrationHelper.register(new VanityLayer(avatarRenderer));
			}
		});
	}
}
