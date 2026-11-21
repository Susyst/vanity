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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.loader.api.FabricLoader;

/** Persistent Vanity settings, stored in config/vanity.json. */
public final class VanityConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanity");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch for all cosmetics. */
	public boolean enabled = true;
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

	/** Turns an id like "glass" or "minecraft:glass" into an item stack, or EMPTY if unknown. */
	public static ItemStack resolveItem(String id) {
		if (id == null || id.isBlank()) return ItemStack.EMPTY;
		Identifier key = Identifier.tryParse(id.trim().toLowerCase(Locale.ROOT));
		if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return ItemStack.EMPTY;
		return new ItemStack(BuiltInRegistries.ITEM.getValue(key));
	}
}
