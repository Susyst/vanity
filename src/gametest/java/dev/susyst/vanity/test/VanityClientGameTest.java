package dev.susyst.vanity.test;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.FitMode;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.screen.VanityScreen;

/**
 * Starts a real client, puts cosmetics on the player and takes screenshots, so rendering can be checked on CI.
 * Screenshots end up in build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class VanityClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1280, 720);

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
				.adjustSettings(creator -> creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");
			singleplayer.getServer().runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
			singleplayer.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
			singleplayer.getServer().runCommand("item replace entity @a armor.legs with minecraft:golden_leggings");
			singleplayer.getServer().runCommand("item replace entity @a armor.feet with minecraft:netherite_boots");
			singleplayer.getConnection().waitForChunksRender();

			setCamera(context, CameraType.THIRD_PERSON_FRONT);
			clearCosmetics(context);
			context.waitTicks(20);
			cleanView(context);
			context.takeScreenshot("01_real_armor_no_cosmetics");

			// Armor look: wearing iron/diamond/gold/netherite, shown as a full netherite set
			setCosmetics(context, FitMode.STRETCH, "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots");
			context.waitTicks(5);
			context.takeScreenshot("01b_netherite_look_front");
			setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(5);
			context.takeScreenshot("01c_netherite_look_back");
			setCamera(context, CameraType.THIRD_PERSON_FRONT);

			// Mixed looks per slot
			setCosmetics(context, FitMode.STRETCH, "minecraft:golden_helmet", "minecraft:netherite_chestplate", "minecraft:leather_leggings", "minecraft:copper_boots");
			context.waitTicks(5);
			context.takeScreenshot("01d_mixed_armor_look");

			// Enchanted real armor keeps its shine on the new look
			singleplayer.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate[minecraft:enchantment_glint_override=true]");
			setCosmetics(context, FitMode.STRETCH, "", "minecraft:netherite_chestplate", "", "");
			context.waitTicks(10);
			context.takeScreenshot("01e_enchanted_diamond_shown_as_netherite");

			setCosmetics(context, FitMode.STRETCH, "minecraft:glass", "minecraft:light_blue_stained_glass", "minecraft:glass", "minecraft:tinted_glass");
			context.waitTicks(5);
			context.takeScreenshot("02_glass_armor_front");

			setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(5);
			context.takeScreenshot("03_glass_armor_back");

			setCamera(context, CameraType.THIRD_PERSON_FRONT);
			setCosmetics(context, FitMode.STRETCH, "minecraft:oak_log", "minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block");
			context.waitTicks(5);
			context.takeScreenshot("04_solid_blocks_armor_shape");

			setCosmetics(context, FitMode.CONTAIN, "minecraft:oak_log", "minecraft:iron_block", "minecraft:gold_block", "minecraft:diamond_block");
			context.waitTicks(5);
			context.takeScreenshot("05_solid_blocks_fit_inside");

			context.runOnClient(client -> {
				VanityConfig config = VanityClient.config();
				set(config.head, "minecraft:carved_pumpkin", FitMode.FREE);
				set(config.chest, "minecraft:diamond_sword", FitMode.CONTAIN);
				set(config.legs, "", FitMode.STRETCH);
				set(config.feet, "", FitMode.STRETCH);
			});
			context.waitTicks(5);
			context.takeScreenshot("06_pumpkin_vanilla_size_and_sword_front");

			setCamera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(5);
			context.takeScreenshot("06b_pumpkin_vanilla_size_and_sword_back");
			setCamera(context, CameraType.THIRD_PERSON_FRONT);

			setCosmetics(context, FitMode.STRETCH, "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots");
			context.runOnClient(client -> VanityClient.config().chest.mode = FitMode.STRETCH);
			context.setScreen(VanityScreen::new);
			context.waitTicks(10);
			context.takeScreenshot("07_wardrobe_screen");
			context.setScreen(() -> null);
			context.waitTicks(2);
		}
	}

	/** Hides the HUD, chat and toasts so the player is clearly visible in screenshots. */
	private static void cleanView(ClientGameTestContext context) {
		context.runOnClient(client -> {
			client.gui.toastManager().clear();
			client.gui.hud.getChat().clearMessages(false);

			if (!client.gui.hud.isHidden()) {
				client.gui.hud.toggle();
			}
		});
	}

	private static void setCamera(ClientGameTestContext context, CameraType cameraType) {
		context.runOnClient(client -> client.options.setCameraType(cameraType));
	}

	private static void clearCosmetics(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				VanityClient.config().get(slot).item = "";
			}
		});
	}

	private static void setCosmetics(ClientGameTestContext context, FitMode mode, String head, String chest, String legs, String feet) {
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.enabled = true;
			set(config.head, head, mode);
			set(config.chest, chest, mode);
			set(config.legs, legs, mode);
			set(config.feet, feet, mode);
		});
	}

	private static void set(SlotSettings settings, String item, FitMode mode) {
		settings.item = item;
		settings.mode = mode;
		settings.hideArmor = true;
		settings.resetTransform();
	}
}
