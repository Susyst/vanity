package dev.susyst.vanity.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import dev.susyst.vanity.render.CustomArmors;

import net.fabricmc.loader.api.FabricLoader;

/** Persistent Vanity settings, stored in config/vanity.json. */
public final class VanityConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanity");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch for all cosmetics. */
	public boolean enabled = true;
	/** Also show the looks on the icons of the armor you're wearing, in your inventory. */
	public boolean showInInventory = true;
	/** Show runes in first person too (they can get in the way). */
	public boolean runesInFirstPerson;
	public SlotSettings head = new SlotSettings();
	public SlotSettings chest = new SlotSettings();
	public SlotSettings legs = new SlotSettings();
	public SlotSettings feet = new SlotSettings();
	/** Saved outfits, switched with the Next outfit key. */
	public List<Outfit> outfits = new ArrayList<>();
	/** Index of the outfit worn or saved last, or -1. */
	public int outfit = -1;
	/** Hit and kill effects. */
	public CombatEffects combat = new CombatEffects();
	/** Held-item skins. */
	public List<ItemSkin> itemSkins = new ArrayList<>();
	/** Items grabbed with the Grab key (hover an item in any inventory and press it), to use as looks. */
	public List<JsonElement> picks = new ArrayList<>();

	public static final int MAX_PICKS = 36;

	public static final int MAX_OUTFITS = 12;

	public SlotSettings get(CosmeticSlot slot) {
		SlotSettings settings = switch (slot) {
			case HEAD -> this.head;
			case CHEST -> this.chest;
			case LEGS -> this.legs;
			case FEET -> this.feet;
		};
		settings.slot = slot;
		return settings;
	}

	/** The outfit worn or saved last, or null. */
	public Outfit currentOutfit() {
		return this.outfit >= 0 && this.outfit < this.outfits.size() ? this.outfits.get(this.outfit) : null;
	}

	/** Wears the outfit at {@code index}. */
	public Outfit wearOutfit(int index) {
		if (index < 0 || index >= this.outfits.size()) return null;
		this.outfit = index;
		Outfit outfit = this.outfits.get(index);
		outfit.wear(this);
		return outfit;
	}

	/** Wears the next (+1) or previous (-1) outfit, wrapping around. Null if none are saved. */
	public Outfit wearNextOutfit(int direction) {
		int count = this.outfits.size();
		if (count == 0) return null;
		int index = this.outfit < 0 ? (direction > 0 ? 0 : count - 1) : Math.floorMod(this.outfit + direction, count);
		return this.wearOutfit(index);
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("vanity.json");
	}

	public static VanityConfig load() {
		Path path = path();
		VanityConfig config = null;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				config = GSON.fromJson(reader, VanityConfig.class);
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}

		if (config == null) config = new VanityConfig();
		config.sanitize();
		return config;
	}

	public void save() {
		Path path = path();

		try {
			Files.createDirectories(path.getParent());
			Path temp = path.resolveSibling("vanity.json.tmp");

			try (Writer writer = Files.newBufferedWriter(temp)) {
				GSON.toJson(this, writer);
			}

			Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", path, e);
		}
	}

	private void sanitize() {
		if (this.head == null) this.head = new SlotSettings();
		if (this.chest == null) this.chest = new SlotSettings();
		if (this.legs == null) this.legs = new SlotSettings();
		if (this.feet == null) this.feet = new SlotSettings();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			this.get(slot).sanitize();
		}

		if (this.outfits == null) this.outfits = new ArrayList<>();
		this.outfits.removeIf(java.util.Objects::isNull);
		while (this.outfits.size() > MAX_OUTFITS) this.outfits.removeLast();
		for (Outfit outfit : this.outfits) outfit.sanitize();
		if (this.outfit >= this.outfits.size()) this.outfit = -1;

		if (this.combat == null) this.combat = new CombatEffects();
		this.combat.sanitize();
		if (this.picks == null) this.picks = new ArrayList<>();
		this.picks.removeIf(json -> json == null || json.isJsonNull());
		if (this.itemSkins == null) this.itemSkins = new ArrayList<>();
		this.itemSkins.removeIf(java.util.Objects::isNull);
		for (ItemSkin skin : this.itemSkins) skin.sanitize();
	}

	/** The outfit set to be worn on this server or world, or null. */
	public Outfit outfitFor(String serverKey) {
		if (serverKey == null) return null;

		for (Outfit outfit : this.outfits) {
			if (outfit.servers.contains(serverKey)) return outfit;
		}

		return null;
	}

	/** Makes {@code outfit} the one worn on this server (or stops that if it already was). Only one outfit per server. */
	public void toggleAutoOutfit(Outfit outfit, String serverKey) {
		if (outfit == null || serverKey == null) return;
		boolean wasOn = outfit.servers.contains(serverKey);
		for (Outfit other : this.outfits) other.servers.remove(serverKey);
		if (!wasOn) outfit.servers.add(serverKey);
	}

	/** The current world's registries, needed to read exact saved items. Null when not in a world. */
	public static HolderLookup.Provider registries() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.level != null ? minecraft.level.registryAccess() : null;
	}

	/** Saves an item with all its data as JSON, or null if it can't be saved. */
	public static JsonElement encodeStack(ItemStack stack, HolderLookup.Provider registries) {
		return ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), stack)
				.resultOrPartial(error -> LOGGER.warn("Could not save item {}: {}", stack, error))
				.orElse(null);
	}

	/** Reads an item saved by {@link #encodeStack}, or EMPTY if it can't be read. */
	public static ItemStack decodeStack(JsonElement json, HolderLookup.Provider registries) {
		if (json == null) return ItemStack.EMPTY;
		return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json)
				.resultOrPartial(error -> LOGGER.warn("Could not read saved item: {}", error))
				.orElse(ItemStack.EMPTY);
	}

	/** Turns an id like "glass" or "minecraft:glass" into an item stack, or EMPTY if unknown. */
	public static ItemStack resolveItem(String id) {
		if (id == null || id.isBlank()) return ItemStack.EMPTY;
		Identifier key = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
		return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
	}

	/**
	 * A look from what was typed: an item id ("netherite_sword"), or a SkyBlock id ("sb:DARK_CLAYMORE", or just
	 * "DARK_CLAYMORE"). A SkyBlock id makes an item of {@code base} (or "@stone_sword" after the id) carrying that id
	 * and its name, so texture packs and mods that texture SkyBlock items by id show it, even if you don't own one.
	 */
	public static ItemStack resolveLook(String text, Item base) {
		if (CustomArmors.isCustomId(text)) return CustomArmors.stack(text, base);
		ItemStack plain = resolveItem(text);
		if (!plain.isEmpty()) return plain;
		String id = skyblockIdOf(text);
		if (id == null) return ItemStack.EMPTY;

		Item item = base;
		int at = text.indexOf('@');

		if (at > 0) {
			ItemStack chosen = resolveItem(text.substring(at + 1));
			if (!chosen.isEmpty()) item = chosen.getItem();
		}

		if (item == null || item == Items.AIR) item = Items.PAPER;
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(prettyName(id)).withStyle(style -> style.withItalic(false)));
		return stack;
	}

	/** The SkyBlock id in typed text ("sb:dark_claymore" or "DARK_CLAYMORE@stone_sword" give "DARK_CLAYMORE"), or null. */
	public static String skyblockIdOf(String text) {
		if (text == null) return null;
		String t = text.trim();
		int at = t.indexOf('@');
		if (at >= 0) t = t.substring(0, at);
		boolean prefixed = t.regionMatches(true, 0, "sb:", 0, 3);
		if (prefixed) t = t.substring(3).toUpperCase(Locale.ROOT);
		// Without "sb:", only ALL_CAPS text counts, so a mistyped item id isn't taken for one
		if (!t.matches("[A-Z0-9_;:\\-]*[A-Z][A-Z0-9_;:\\-]*")) return null;
		if (!prefixed && t.indexOf(':') >= 0) return null;
		return t;
	}

	/** "DARK_CLAYMORE" as "Dark Claymore". */
	public static String prettyName(String id) {
		StringBuilder name = new StringBuilder();

		for (String word : id.toLowerCase(Locale.ROOT).split("[_;:]+")) {
			if (word.isEmpty()) continue;
			if (!name.isEmpty()) name.append(' ');
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}

		return name.toString();
	}

	/** Items grabbed with the Grab key, newest first, saved with all their data. */
	public List<ItemStack> picks(HolderLookup.Provider registries) {
		List<ItemStack> stacks = new ArrayList<>();

		for (JsonElement json : this.picks) {
			ItemStack stack = decodeStack(json, registries);
			if (!stack.isEmpty()) stacks.add(stack);
		}

		return stacks;
	}

	/** Saves a grabbed item to the front of the list (moving it there if it was already grabbed). */
	public void addPick(ItemStack stack, HolderLookup.Provider registries) {
		if (stack.isEmpty()) return;
		ItemStack single = stack.copyWithCount(1);
		List<ItemStack> old = this.picks(registries);
		old.removeIf(other -> ItemStack.isSameItemSameComponents(other, single));
		old.addFirst(single);
		while (old.size() > MAX_PICKS) old.removeLast();
		this.picks.clear();

		for (ItemStack pick : old) {
			JsonElement json = encodeStack(pick, registries);
			if (json != null) this.picks.add(json);
		}
	}

	public void removePick(ItemStack stack, HolderLookup.Provider registries) {
		List<ItemStack> old = this.picks(registries);
		old.removeIf(other -> ItemStack.isSameItemSameComponents(other, stack));
		this.picks.clear();

		for (ItemStack pick : old) {
			JsonElement json = encodeStack(pick, registries);
			if (json != null) this.picks.add(json);
		}
	}
}
