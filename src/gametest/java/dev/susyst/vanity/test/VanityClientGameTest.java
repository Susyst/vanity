package dev.susyst.vanity.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.susyst.vanity.config.HitEffect;
import dev.susyst.vanity.config.KillEffect;
import dev.susyst.vanity.render.CombatParticles;
import dev.susyst.vanity.render.CustomArmors;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import dev.susyst.vanity.AutoOutfits;
import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.GlowAnimation;
import dev.susyst.vanity.config.ItemSkin;
import dev.susyst.vanity.render.ItemSkins;
import dev.susyst.vanity.config.DyeAnimation;
import dev.susyst.vanity.config.RuneReaction;
import dev.susyst.vanity.render.RuneParticles;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.FitMode;
import dev.susyst.vanity.config.GlintMode;
import dev.susyst.vanity.config.Rune;
import dev.susyst.vanity.config.RuneEffect;
import dev.susyst.vanity.config.RuneShape;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.TrimMode;
import dev.susyst.vanity.render.VanityRenderData;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.VanityCosmetics;
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

			// Texture packs: wearing diamond, shown as gold, with a pack that repaints gold armor
			singleplayer.getServer().runCommand("item replace entity @a armor.chest with minecraft:diamond_chestplate");
			setCosmetics(context, FitMode.STRETCH, "minecraft:golden_helmet", "minecraft:golden_chestplate", "minecraft:golden_leggings", "minecraft:golden_boots");
			context.waitTicks(10);
			context.takeScreenshot("08a_gold_look_vanilla_textures");

			enableTestTexturePack(context);
			context.waitTicks(10);
			context.takeScreenshot("08b_gold_look_with_texture_pack");

			testSkyBlockLooks(context, singleplayer);
			testInventoryIcons(context, singleplayer);
			testArmorOptions(context, singleplayer);
			testTrimsAndDesigns(context, singleplayer);
			testGlowOpacityRunes(context, singleplayer);
			testAnimatedDye(context);
			testOutfits(context);
			testReactiveRunes(context, singleplayer);
			testAnimatedDesignAndGlow(context, singleplayer);
			testItemSkins(context, singleplayer);
			testGrabPickAndSkyblockIds(context, singleplayer);
			testCustomArmors(context, singleplayer);
			testCombatEffects(context, singleplayer);
			testAutoOutfit(context);
		}
	}

	private static final int NECRON_RED = 0xE7413C;

	/** A SkyBlock-style armor piece: dyed leather with a custom name and a SkyBlock item id. */
	private static ItemStack skyBlockPiece(ItemStack base, int color, String name, String id) {
		base.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
		base.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		base.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return base;
	}

	/**
	 * Wears SkyBlock-style armor, saves its look with "Worn", then switches to diamond: the SkyBlock look must stay,
	 * keep its name, dye and SkyBlock id, and survive saving and reloading the config.
	 */
	private static void testSkyBlockLooks(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		clearCosmetics(context);
		ItemStack chest = skyBlockPiece(new ItemStack(Items.LEATHER_CHESTPLATE), NECRON_RED, "Necron's Chestplate", "NECRON_CHESTPLATE");
		ItemStack legs = skyBlockPiece(new ItemStack(Items.LEATHER_LEGGINGS), 0x1793C4, "Storm's Leggings", "WISE_WITHER_LEGGINGS");
		singleplayer.getServer().runOnServer(server -> {
			singleplayer.getConnection().getServerPlayer().setItemSlot(EquipmentSlot.CHEST, chest.copy());
			singleplayer.getConnection().getServerPlayer().setItemSlot(EquipmentSlot.LEGS, legs.copy());
		});
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.LEATHER_CHESTPLATE
				&& client.player.getItemBySlot(EquipmentSlot.LEGS).getItem() == Items.LEATHER_LEGGINGS);

		// Same as pressing "Worn" on the Chest and Legs tabs
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.chest.setLook(client.player.getItemBySlot(EquipmentSlot.CHEST), client.player.registryAccess());
			config.legs.setLook(client.player.getItemBySlot(EquipmentSlot.LEGS), client.player.registryAccess());
			// A player-head helmet, like most SkyBlock helmets
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Notch"));
			config.head.setLook(head, client.player.registryAccess());
		});

		// Now actually wear plain diamond
		singleplayer.getServer().runOnServer(server -> {
			singleplayer.getConnection().getServerPlayer().setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			singleplayer.getConnection().getServerPlayer().setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		});
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.DIAMOND_CHESTPLATE);
		context.waitTicks(40); // let the head skin download
		context.takeScreenshot("09_skyblock_looks_while_wearing_diamond");

		context.runOnClient(client -> {
			VanityClient.config().save();
			VanityConfig reloaded = VanityConfig.load();
			ItemStack saved = reloaded.chest.stack();

			if (!saved.getHoverName().getString().equals("Necron's Chestplate")) {
				throw new AssertionError("Saved look lost its name: " + saved.getHoverName().getString());
			}

			DyedItemColor color = saved.get(DataComponents.DYED_COLOR);

			if (color == null || color.rgb() != NECRON_RED) {
				throw new AssertionError("Saved look lost its dye color: " + color);
			}

			CustomData data = saved.get(DataComponents.CUSTOM_DATA);

			if (data == null || !data.copyTag().getStringOr("id", "").equals("NECRON_CHESTPLATE")) {
				throw new AssertionError("Saved look lost its SkyBlock id: " + data);
			}

			if (reloaded.head.stack().getItem() != Items.PLAYER_HEAD || reloaded.head.stack().get(DataComponents.PROFILE) == null) {
				throw new AssertionError("Saved head look lost its skin profile");
			}
		});

		context.setScreen(VanityScreen::new);
		context.waitTicks(10);
		context.takeScreenshot("10_wardrobe_skyblock_head");
		context.setScreen(() -> null);
	}

	/**
	 * Wearing iron helmet + diamond chestplate with golden helmet / netherite chestplate looks: the worn items' icons in
	 * the inventory show the looks, a spare diamond chestplate in the hotbar does not, and tooltips keep the real name.
	 */
	private static void testInventoryIcons(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("gamemode survival @a");
		singleplayer.getServer().runOnServer(server -> {
			var player = singleplayer.getConnection().getServerPlayer();
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
		});
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.CHEST).getItem() == Items.DIAMOND_CHESTPLATE
				&& client.player.getItemBySlot(EquipmentSlot.HEAD).getItem() == Items.IRON_HELMET
				&& client.player.getInventory().getItem(0).getItem() == Items.DIAMOND_CHESTPLATE);

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.enabled = true;
			config.showInInventory = true;
			set(config.head, "minecraft:golden_helmet", FitMode.STRETCH);
			set(config.chest, "minecraft:netherite_chestplate", FitMode.STRETCH);
			set(config.legs, "", FitMode.STRETCH);
			set(config.feet, "", FitMode.STRETCH);

			ItemStack wornChest = client.player.getItemBySlot(EquipmentSlot.CHEST);
			ItemStack spareChest = client.player.getInventory().getItem(0);

			if (VanityCosmetics.inventoryIcon(wornChest).getItem() != Items.NETHERITE_CHESTPLATE) {
				throw new AssertionError("Worn chestplate icon should show the netherite look");
			}

			if (VanityCosmetics.inventoryIcon(spareChest) != spareChest) {
				throw new AssertionError("A chestplate you're not wearing must keep its own icon");
			}

			String tooltip = Screen.getTooltipFromItem(client, wornChest).stream().map(Component::getString).reduce("", (a, b) -> a + "\n" + b);

			if (!tooltip.contains("Diamond Chestplate") || !tooltip.contains("Vanity look: Netherite Chestplate")) {
				throw new AssertionError("Tooltip should keep the real name and add the look: " + tooltip);
			}
		});

		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(10);
		context.takeScreenshot("11_inventory_icons_show_looks");
		context.setScreen(() -> null);

		context.runOnClient(client -> VanityClient.config().showInInventory = false);
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(10);
		context.takeScreenshot("12_inventory_icons_toggle_off");
		context.setScreen(() -> null);
		context.runOnClient(client -> VanityClient.config().showInInventory = true);
	}

	/** The armor the game is about to draw on the player for this slot (what the armor layer renders). */
	private static ItemStack drawnArmor(Minecraft client, EquipmentSlot slot) {
		EntityRenderState state = client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);

		if (!(state instanceof AvatarRenderState avatar)) {
			throw new AssertionError("Expected a player render state");
		}

		return switch (slot) {
			case HEAD -> avatar.headEquipment;
			case CHEST -> avatar.chestEquipment;
			case LEGS -> avatar.legsEquipment;
			case FEET -> avatar.feetEquipment;
			default -> ItemStack.EMPTY;
		};
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	/** "No armor" hide/show, invisible slots, leather dye and glint on/off. */
	private static void testArmorOptions(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			var player = singleplayer.getConnection().getServerPlayer();
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			ItemStack enchantedChest = new ItemStack(Items.DIAMOND_CHESTPLATE);
			enchantedChest.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			player.setItemSlot(EquipmentSlot.CHEST, enchantedChest);
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); // no boots on
		});
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
				&& client.player.getItemBySlot(EquipmentSlot.CHEST).hasFoil());

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			set(config.head, "", FitMode.STRETCH);
			set(config.chest, "minecraft:netherite_chestplate", FitMode.STRETCH);
			set(config.legs, "minecraft:netherite_leggings", FitMode.STRETCH);
			set(config.feet, "minecraft:netherite_boots", FitMode.STRETCH);

			// No boots worn: by default the boots look is hidden too
			check(drawnArmor(client, EquipmentSlot.FEET).isEmpty(), "Boots look should hide when no boots are worn");
			config.feet.onlyWhenWorn = false;
			check(drawnArmor(client, EquipmentSlot.FEET).getItem() == Items.NETHERITE_BOOTS, "Boots look should show when 'No armor: Show'");
			config.feet.onlyWhenWorn = true;

			// Glint: like real (enchanted diamond), off, on
			check(drawnArmor(client, EquipmentSlot.CHEST).hasFoil(), "Glint 'Like real' should copy the enchanted chestplate's shine");
			config.chest.glint = GlintMode.OFF;
			check(!drawnArmor(client, EquipmentSlot.CHEST).hasFoil(), "Glint 'Off' should remove the shine");
			config.legs.glint = GlintMode.ON;
			check(drawnArmor(client, EquipmentSlot.LEGS).hasFoil(), "Glint 'On' should add shine to unenchanted leggings");

			// Invisible legs: nothing drawn, not even the real golden leggings
			config.legs.invisible = true;
			check(drawnArmor(client, EquipmentSlot.LEGS).isEmpty(), "Invisible slot should draw nothing");
		});
		context.waitTicks(5);
		context.takeScreenshot("13_no_boots_invisible_legs_glint_on_off");

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.legs.invisible = false;
			set(config.legs, "", FitMode.STRETCH);
			set(config.chest, "minecraft:leather_chestplate", FitMode.STRETCH);
			config.chest.customDye = true;
			config.chest.dyeColor = 0x30C0FF;
			set(config.head, "minecraft:leather_helmet", FitMode.STRETCH);
			config.head.customDye = true;
			config.head.dyeColor = 0xFF40A0;

			DyedItemColor color = drawnArmor(client, EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR);
			check(color != null && color.rgb() == 0x30C0FF, "Dyed leather look should use the slider color, got " + color);
		});
		context.waitTicks(5);
		context.takeScreenshot("14_dyed_leather_looks");

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST));
		context.waitTicks(10);
		context.takeScreenshot("15_wardrobe_dye_sliders");
		context.setScreen(() -> null);
	}

	private static boolean hasTrim(ItemStack stack, String pattern, String material) {
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		return trim != null && trim.pattern().is(Identifier.parse(pattern)) && trim.material().is(Identifier.parse(material));
	}

	/** Vanilla trims on a look and on real armor (and its icon), and custom multi-color trim designs. */
	private static void testTrimsAndDesigns(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			var player = singleplayer.getConnection().getServerPlayer();
			player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
			player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		});
		context.waitFor(client -> client.player.getItemBySlot(EquipmentSlot.FEET).getItem() == Items.IRON_BOOTS
				&& client.player.getItemBySlot(EquipmentSlot.LEGS).getItem() == Items.IRON_LEGGINGS
				&& !client.player.getItemBySlot(EquipmentSlot.CHEST).hasFoil());

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			set(config.head, "minecraft:netherite_helmet", FitMode.STRETCH);
			set(config.chest, "minecraft:netherite_chestplate", FitMode.STRETCH);
			set(config.legs, "", FitMode.STRETCH);
			set(config.feet, "", FitMode.STRETCH);

			// Picked vanilla trim on a look
			config.chest.trim = TrimMode.PICK;
			config.chest.trimPattern = "minecraft:rib";
			config.chest.trimMaterial = "minecraft:gold";
			check(hasTrim(drawnArmor(client, EquipmentSlot.CHEST), "minecraft:rib", "minecraft:gold"), "Chest look should have a gold rib trim");

			// Trim on real armor, no look needed, also on the inventory icon
			config.legs.trim = TrimMode.PICK;
			config.legs.trimPattern = "minecraft:silence";
			config.legs.trimMaterial = "minecraft:diamond";
			check(hasTrim(drawnArmor(client, EquipmentSlot.LEGS), "minecraft:silence", "minecraft:diamond"), "Real leggings should get the picked trim");
			ItemStack wornLegs = client.player.getItemBySlot(EquipmentSlot.LEGS);
			check(hasTrim(VanityCosmetics.inventoryIcon(wornLegs), "minecraft:silence", "minecraft:diamond"), "Leggings icon should show the picked trim");

			// Custom design: two patterns in custom colors on the helmet, one on the boots
			TrimLayer eye = config.head.design[0];
			eye.enabled = true;
			eye.pattern = "minecraft:eye";
			eye.color = 0x00FFFF;
			TrimLayer spire = config.head.design[1];
			spire.enabled = true;
			spire.pattern = "minecraft:spire";
			spire.color = 0xFF2080;
			TrimLayer bolt = config.feet.design[0];
			bolt.enabled = true;
			bolt.pattern = "minecraft:bolt";
			bolt.color = 0xFFE000;

			AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
			VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
			check(data != null && data.designs[CosmeticSlot.HEAD.ordinal()].length == 2, "Helmet should have 2 design layers");
			check(data.designs[CosmeticSlot.FEET.ordinal()].length == 1, "Boots should have 1 design layer");
		});
		context.waitTicks(10);
		context.takeScreenshot("16_trims_and_custom_designs");
		setCamera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(5);
		context.takeScreenshot("17_trims_and_custom_designs_back");
		setCamera(context, CameraType.THIRD_PERSON_FRONT);

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.TRIM));
		context.waitTicks(10);
		context.takeScreenshot("18_wardrobe_trim_page");
		context.setScreen(() -> new VanityScreen(CosmeticSlot.HEAD, VanityScreen.Page.DESIGN));
		context.waitTicks(10);
		context.takeScreenshot("19_wardrobe_design_page");
		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.STYLE));
		context.waitTicks(10);
		context.takeScreenshot("20_wardrobe_style_page");
		context.setScreen(() -> null);
	}

	private static void rune(Rune rune, RuneEffect effect, RuneShape shape, int color, float size, int detail) {
		rune.enabled = true;
		rune.effect = effect;
		rune.shape = shape;
		rune.color = color;
		rune.size = size;
		rune.detail = detail;
		rune.length = 12;
		rune.speed = 1.0F;
	}

	/** Glowing armor and trims at night, see-through leggings, and runes on every piece. */
	private static void testGlowOpacityRunes(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			// Chest: netherite look, fully glowing, with a glowing gold rib trim
			config.chest.glow = 1.0F;
			config.chest.trimGlowAmount = 0.5F;
			// Head: keep the custom design from before, make it glow
			config.head.trimGlowAmount = 0.5F;
			// Legs: see-through
			config.legs.opacity = 0.35F;
			// Feet: real iron boots glowing, with the glowing yellow bolt design from before
			config.feet.glow = 1.0F;
			config.feet.trimGlowAmount = 0.5F;

			check(drawnArmor(client, EquipmentSlot.LEGS).isEmpty(), "See-through leggings must not be drawn by the vanilla armor layer");
			AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
			VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
			check(data != null && !data.armor[CosmeticSlot.LEGS.ordinal()].isEmpty(), "Vanity should draw the see-through leggings");
			check(Math.abs(data.opacity[CosmeticSlot.LEGS.ordinal()] - 0.35F) < 0.001F, "Leggings opacity should be 35%");
			check(!data.armor[CosmeticSlot.CHEST.ordinal()].isEmpty() && data.glow[CosmeticSlot.CHEST.ordinal()] > 0.99F, "Chest should glow");
		});

		singleplayer.getServer().runCommand("time set noon");
		context.waitTicks(10);
		context.takeScreenshot("21_see_through_leggings_day");

		singleplayer.getServer().runCommand("time set midnight");
		context.waitTicks(10);
		context.takeScreenshot("22_glowing_armor_and_trims_night");

		// Runes on every piece
		singleplayer.getServer().runCommand("time set noon");
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			rune(config.head.rune, RuneEffect.DUST, RuneShape.HALO, 0x5BD2FF, 1.2F, 16);
			rune(config.chest.rune, RuneEffect.DUST, RuneShape.WINGS, 0xFF4FD8, 1.0F, 28);
			rune(config.legs.rune, RuneEffect.RAINBOW, RuneShape.SPIRAL, 0xFFFFFF, 1.0F, 20);
			rune(config.feet.rune, RuneEffect.FLAME, RuneShape.TRAIL, 0xFFFFFF, 1.0F, 14);
		});
		context.waitTicks(30);
		context.takeScreenshot("23_runes_front");
		setCamera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(20);
		context.takeScreenshot("24_runes_back_wings");
		setCamera(context, CameraType.THIRD_PERSON_FRONT);

		context.runOnClient(client -> {
			rune(VanityClient.config().chest.rune, RuneEffect.SOUL_FLAME, RuneShape.ORBIT, 0xFFFFFF, 1.0F, 24);
			rune(VanityClient.config().head.rune, RuneEffect.LIGHTNING, RuneShape.HALO, 0xFFFFFF, 1.0F, 12);
			VanityClient.config().legs.rune.enabled = false;
		});
		context.waitTicks(30);
		context.takeScreenshot("25_runes_orbit_lightning");

		// Low detail must still draw the whole shape (a dotted halo and wings), not a broken one
		context.runOnClient(client -> {
			rune(VanityClient.config().head.rune, RuneEffect.DUST, RuneShape.HALO, 0xFFD54F, 1.2F, 6);
			rune(VanityClient.config().chest.rune, RuneEffect.DUST, RuneShape.WINGS, 0x5BD2FF, 1.0F, 10);
			VanityClient.config().feet.rune.enabled = false;
		});
		context.waitTicks(30);
		setCamera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(15);
		context.takeScreenshot("25b_runes_low_detail");
		setCamera(context, CameraType.THIRD_PERSON_FRONT);

		// All tab: one change applies to every piece
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.chest.rune.enabled = false;
			config.legs.rune.enabled = false;
			config.head.rune.shape = null; // "Per piece": every slot keeps its own shape
		});
		context.setScreen(() -> VanityScreen.allSlots(VanityScreen.Page.RUNE));
		context.waitTicks(5);

		// Click the Effect dropdown, then the "Rainbow" row, at the positions the screen reports
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Effect:"));
		context.waitTicks(3);
		check(context.computeOnClient(client -> ((VanityScreen) client.gui.screen()).menuOpen()), "Clicking Effect should open its dropdown");
		context.takeScreenshot("25c_dropdown_open");
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).menuRowCenter("Rainbow"));
		context.waitTicks(3);
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				Rune rune = VanityClient.config().get(slot).rune;
				check(rune.enabled && rune.effect == RuneEffect.RAINBOW, slot.label + " should get the Rainbow rune picked on the All tab, got " + rune.effect + " enabled=" + rune.enabled);
				check(rune.shape == null, slot.label + " should keep its own rune shape, got " + rune.shape);
			}
		});
		context.takeScreenshot("25d_all_slots_rainbow_picked");
		context.setScreen(() -> null);
		context.waitTicks(30);
		context.takeScreenshot("25e_rainbow_runes_everywhere");

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.RUNE));
		context.waitTicks(10);
		context.takeScreenshot("26_wardrobe_rune_page");
		context.setScreen(() -> new VanityScreen(CosmeticSlot.LEGS, VanityScreen.Page.STYLE));
		context.waitTicks(10);
		context.takeScreenshot("27_wardrobe_style_glow_opacity");
		context.setScreen(() -> null);
	}

	/** Leather looks with rainbow, pulse and shimmer dye: the color must move over time. */
	private static void testAnimatedDye(ClientGameTestContext context) {
		setCosmetics(context, FitMode.STRETCH, "minecraft:leather_helmet", "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots");
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();

			for (CosmeticSlot slot : CosmeticSlot.values()) {
				SlotSettings settings = config.get(slot);
				settings.rune.enabled = false;
				settings.customDye = true;
			}

			config.head.dyeColor = 0xFF4FD8;
			config.head.dyeAnimation = DyeAnimation.SHIMMER;
			config.chest.dyeAnimation = DyeAnimation.RAINBOW;
			config.chest.dyeSpeed = 2.0F;
			config.legs.dyeColor = 0x3366FF;
			config.legs.dyeAnimation = DyeAnimation.PULSE;
			config.feet.dyeAnimation = DyeAnimation.RAINBOW;

			for (CosmeticSlot slot : CosmeticSlot.values()) {
				SlotSettings settings = config.get(slot);
				check(settings.dyeColorNow(0L) != settings.dyeColorNow(700L), slot.label + " dye should change over time with " + settings.dyeAnimation);
			}

			ItemStack real = client.player.getItemBySlot(EquipmentSlot.CHEST);
			ItemStack early = VanityCosmetics.styled(config.chest.stack(), real, config.chest);
			check(early.get(DataComponents.DYED_COLOR) != null, "Rainbow chestplate should be dyed");
		});
		context.waitTicks(10);
		context.takeScreenshot("28a_animated_dye");
		int before = context.computeOnClient(client -> drawnArmor(client, EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR).rgb());
		context.waitTicks(25);
		context.takeScreenshot("28b_animated_dye_later");
		int after = context.computeOnClient(client -> drawnArmor(client, EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR).rgb());
		check(before != after, "The drawn rainbow chestplate should change color, stayed #" + Integer.toHexString(before));

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.STYLE));
		context.waitTicks(10);
		context.takeScreenshot("28c_wardrobe_dye_animation");
		context.setScreen(() -> null);
	}

	/** Save two outfits in the wardrobe, then switch between them with the Next outfit key. */
	private static void testOutfits(ClientGameTestContext context) {
		context.runOnClient(client -> {
			VanityClient.config().outfits.clear();
			VanityClient.config().outfit = -1;
		});

		// Outfit 1: the animated leather from before
		context.setScreen(() -> new VanityScreen(CosmeticSlot.HEAD, VanityScreen.Page.OUTFITS));
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Save new"));
		context.waitTicks(2);
		context.runOnClient(client -> VanityClient.config().outfits.get(0).name = "Rainbow leather");
		context.setScreen(() -> null);

		// Outfit 2: full netherite with a gold trim
		setCosmetics(context, FitMode.STRETCH, "minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots");
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				SlotSettings settings = VanityClient.config().get(slot);
				settings.trim = TrimMode.PICK;
				settings.trimPattern = "minecraft:rib";
				settings.trimMaterial = "minecraft:gold";
			}
		});
		context.setScreen(() -> new VanityScreen(CosmeticSlot.HEAD, VanityScreen.Page.OUTFITS));
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Save new"));
		context.waitTicks(2);
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.outfits.size() == 2, "Two outfits should be saved, got " + config.outfits.size());
			config.outfits.get(1).name = "Netherite gold";
			check(config.outfit == 1, "The new outfit should be picked");
		});
		context.waitTicks(2);
		context.takeScreenshot("29a_wardrobe_outfits_page");
		context.setScreen(() -> null);

		// Next outfit key wraps around to the first outfit
		context.runOnClient(client -> {
			showHud(client);
		});
		context.getInput().pressKey(VanityClient.nextOutfitKey());
		context.waitTicks(5);
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.outfit == 0, "Next outfit should wrap to the first, got " + config.outfit);
			check(config.chest.item.equals("minecraft:leather_chestplate") && config.chest.dyeAnimation == DyeAnimation.RAINBOW,
					"Wearing the first outfit should bring back the rainbow leather, got " + config.chest.item);
		});
		context.takeScreenshot("29b_outfit_switched_with_key");
		context.getInput().pressKey(VanityClient.nextOutfitKey());
		context.waitTicks(5);
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.outfit == 1 && config.chest.item.equals("minecraft:netherite_chestplate"), "Next outfit should switch to netherite, got " + config.chest.item);
			check(hasTrim(drawnArmor(client, EquipmentSlot.CHEST), "minecraft:rib", "minecraft:gold"), "The netherite outfit's trim should come back too");
		});
		context.takeScreenshot("29c_second_outfit");
		cleanView(context);
	}

	/** A burst of rune particles when you get hit, and denser runes while sprinting. */
	private static void testReactiveRunes(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			rune(config.head.rune, RuneEffect.DUST, RuneShape.HALO, 0x5BD2FF, 1.2F, 16);
			rune(config.chest.rune, RuneEffect.RAINBOW, RuneShape.ORBIT, 0xFFFFFF, 1.0F, 20);
			rune(config.legs.rune, RuneEffect.SOUL_FLAME, RuneShape.SPIRAL, 0xFFFFFF, 1.0F, 16);
			rune(config.feet.rune, RuneEffect.FLAME, RuneShape.TRAIL, 0xFFFFFF, 1.0F, 14);

			for (CosmeticSlot slot : CosmeticSlot.values()) {
				check(config.get(slot).rune.react == RuneReaction.BOTH, "Runes should react to hits and sprinting by default");
			}

			RuneParticles.lastBurstParticles = 0;
		});
		context.waitTicks(30);
		context.takeScreenshot("30a_runes_before_hit");

		singleplayer.getServer().runCommand("gamemode survival @a");
		singleplayer.getServer().runCommand("damage @p 2 minecraft:generic");
		context.waitTicks(4);
		context.takeScreenshot("30b_rune_hit_burst");
		context.runOnClient(client -> check(RuneParticles.lastBurstParticles >= 40, "Getting hit should make a rune burst, got " + RuneParticles.lastBurstParticles + " particles"));
		singleplayer.getServer().runCommand("gamemode creative @a");
		singleplayer.getServer().runCommand("effect give @a minecraft:instant_health 1 5 true");

		// Sprint toward the camera
		context.waitTicks(20);
		context.getInput().holdKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keySprint);
		context.waitTicks(14);
		boolean sprinting = context.computeOnClient(client -> client.player.isSprinting());
		context.takeScreenshot("30c_runes_sprinting");
		context.getInput().releaseKey(options -> options.keySprint);
		context.getInput().releaseKey(options -> options.keyUp);
		check(sprinting, "The player should be sprinting for the sprint rune screenshot");
		context.waitTicks(20);

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.RUNE));
		context.waitTicks(10);
		context.takeScreenshot("30d_wardrobe_rune_react");
		context.setScreen(() -> null);
	}

	/** Rainbow design colors and rainbow/breathing glow on non-leather armor: they must change from frame to frame. */
	private static void testAnimatedDesignAndGlow(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();

			for (CosmeticSlot slot : CosmeticSlot.values()) config.get(slot).rune.enabled = false;

			TrimLayer flow = config.chest.design[0];
			flow.enabled = true;
			flow.pattern = "minecraft:flow";
			flow.color = 0xFFFFFF;
			config.chest.designAnimation = DyeAnimation.RAINBOW;
			config.chest.glow = 1.0F;
			config.chest.glowAnimation = GlowAnimation.RAINBOW;
			config.legs.glow = 1.0F;
			config.legs.glowAnimation = GlowAnimation.BREATHE;
			config.head.trimGlowAmount = 1.0F;
			config.head.glowAnimation = GlowAnimation.FLICKER;
		});
		singleplayer.getServer().runCommand("time set midnight");
		context.waitTicks(10);
		VanityRenderData first = renderData(context);
		context.takeScreenshot("31a_rainbow_design_and_glow_night");
		context.waitTicks(16);
		VanityRenderData second = renderData(context);
		context.takeScreenshot("31b_rainbow_design_and_glow_later");

		int chest = CosmeticSlot.CHEST.ordinal();
		int legs = CosmeticSlot.LEGS.ordinal();
		check(first.designs[chest].length == 1 && second.designs[chest].length == 1, "Chest should have its design layer");
		check(first.designs[chest][0].color != second.designs[chest][0].color, "Rainbow design color should move, stayed #" + Integer.toHexString(first.designs[chest][0].color));
		check(first.glowTint[chest] >= 0 && first.glowTint[chest] != second.glowTint[chest], "Rainbow glow should be tinted with a moving color");
		check(Math.abs(first.glow[legs] - second.glow[legs]) > 0.01F, "Breathing glow should change strength: " + first.glow[legs] + " vs " + second.glow[legs]);
		check(first.glowTint[legs] == -1, "Breathing glow keeps the armor's colors");

		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.STYLE));
		context.waitTicks(10);
		context.takeScreenshot("31c_wardrobe_glow_animation");
		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.DESIGN));
		context.waitTicks(10);
		context.takeScreenshot("31d_wardrobe_design_colors");
		context.setScreen(() -> null);

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			config.chest.resetDesign();
			config.chest.glow = 0.0F;
			config.legs.glow = 0.0F;
			config.head.trimGlowAmount = 0.0F;
		});
		singleplayer.getServer().runCommand("time set noon");
	}

	/** A copy of the player's Vanity render data for this frame. */
	private static VanityRenderData renderData(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
			VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
			check(data != null, "Player should have Vanity render data");
			VanityRenderData copy = new VanityRenderData();

			for (int i = 0; i < copy.glow.length; i++) {
				copy.designs[i] = new TrimLayer[data.designs[i].length];
				for (int k = 0; k < data.designs[i].length; k++) copy.designs[i][k] = data.designs[i][k].copy();
				copy.glow[i] = data.glow[i];
				copy.trimGlow[i] = data.trimGlow[i];
				copy.glowTint[i] = data.glowTint[i];
			}

			return copy;
		});
	}

	/** Held-item skins: a diamond sword shown as netherite, and a SkyBlock "Hyperion" (by its id) shown as a gold sword. */
	private static void testItemSkins(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> VanityClient.config().itemSkins.clear());
		singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
		singleplayer.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:netherite_sword");
		context.waitTicks(5);

		// Hand tab: New from held, then the off-hand netherite sword as the look
		context.setScreen(VanityScreen::hand);
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("New skin"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).pickerCellCenter("minecraft:diamond_sword"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Pick"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).pickerCellCenter("minecraft:netherite_sword"));
		context.waitTicks(3);
		context.takeScreenshot("32a_wardrobe_hand_skins");
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("item replace entity @p weapon.offhand with minecraft:air");
		context.waitTicks(3);

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.itemSkins.size() == 1, "One item skin should be made, got " + config.itemSkins.size());
			ItemSkin skin = config.itemSkins.get(0);
			check(skin.item.equals("minecraft:diamond_sword") && skin.skyblockId.isEmpty(), "Skin should match diamond swords, got " + skin.item + " " + skin.skyblockId);
			check(skin.look().is(Items.NETHERITE_SWORD), "Skin look should be the off-hand netherite sword, got " + skin.look());
			check(ItemSkins.skinned(client.player.getMainHandItem()).is(Items.NETHERITE_SWORD), "Held diamond sword should be drawn as netherite");
			check(ItemSkins.skinned(new ItemStack(Items.IRON_SWORD)).is(Items.IRON_SWORD), "Other items keep their look");
			showHud(client);
		});
		context.waitTicks(5);
		context.takeScreenshot("32b_sword_skin_third_person");
		setCamera(context, CameraType.FIRST_PERSON);
		context.waitTicks(10);
		context.takeScreenshot("32c_sword_skin_first_person");
		setCamera(context, CameraType.THIRD_PERSON_FRONT);

		// SkyBlock item: matched by its SkyBlock id, not just its type
		singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:iron_sword[minecraft:custom_data={id:\"HYPERION\"},minecraft:custom_name=\"Hyperion\"]");
		context.waitTicks(5);
		context.runOnClient(VanityClientGameTest::showHud);
		context.setScreen(VanityScreen::hand);
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("New skin"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).pickerCellCenter("HYPERION"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Look item ID"));
		context.getInput().typeChars("golden_sword");
		context.waitTicks(3);
		context.takeScreenshot("32d_wardrobe_skyblock_skin");
		context.setScreen(() -> null);
		context.waitTicks(3);
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.itemSkins.size() == 2, "A second skin should be made for the Hyperion, got " + config.itemSkins.size());
			ItemSkin skin = config.itemSkins.get(1);
			check(skin.skyblockId.equals("HYPERION"), "Skin should match the SkyBlock id HYPERION, got '" + skin.skyblockId + "'");
			check(skin.look().is(Items.GOLDEN_SWORD), "Typed look should be a golden sword, got " + skin.lookItem);
			check(ItemSkins.skinned(client.player.getMainHandItem()).is(Items.GOLDEN_SWORD), "Hyperion should be drawn as a golden sword");
			check(ItemSkins.skinned(new ItemStack(Items.IRON_SWORD)).is(Items.IRON_SWORD), "A plain iron sword (no SkyBlock id) must not get the Hyperion skin");
		});
		context.takeScreenshot("32e_skyblock_item_skin");
		cleanView(context);
	}

	/**
	 * Easier SkyBlock looks: grab an item from an inventory with the Grab key, use it from the picker, carry a skinned
	 * item on the cursor without it going back to its real texture, and type SkyBlock ids as looks.
	 */
	private static void testGrabPickAndSkyblockIds(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("item replace entity @p inventory.0 with minecraft:stone_sword[minecraft:custom_data={id:\"DARK_CLAYMORE\"},minecraft:custom_name=\"Dark Claymore\"]");
		context.waitTicks(5);
		context.runOnClient(client -> {
			VanityClient.config().picks.clear();
			showHud(client);
		});

		// Hover the Dark Claymore in the inventory and press the Grab key (survival, so it's the normal inventory)
		singleplayer.getServer().runCommand("gamemode survival @p");
		context.waitTicks(5);
		context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
		context.waitTicks(5);
		context.getInput().setCursorPos(slotCenter(context, 9)[0], slotCenter(context, 9)[1]);
		context.waitTicks(2);
		context.getInput().pressKey(VanityClient.grabKey());
		context.waitTicks(3);
		context.runOnClient(client -> {
			List<ItemStack> picks = VanityClient.config().picks(client.player.registryAccess());
			check(picks.size() == 1 && ItemSkins.skyblockId(picks.get(0)).equals("DARK_CLAYMORE"), "The Grab key should save the hovered Dark Claymore, got " + picks);
		});
		context.takeScreenshot("34a_grabbed_with_key");

		// Pick up the skinned Hyperion: on the cursor it must keep its skin
		int hotbar = context.computeOnClient(client -> 36 + client.player.getInventory().getSelectedSlot());
		double[] hyperion = slotCenter(context, hotbar);
		context.getInput().setCursorPos(hyperion[0], hyperion[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.getInput().setCursorPos(hyperion[0] + 60, hyperion[1] - 90);
		context.waitTicks(3);
		context.runOnClient(client -> {
			ItemStack carried = client.player.containerMenu.getCarried();
			check(ItemSkins.skyblockId(carried).equals("HYPERION"), "The Hyperion should be on the cursor, got " + carried);
			check(VanityCosmetics.inventoryIcon(carried).is(Items.GOLDEN_SWORD), "The item on the cursor should keep its skin");
		});
		context.takeScreenshot("34b_carried_item_keeps_skin");
		context.getInput().setCursorPos(hyperion[0], hyperion[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(3);
		context.runOnClient(client -> check(client.player.containerMenu.getCarried().isEmpty(), "The Hyperion should be put back"));
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("gamemode creative @p");

		// Use the grabbed Dark Claymore as the Hyperion's look, from the picker
		context.setScreen(VanityScreen::hand);
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Skin:"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).menuRowCenter("Hyperion → Golden Sword"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Pick"));
		context.waitTicks(3);
		context.runOnClient(client -> check(((VanityScreen) client.gui.screen()).pickerShown(), "Pick should open the item picker"));
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		context.takeScreenshot("34c_item_picker");
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).pickerCellCenter("DARK_CLAYMORE"));
		context.waitTicks(3);
		context.takeScreenshot("34d_hand_tab_dark_claymore_look");
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			ItemStack drawn = ItemSkins.skinned(client.player.getMainHandItem());
			check(ItemSkins.skyblockId(drawn).equals("DARK_CLAYMORE") && drawn.is(Items.STONE_SWORD), "The Hyperion should be drawn as the grabbed Dark Claymore, got " + drawn);
		});

		// Typed SkyBlock ids: work without owning the item, so packs that texture by id show it
		context.runOnClient(client -> {
			check("HYPERION".equals(VanityConfig.skyblockIdOf("sb:hyperion")), "sb:hyperion is a SkyBlock id");
			check("DARK_CLAYMORE".equals(VanityConfig.skyblockIdOf("DARK_CLAYMORE@stone_sword")), "ALL_CAPS text is a SkyBlock id");
			check(VanityConfig.skyblockIdOf("diamond_sword") == null && VanityConfig.skyblockIdOf("minecraft:DIAMOND") == null, "Item ids aren't SkyBlock ids");
			ItemStack claymore = VanityConfig.resolveLook("DARK_CLAYMORE@stone_sword", Items.IRON_SWORD);
			check(claymore.is(Items.STONE_SWORD) && claymore.getHoverName().getString().equals("Dark Claymore"), "SkyBlock look should be a named stone sword, got " + claymore);

			VanityConfig config = VanityClient.config();
			config.chest.setLook("sb:SUPERIOR_DRAGON_CHESTPLATE");
			ItemStack chest = config.get(CosmeticSlot.CHEST).stack();
			check(chest.is(Items.LEATHER_CHESTPLATE) && ItemSkins.skyblockId(chest).equals("SUPERIOR_DRAGON_CHESTPLATE"), "Typed SkyBlock chestplate should be a leather chestplate with its id, got " + chest);
			check(drawnArmor(client, EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE), "It should be worn as the look");
		});
		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.LOOK));
		context.waitTicks(10);
		context.takeScreenshot("34e_wardrobe_skyblock_id_look");
		context.setScreen(() -> null);
		cleanView(context);
	}

	/** Screen position (window pixels) of a slot in the open inventory screen. */
	private static double[] slotCenter(ClientGameTestContext context, int menuSlot) {
		return context.computeOnClient(client -> {
			InventoryScreen screen = (InventoryScreen) client.gui.screen();
			Slot slot = screen.getMenu().getSlot(menuSlot);
			double scale = client.getWindow().getGuiScale();
			int left = (screen.width - 176) / 2;
			int top = (screen.height - 166) / 2;
			return new double[] {(left + slot.x + 8) * scale, (top + slot.y + 8) * scale};
		});
	}

	/** Vanity's own armor designs and armor drawn by you (a PNG in config/vanity/armors), worn through the real armor renderer. */
	private static void testCustomArmors(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) VanityClient.config().get(slot).rune.enabled = false;
			faceForward(client);
			check(CustomArmors.BUILT_IN.size() == 8, "There should be 8 built-in custom armors, got " + CustomArmors.BUILT_IN.size());
		});

		for (CameraType camera : new CameraType[] {CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK}) {
			setCamera(context, camera);

			for (CustomArmors.Armor armor : CustomArmors.BUILT_IN) {
				context.runOnClient(client -> {
					VanityConfig config = VanityClient.config();
					for (CosmeticSlot slot : CosmeticSlot.values()) {
						config.get(slot).setLook(armor.lookId());
						config.get(slot).resetArmorStyle();
					}
					check(VanityClient.equipmentAssets().get(armor.assetKey()) != EquipmentAssetManager.MISSING, armor.name() + " should have armor model layers");
					check(!armor.extras().isEmpty(), armor.name() + " should have 3D pieces");
					ItemStack chest = drawnArmor(client, EquipmentSlot.CHEST);
					check(chest.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().equals(armor.assetKey()), armor.name() + " should be worn with its own textures, got " + chest);
					VanityRenderData data = liveData(client);
					check(data.custom[CosmeticSlot.CHEST.ordinal()] == armor && data.extras[CosmeticSlot.CHEST.ordinal()], armor.name() + " should be drawn with its 3D pieces");
				});
				context.waitTicks(3);
				context.takeScreenshot("35_custom_armor_" + armor.id() + (camera == CameraType.THIRD_PERSON_BACK ? "_back" : ""));
			}
		}

		// The glowing ones at night
		setCamera(context, CameraType.THIRD_PERSON_FRONT);
		singleplayer.getServer().runCommand("time set midnight");
		context.waitTicks(5);

		for (CustomArmors.Armor armor : CustomArmors.BUILT_IN) {
			if (!armor.glow()) continue;
			context.runOnClient(client -> {
				for (CosmeticSlot slot : CosmeticSlot.values()) VanityClient.config().get(slot).setLook(armor.lookId());
			});
			context.waitTicks(3);
			context.takeScreenshot("35_custom_armor_" + armor.id() + "_night");
		}

		singleplayer.getServer().runCommand("time set noon");
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) VanityClient.config().get(slot).extras = false;
		});
		context.waitTicks(3);
		context.takeScreenshot("35_custom_armor_circuit_without_3d");
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) VanityClient.config().get(slot).extras = true;
		});

		// Pick one from the look list
		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.LOOK));
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("All slots look like"));
		context.waitTicks(2);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).menuRowCenter("Void Walker"));
		context.waitTicks(3);
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				check(VanityClient.config().get(slot).item.equals("vanity:void_walker"), slot.label + " should look like Void Walker, got " + VanityClient.config().get(slot).item);
			}
		});
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("All slots look like"));
		context.waitTicks(2);
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		context.takeScreenshot("35b_wardrobe_custom_armor_list");
		context.setScreen(() -> null);

		// Your own armor: copy the template into the folder, reopen the wardrobe to load it
		context.runOnClient(client -> {
			try {
				Path folder = CustomArmors.folder();
				check(Files.isRegularFile(folder.resolve("README.txt")), "The armor folder should have a README");
				Files.copy(folder.resolve("examples/template.png"), folder.resolve("my_armor.png"), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(folder.resolve("examples/template_leggings.png"), folder.resolve("my_armor_leggings.png"), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(folder.resolve("examples/example.png"), folder.resolve("glowy.png"), StandardCopyOption.REPLACE_EXISTING);
				Files.copy(folder.resolve("examples/example_glow.png"), folder.resolve("glowy_glow.png"), StandardCopyOption.REPLACE_EXISTING);
			} catch (java.io.IOException e) {
				throw new AssertionError("Couldn't copy the armor template", e);
			}
		});
		context.setScreen(() -> new VanityScreen(CosmeticSlot.CHEST, VanityScreen.Page.LOOK));
		context.waitTicks(5);
		context.setScreen(() -> null);
		context.runOnClient(client -> {
			CustomArmors.Armor mine = CustomArmors.find("vanity:user/my_armor");
			check(mine != null && mine.user() && mine.name().equals("My Armor"), "Your armor PNG should load as \"My Armor\", got " + mine);
			check(VanityClient.equipmentAssets().get(mine.assetKey()) != EquipmentAssetManager.MISSING, "Your armor should have model layers");
			CustomArmors.Armor glowy = CustomArmors.find("vanity:user/glowy");
			check(glowy != null && glowy.glow(), "glowy.png with glowy_glow.png should load as a glowing armor, got " + glowy);
			check(CustomArmors.find("vanity:user/glowy_glow") == null, "The _glow file isn't an armor by itself");
			for (CosmeticSlot slot : CosmeticSlot.values()) VanityClient.config().get(slot).setLook(mine.lookId());
		});
		context.waitTicks(3);
		context.takeScreenshot("35c_your_own_armor_png");
	}

	/** Hit and kill effects on a real target: hit a pig, it dies, the kill effect plays where it was. */
	private static void testCombatEffects(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		KillEffect[] kills = {KillEffect.FIREWORK, KillEffect.LIGHTNING, KillEffect.SOUL_BURST, KillEffect.RAINBOW_RING};
		HitEffect[] hits = {HitEffect.SPARKS, HitEffect.LIGHTNING, HitEffect.SOUL, HitEffect.RAINBOW};

		for (int i = 0; i < kills.length; i++) {
			KillEffect kill = kills[i];
			HitEffect hit = hits[i];
			context.runOnClient(client -> {
				VanityConfig config = VanityClient.config();
				config.combat.hit = hit;
				config.combat.kill = kill;
				config.combat.color = 0x3FE0FF;
				CombatParticles.lastHitParticles = 0;
				CombatParticles.lastKillParticles = 0;
			});
			singleplayer.getServer().runCommand("kill @e[type=minecraft:pig]");
			singleplayer.getServer().runCommand("execute at @p run summon minecraft:pig ^1.4 ^ ^0.6 {NoAI:1b,Health:1f,Silent:1b}");
			context.waitTicks(5);
			context.runOnClient(client -> {
				Entity pig = null;
				for (Entity entity : client.level.entitiesForRendering()) {
					if (entity.getType() == EntityTypes.PIG && entity.isAlive()) pig = entity;
				}
				check(pig != null, "A pig should be there to hit");
				client.gameMode.attack(client.player, pig);
				check(CombatParticles.lastHitParticles > 0, hit.label + " hit effect should play");
			});
			context.waitTicks(1);
			if (i == 0) context.takeScreenshot("36a_hit_effect_sparks");
			context.waitTicks(4);
			context.runOnClient(client -> check(CombatParticles.lastKillParticles > 0, kill.label + " kill effect should play when the pig dies"));
			context.waitTicks(i == 0 ? 6 : 3);
			context.takeScreenshot("36_kill_effect_" + kill.name().toLowerCase(java.util.Locale.ROOT));
		}

		// Try it, from the wardrobe
		context.runOnClient(client -> {
			VanityClient.config().combat.kill = KillEffect.TOTEM;
			CombatParticles.lastKillParticles = 0;
		});
		context.setScreen(VanityScreen::hand);
		context.waitTicks(3);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Hit & kill"));
		context.waitTicks(3);
		context.getInput().setCursorPos(1, 1);
		context.waitTicks(2);
		context.takeScreenshot("36e_wardrobe_hit_and_kill");
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Try it"));
		context.waitTicks(14);
		context.runOnClient(client -> {
			check(client.gui.screen() == null, "Try it should close the wardrobe");
			check(CombatParticles.lastKillParticles > 0, "Try it should play the kill effect");
			// The test's mouse moves turn the view once the wardrobe closes; face the camera again
			client.player.setXRot(0.0F);
		});
		context.waitTicks(1);
		context.takeScreenshot("36f_try_it_totem");
		context.runOnClient(client -> {
			VanityClient.config().combat.hit = HitEffect.NONE;
			VanityClient.config().combat.kill = KillEffect.NONE;
		});
	}

	/** Auto-wear an outfit in this world: switching away and "joining" again puts it back on. */
	private static void testAutoOutfit(ClientGameTestContext context) {
		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			check(config.outfits.size() >= 2, "Outfits from the earlier test should still be saved");
			config.wearOutfit(0);
		});
		context.setScreen(() -> new VanityScreen(CosmeticSlot.HEAD, VanityScreen.Page.OUTFITS));
		context.waitTicks(5);
		clickAt(context, client -> ((VanityScreen) client.gui.screen()).widgetCenter("Auto-wear"));
		context.waitTicks(3);
		context.takeScreenshot("33a_wardrobe_auto_wear");
		context.setScreen(() -> null);

		context.runOnClient(client -> {
			VanityConfig config = VanityClient.config();
			String key = AutoOutfits.currentKey(client);
			check(key != null && key.startsWith("world:"), "Singleplayer key should be the world, got " + key);
			check(config.outfits.get(0).servers.contains(key), "First outfit should be set to auto-wear in this world");
			check(config.outfitFor(key) == config.outfits.get(0), "This world should map to the first outfit");

			config.wearOutfit(1);
			check(config.chest.item.equals("minecraft:netherite_chestplate"), "Switched to the netherite outfit");
			AutoOutfits.onJoin(client);
			check(config.outfit == 0 && config.chest.item.equals("minecraft:leather_chestplate"), "Joining should put the auto outfit back on, got " + config.chest.item);
			showHud(client);
		});
		context.waitTicks(5);
		context.takeScreenshot("33b_auto_outfit_on_join");
		cleanView(context);
	}

	private static final String PACK = "vanity_test_pack";
	private static final String[] PACK_FILES = {
			"pack.mcmeta",
			"assets/minecraft/textures/entity/equipment/humanoid/gold.png",
			"assets/minecraft/textures/entity/equipment/humanoid_leggings/gold.png"
	};

	/** Installs a real texture pack into the resourcepacks folder, turns it on and reloads resources (like F3+T). */
	private static void enableTestTexturePack(ClientGameTestContext context) {
		context.runOnClient(client -> {
			Path packDir = client.gameDirectory.toPath().resolve("resourcepacks").resolve(PACK);

			for (String file : PACK_FILES) {
				Path target = packDir.resolve(file);

				try (InputStream in = VanityClientGameTest.class.getResourceAsStream("/" + PACK + "/" + file)) {
					if (in == null) throw new AssertionError("Missing test pack file " + file);
					Files.createDirectories(target.getParent());
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			PackRepository packs = client.getResourcePackRepository();
			packs.reload();

			if (!packs.addPack("file/" + PACK)) {
				throw new AssertionError("Test pack was not found. Available packs: " + packs.getAvailableIds());
			}
		});

		CompletableFuture<Void> reload = context.computeOnClient(Minecraft::reloadResourcePacks);
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 20 * 120);
		context.runOnClient(client -> {
			if (!client.getResourcePackRepository().getSelectedIds().contains("file/" + PACK)) {
				throw new AssertionError("Test pack is not active after reload");
			}
		});
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

	/** Turns the player to face the third-person front camera straight on. */
	private static void faceForward(Minecraft client) {
		client.player.setYRot(0.0F);
		client.player.setXRot(0.0F);
		client.player.yBodyRot = 0.0F;
		client.player.yHeadRot = 0.0F;
	}

	/** The player's Vanity render data for this frame. */
	private static VanityRenderData liveData(Minecraft client) {
		AvatarRenderState state = (AvatarRenderState) client.getEntityRenderDispatcher().getRenderer(client.player).createRenderState(client.player, 1.0F);
		return state.getData(VanityClient.RENDER_DATA);
	}

	/** Shows the HUD (hotbar, action bar) with chat and toasts cleared. */
	private static void showHud(Minecraft client) {
		client.gui.toastManager().clear();
		client.gui.hud.getChat().clearMessages(false);
		if (client.gui.hud.isHidden()) client.gui.hud.toggle();
	}

	private static void setCamera(ClientGameTestContext context, CameraType cameraType) {
		context.runOnClient(client -> client.options.setCameraType(cameraType));
	}

	private static void clearCosmetics(ClientGameTestContext context) {
		context.runOnClient(client -> {
			for (CosmeticSlot slot : CosmeticSlot.values()) {
				set(VanityClient.config().get(slot), "", FitMode.STRETCH);
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
		settings.setLook(item);
		settings.mode = mode;
		settings.invisible = false;
		settings.onlyWhenWorn = true;
		settings.resetArmorStyle();
		settings.hideArmor = true;
		settings.resetTransform();
	}

	private static void clickAt(ClientGameTestContext context, java.util.function.Function<net.minecraft.client.Minecraft, double[]> where) {
		double[] spot = context.computeOnClient(client -> {
			double[] gui = where.apply(client);
			check(gui != null, "Couldn't find the thing to click");
			double scale = client.getWindow().getGuiScale();
			return new double[] {gui[0] * scale, gui[1] * scale};
		});
		context.getInput().setCursorPos(spot[0], spot[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}
}
