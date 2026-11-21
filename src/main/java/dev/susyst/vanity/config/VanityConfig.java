package dev.susyst.vanity.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.loader.api.FabricLoader;

/** Persistent Vanity settings, stored in config/vanity.json. */
public final class VanityConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanity");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch for all cosmetics. */
	public boolean enabled = true;
	/** Also show the looks on the icons of the armor you're wearing, in your inventory. */
	public boolean showInInventory = true;
	public SlotSettings head = new SlotSettings();
	public SlotSettings chest = new SlotSettings();
	public SlotSettings legs = new SlotSettings();
	public SlotSettings feet = new SlotSettings();

	public SlotSettings get(CosmeticSlot slot) {
		return switch (slot) {
			case HEAD -> this.head;
			case CHEST -> this.chest;
			case LEGS -> this.legs;
			case FEET -> this.feet;
		};
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
}
