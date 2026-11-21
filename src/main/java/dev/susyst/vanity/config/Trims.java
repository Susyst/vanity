package dev.susyst.vanity.config;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/** The vanilla armor trim patterns and materials, and colors for the color pickers. */
public final class Trims {
	public static final List<String> PATTERNS = List.of(
			"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib",
			"spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt");

	public static final List<String> MATERIALS = List.of(
			"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin");

	/** Quick colors: the 16 dye colors followed by the trim material colors. */
	public static final List<Integer> PALETTE = List.of(
			0xF9FFFE, 0x9D9D97, 0x474F52, 0x1D1D21, 0x835432, 0xB02E26, 0xF9801D, 0xFED83D,
			0x80C71F, 0x5E7C16, 0x169C9C, 0x3AB3DA, 0x3C44AA, 0x8932B8, 0xC74EBD, 0xF38BAA,
			0xE3D4C4, 0xCFD3D3, 0x625859, 0x971607, 0xB4684D, 0xDEB12D, 0x11A036, 0x6EECD2,
			0x416E97, 0x9A5CC6, 0xEB7114);

	private Trims() {
	}

	/** A representative RGB color for a vanilla trim material, used for see-through and glowing trims. */
	public static int materialColor(String materialId) {
		String name = materialId.startsWith("minecraft:") ? materialId.substring(10) : materialId;
		int index = MATERIALS.indexOf(name);
		return index < 0 ? 0xFFFFFF : PALETTE.get(16 + index);
	}

	/** "minecraft:sentry" for "sentry". */
	public static String id(String name) {
		return "minecraft:" + name;
	}

	/** "Sentry" for "minecraft:sentry". */
	public static String displayName(String id) {
		String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
		if (path.isEmpty()) return id;
		return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1).replace('_', ' ');
	}

	/** The next (or previous) entry of a list after the given id. */
	public static String step(List<String> names, String currentId, int direction) {
		int index = names.indexOf(currentId.startsWith("minecraft:") ? currentId.substring(10) : currentId);
		int next = Math.floorMod(index + direction, names.size());
		if (index < 0) next = direction > 0 ? 0 : names.size() - 1;
		return id(names.get(next));
	}

	/** A real armor trim from the world's trim registries, or null if they aren't available. */
	public static ArmorTrim create(String patternId, String materialId) {
		HolderLookup.Provider registries = VanityConfig.registries();
		if (registries == null) return null;
		Identifier pattern = Identifier.tryParse(patternId);
		Identifier material = Identifier.tryParse(materialId);
		if (pattern == null || material == null) return null;

		Optional<Holder.Reference<TrimPattern>> patternHolder = registries.lookup(Registries.TRIM_PATTERN)
				.flatMap(lookup -> lookup.get(ResourceKey.create(Registries.TRIM_PATTERN, pattern)));
		Optional<Holder.Reference<TrimMaterial>> materialHolder = registries.lookup(Registries.TRIM_MATERIAL)
				.flatMap(lookup -> lookup.get(ResourceKey.create(Registries.TRIM_MATERIAL, material)));

		if (patternHolder.isEmpty() || materialHolder.isEmpty()) return null;
		return new ArmorTrim(materialHolder.get(), patternHolder.get());
	}
}
