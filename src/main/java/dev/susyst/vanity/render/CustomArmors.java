package dev.susyst.vanity.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.NativeImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Armor you draw yourself, loaded from config/vanity/armors/<name>.png and <name>_leggings.png. It's worn through the
 * normal armor renderer, so trims, glint, opacity, glow and runes all work on it.
 */
public final class CustomArmors {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanity");
	public static final String PREFIX = "vanity:";

	/**
	 * A custom armor: {@code id} is "user/<file name>". {@code glow}: it has <id>_glow.png textures drawn full-bright
	 * on top; {@code pulse}: that glow breathes.
	 */
	public record Armor(String id, String name, int color, boolean user, boolean glow, boolean pulse) {
		public String lookId() {
			return PREFIX + this.id;
		}

		public ResourceKey<EquipmentAsset> assetKey() {
			return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("vanity", this.id));
		}

		/** The glow texture for one armor layer (humanoid or humanoid_leggings). */
		public Identifier glowTexture(EquipmentClientInfo.LayerType layerType) {
			return Identifier.fromNamespaceAndPath("vanity", "textures/entity/equipment/" + layerType.getSerializedName() + "/" + this.id + "_glow.png");
		}
	}

	/** The custom armor an armor piece is drawn with, or null if it's normal armor. */
	public static Armor byStack(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) return null;
		Identifier id = equippable.assetId().get().identifier();
		if (!id.getNamespace().equals("vanity")) return null;
		return find(PREFIX + id.getPath());
	}

	private static final int MAX_USER_ARMORS = 32;
	private static final List<Armor> USER = new ArrayList<>();
	private static final Map<Identifier, EquipmentClientInfo> USER_INFO = new HashMap<>();
	private static final List<Identifier> USER_TEXTURES = new ArrayList<>();
	private static String lastScan = "";

	private CustomArmors() {
	}

	public static Path folder() {
		return FabricLoader.getInstance().getConfigDir().resolve("vanity").resolve("armors");
	}

	/** Your own armors, in file name order. */
	public static List<Armor> all() {
		return List.copyOf(USER);
	}

	public static Armor find(String lookId) {
		if (lookId == null || !lookId.startsWith(PREFIX)) return null;
		String id = lookId.substring(PREFIX.length()).toLowerCase(Locale.ROOT);

		for (Armor armor : all()) {
			if (armor.id().equals(id)) return armor;
		}

		return null;
	}

	/** True for any "vanity:..." look id, known or not (so it isn't taken for a SkyBlock id). */
	public static boolean isCustomId(String text) {
		return text != null && text.trim().toLowerCase(Locale.ROOT).startsWith(PREFIX);
	}

	/**
	 * The armor piece to wear for a custom armor look: a leather piece for that slot (so the icon is a leather piece
	 * in the design's color) that's worn with the custom armor's textures.
	 */
	public static ItemStack stack(String lookId, Item base) {
		Armor armor = find(lookId.trim());
		if (armor == null) return ItemStack.EMPTY;
		if (base == null || new ItemStack(base).get(DataComponents.EQUIPPABLE) == null) base = Items.LEATHER_CHESTPLATE;

		ItemStack stack = new ItemStack(base);
		Equippable old = stack.get(DataComponents.EQUIPPABLE);
		stack.set(DataComponents.EQUIPPABLE, Equippable.builder(old.slot()).setEquipSound(old.equipSound()).setAsset(armor.assetKey()).build());
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(armor.color()));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(armor.name() + " " + pieceName(base)).withStyle(style -> style.withItalic(false)));
		return stack;
	}

	private static String pieceName(Item base) {
		if (base == Items.LEATHER_HELMET) return "Helmet";
		if (base == Items.LEATHER_LEGGINGS) return "Leggings";
		if (base == Items.LEATHER_BOOTS) return "Boots";
		return "Chestplate";
	}

	/** The armor model layers for one of your own armors, or null for anything else. */
	public static EquipmentClientInfo userInfo(ResourceKey<EquipmentAsset> key) {
		Identifier id = key.identifier();
		if (!id.getNamespace().equals("vanity") || !id.getPath().startsWith("user/")) return null;
		return USER_INFO.get(id);
	}

	/**
	 * Looks for armor PNGs in config/vanity/armors (making the folder, with a template and an example, the first
	 * time). Cheap when nothing changed. Call on the render thread.
	 */
	public static void reloadUserArmors(Minecraft client) {
		Path folder = folder();

		try {
			if (!Files.isDirectory(folder)) createFolder(folder);
			StringBuilder scan = new StringBuilder();
			List<Path> files = new ArrayList<>();

			try (Stream<Path> list = Files.list(folder)) {
				list.filter(path -> {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					return name.endsWith(".png") && !name.endsWith("_leggings.png") && !name.endsWith("_glow.png") && Files.isRegularFile(path);
				}).sorted().limit(MAX_USER_ARMORS).forEach(files::add);
			}

			for (Path file : files) {
				scan.append(file.getFileName()).append(Files.getLastModifiedTime(file).toMillis());
				for (Path extra : List.of(leggingsFile(file), siblingFile(file, "_glow"), siblingFile(file, "_leggings_glow"))) {
					if (Files.isRegularFile(extra)) scan.append(extra.getFileName()).append(Files.getLastModifiedTime(extra).toMillis());
				}
				scan.append(';');
			}

			if (scan.toString().equals(lastScan)) return;
			lastScan = scan.toString();

			for (Identifier texture : USER_TEXTURES) client.getTextureManager().release(texture);
			USER_TEXTURES.clear();
			USER.clear();
			USER_INFO.clear();

			for (Path file : files) loadUserArmor(client, file);
			LOGGER.info("Vanity: loaded {} custom armor(s) from {}", USER.size(), folder);
		} catch (IOException e) {
			LOGGER.warn("Vanity: couldn't read custom armors from {}", folder, e);
		}
	}

	private static Path leggingsFile(Path file) {
		String name = file.getFileName().toString();
		return file.resolveSibling(name.substring(0, name.length() - 4) + "_leggings.png");
	}

	private static Path siblingFile(Path file, String suffix) {
		String name = file.getFileName().toString();
		return file.resolveSibling(name.substring(0, name.length() - 4) + suffix + ".png");
	}

	private static void loadUserArmor(Minecraft client, Path file) {
		String fileName = file.getFileName().toString();
		String stem = fileName.substring(0, fileName.length() - 4);
		String id = "user/" + stem.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");

		try {
			NativeImage main = read(file);
			if (main == null) return;
			Path legsFile = leggingsFile(file);
			NativeImage legs = Files.isRegularFile(legsFile) ? read(legsFile) : null;
			if (legs == null) legs = copy(main);

			int color = averageColor(main);
			register(client, Identifier.fromNamespaceAndPath("vanity", "textures/entity/equipment/humanoid/" + id + ".png"), main);
			register(client, Identifier.fromNamespaceAndPath("vanity", "textures/entity/equipment/humanoid_leggings/" + id + ".png"), legs);

			// Optional glow: <name>_glow.png (and <name>_leggings_glow.png), drawn full-bright on top
			boolean glow = false;
			NativeImage glowMain = Files.isRegularFile(siblingFile(file, "_glow")) ? read(siblingFile(file, "_glow")) : null;

			if (glowMain != null) {
				NativeImage glowLegs = Files.isRegularFile(siblingFile(file, "_leggings_glow")) ? read(siblingFile(file, "_leggings_glow")) : copy(glowMain);
				if (glowLegs == null) glowLegs = copy(glowMain);
				register(client, Identifier.fromNamespaceAndPath("vanity", "textures/entity/equipment/humanoid/" + id + "_glow.png"), glowMain);
				register(client, Identifier.fromNamespaceAndPath("vanity", "textures/entity/equipment/humanoid_leggings/" + id + "_glow.png"), glowLegs);
				glow = true;
			}

			Identifier layer = Identifier.fromNamespaceAndPath("vanity", id);
			USER_INFO.put(layer, EquipmentClientInfo.builder()
					.addLayers(EquipmentClientInfo.LayerType.HUMANOID, new EquipmentClientInfo.Layer(layer))
					.addLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, new EquipmentClientInfo.Layer(layer))
					.build());
			USER.add(new Armor(id, prettyName(stem), color, true, glow, false));
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Vanity: couldn't load custom armor {}", file, e);
		}
	}

	/** Reads an armor PNG; it must be twice as wide as tall (64x32, 128x64, ...). */
	private static NativeImage read(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			NativeImage image = NativeImage.read(in);

			if (image.getWidth() != image.getHeight() * 2 || image.getWidth() < 64) {
				LOGGER.warn("Vanity: {} is {}x{}; armor textures must be 64x32 (or 128x64, ...)", file, image.getWidth(), image.getHeight());
				image.close();
				return null;
			}

			return image;
		}
	}

	private static NativeImage copy(NativeImage image) {
		NativeImage copy = new NativeImage(image.getWidth(), image.getHeight(), false);
		copy.copyFrom(image);
		return copy;
	}

	private static void register(Minecraft client, Identifier id, NativeImage image) {
		client.getTextureManager().register(id, new DynamicTexture(() -> "Vanity custom armor " + id, image));
		USER_TEXTURES.add(id);
	}

	/** Average color of the visible pixels, for the armor's icon. */
	private static int averageColor(NativeImage image) {
		long r = 0;
		long g = 0;
		long b = 0;
		long count = 0;

		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if ((argb >>> 24) < 128) continue;
				r += (argb >> 16) & 0xFF;
				g += (argb >> 8) & 0xFF;
				b += argb & 0xFF;
				count++;
			}
		}

		if (count == 0) return 0xA06540;
		return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
	}

	private static String prettyName(String stem) {
		StringBuilder name = new StringBuilder();

		for (String word : stem.split("[_\\- ]+")) {
			if (word.isEmpty()) continue;
			if (!name.isEmpty()) name.append(' ');
			name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}

		return name.isEmpty() ? stem : name.toString();
	}

	/** Makes the folder with a README, a template (each face in its own color) and an example to copy. */
	private static void createFolder(Path folder) throws IOException {
		Files.createDirectories(folder.resolve("examples"));
		for (String name : List.of("template.png", "template_leggings.png", "example.png", "example_leggings.png", "example_glow.png")) {
			try (InputStream in = CustomArmors.class.getResourceAsStream("/assets/vanity/armor_folder/" + name)) {
				if (in != null) Files.copy(in, folder.resolve("examples").resolve(name));
			}
		}

		Files.writeString(folder.resolve("README.txt"), """
				Vanity custom armors
				====================

				Put your own armor here and it shows up in the wardrobe's look list (and as vanity:user/<name>).

				  <name>.png           helmet, chestplate and boots (64x32, like vanilla armor textures)
				  <name>_leggings.png  leggings (optional; <name>.png is used if it's missing)
				  <name>_glow.png      optional: pixels that glow in the dark, drawn over the armor at full
				                       brightness (same layout; leave everything else transparent).
				                       <name>_leggings_glow.png does the same for leggings.

				Bigger sizes work too, as long as it's twice as wide as tall (128x64, 256x128).
				examples/template.png shows which pixels go on which face: front is red, sides orange and blue,
				back purple, top yellow. examples/example.png is a finished armor to start from.
				Files in the examples folder aren't loaded; copy one up here to use it.

				Changes load the next time you open the wardrobe.
				""");
	}
}
