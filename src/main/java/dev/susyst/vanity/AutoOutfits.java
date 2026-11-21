package dev.susyst.vanity;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import dev.susyst.vanity.config.Outfit;
import dev.susyst.vanity.config.VanityConfig;

/** Puts on the outfit picked for a server or singleplayer world when you join it. */
public final class AutoOutfits {
	private AutoOutfits() {
	}

	/**
	 * A key for the server or world you're in: "server:hypixel.net" (lowercase, no port, no leading "mc."/"play.")
	 * or "world:My World". Null when not in one.
	 */
	public static String currentKey(Minecraft client) {
		MinecraftServer local = client.getSingleplayerServer();
		if (local != null) return "world:" + local.getWorldData().getLevelName();

		ServerData server = client.getCurrentServer();
		if (server == null || server.ip == null || server.ip.isBlank()) return null;
		String host = server.ip.trim().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
		if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		if (host.startsWith("mc.")) host = host.substring(3);
		else if (host.startsWith("play.")) host = host.substring(5);
		return "server:" + host;
	}

	/** A readable name for a key: "hypixel.net" or "this world". */
	public static String describe(String key) {
		if (key == null) return "here";
		if (key.startsWith("server:")) return key.substring(7);
		if (key.startsWith("world:")) return "world \"" + key.substring(6) + "\"";
		return key;
	}

	/** Called when you join a server or world. */
	public static void onJoin(Minecraft client) {
		VanityConfig config = VanityClient.config();
		String key = currentKey(client);
		Outfit outfit = config.outfitFor(key);
		if (outfit == null) return;

		config.wearOutfit(config.outfits.indexOf(outfit));
		config.save();
		client.gui.hud.setOverlayMessage(Component.literal("Outfit: ").withColor(0x8FA3B8)
				.append(Component.literal(outfit.name).withColor(0x5BD2FF))
				.append(Component.literal("  (auto on " + describe(key) + ")").withColor(0x8FA3B8)), false);
	}
}
