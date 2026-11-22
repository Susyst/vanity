package dev.susyst.vanity.render;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.AbilityEffects;
import dev.susyst.vanity.config.TrailStyle;
import dev.susyst.vanity.config.VanityConfig;

/**
 * SkyBlock item abilities: a trail along teleports and a ring for everything else. Hypixel shows an ability's mana
 * cost above the hotbar when it goes off ("-50 Mana (Instant Transmission)"), which is how a use is noticed; a
 * teleport is the jump in your position that follows (or just came before) it. Right-clicking a known teleport item
 * also counts, in case the mana text is hidden. Only you see the effects.
 */
public final class Abilities {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Pattern MANA = Pattern.compile("-([\\d,.]+) Mana \\(([^)]+)\\)");
	/** Abilities that move you: Instant Transmission (AOTE/AOTV), Ether Transmission (etherwarp), Wither Impact... */
	private static final String[] TELEPORTS = {"Transmission", "Wither Impact", "Teleport", "Warp"};
	/** Items that teleport you when right-clicked. */
	private static final Set<String> TELEPORT_ITEMS = Set.of("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "HYPERION", "VALKYRIE", "ASTRAEA", "SCYLLA",
			"NECRON_BLADE");
	/** Wither blades: their teleport ends in an implosion. */
	private static final Set<String> WITHER_BLADES = Set.of("HYPERION", "VALKYRIE", "ASTRAEA", "SCYLLA", "NECRON_BLADE");
	/** Moving more than this in one tick is a teleport, not walking or flying. */
	private static final double JUMP = 2.4;
	/** The same ability shown again within this many ticks is the same use (the text stays up for a while). */
	private static final int REPEAT = 25;

	private static ClientLevel lastLevel;
	private static Vec3 lastPos;
	private static Vec3 jumpFrom;
	private static Vec3 jumpTo;
	private static long jumpTick = Long.MIN_VALUE;
	private static long armedUntil = Long.MIN_VALUE;
	private static boolean implosion;
	private static String lastAbility = "";
	private static long lastAbilityTick = -1000L;

	/** For tests: the last ability seen, and the particles of the last trail and ring. */
	public static String lastAbilityName = "";
	public static int lastTrailParticles;
	public static int lastRingParticles;

	private Abilities() {
	}

	/** Text above the hotbar (the action bar). */
	public static void onActionBar(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || message == null) return;
		String text = ChatFormatting.stripFormatting(message.getString());
		if (text == null) return;
		Matcher matcher = MANA.matcher(text);
		if (!matcher.find()) return;

		String name = matcher.group(2).trim();
		long now = client.level.getGameTime();
		boolean repeat = name.equals(lastAbility) && now - lastAbilityTick <= REPEAT;
		lastAbility = name;
		lastAbilityTick = now;
		boolean teleport = isTeleport(name);

		// A teleport only draws once the jump is seen, so arming again for the same text is harmless
		if (teleport) arm(client, name.contains("Wither Impact"));
		if (repeat) return;
		lastAbilityName = name;
		if (!teleport) ring(client, client.player.position());
	}

	/** Right-click with a SkyBlock item: teleport items arm the trail. */
	public static void onUse(Minecraft client, String skyblockId) {
		if (skyblockId != null && TELEPORT_ITEMS.contains(skyblockId)) arm(client, WITHER_BLADES.contains(skyblockId));
	}

	public static boolean isTeleport(String ability) {
		for (String word : TELEPORTS) {
			if (ability.contains(word)) return true;
		}

		return false;
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null || level != lastLevel) {
			lastLevel = level;
			lastPos = player == null ? null : player.position();
			jumpTick = Long.MIN_VALUE;
			armedUntil = Long.MIN_VALUE;
			return;
		}

		Vec3 pos = player.position();
		long now = level.getGameTime();

		if (lastPos != null && pos.distanceTo(lastPos) > JUMP) {
			jumpFrom = lastPos;
			jumpTo = pos;
			jumpTick = now;
			if (now <= armedUntil) fire(client);
		}

		lastPos = pos;
	}

	/** Wardrobe "Try it": a teleport trail from a few blocks behind you, ending in a ring. */
	public static void preview(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) return;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		Vec3 to = player.position().add(flat.scale(1.5));
		AbilityEffects effects = VanityClient.config().abilities;
		trail(client.level, to.subtract(flat.scale(7)), to, effects);
		ring(client, to);
	}

	/** Watch for the jump of a teleport for a moment, or draw it now if it already happened. */
	private static void arm(Minecraft client, boolean endsInImplosion) {
		long now = client.level.getGameTime();
		armedUntil = now + 10;
		implosion |= endsInImplosion;
		if (jumpTick != Long.MIN_VALUE && now - jumpTick <= 4) fire(client);
	}

	private static void fire(Minecraft client) {
		VanityConfig config = VanityClient.config();
		AbilityEffects effects = config.abilities;

		if (config.enabled && effects.teleports && effects.style != TrailStyle.NONE) {
			trail(client.level, jumpFrom, jumpTo, effects);
			if (implosion) burst(client.level, jumpTo, effects);
		}

		armedUntil = Long.MIN_VALUE;
		jumpTick = Long.MIN_VALUE;
		implosion = false;
	}

	/** A line of particles from where you were to where you are, at chest height, with a puff at each end. */
	private static void trail(ClientLevel level, Vec3 from, Vec3 to, AbilityEffects effects) {
		Vec3 a = from.add(0, 1.0, 0);
		Vec3 b = to.add(0, 1.0, 0);
		double dist = a.distanceTo(b);
		int n = Math.max(6, (int) Math.round(dist * 5 * effects.amount));
		double jitter = 0.07 * effects.size;
		lastTrailParticles = 0;

		for (int i = 0; i < n; i++) {
			double t = (double) i / (n - 1);
			Vec3 p = a.add(b.subtract(a).scale(t));
			lastTrailParticles += TrailParticles.add(level, TrailParticles.particle(effects.style, i, n, effects.color, effects.size),
					p.x + RANDOM.nextGaussian() * jitter, p.y + RANDOM.nextGaussian() * jitter, p.z + RANDOM.nextGaussian() * jitter,
					RANDOM.nextGaussian() * 0.01, 0.01, RANDOM.nextGaussian() * 0.01);
		}

		for (Vec3 end : new Vec3[] {a, b}) {
			for (int i = 0; i < Math.round(8 * effects.amount); i++) {
				lastTrailParticles += TrailParticles.add(level, TrailParticles.particle(effects.style, i, 8, effects.color, effects.size), end.x, end.y, end.z,
						RANDOM.nextGaussian() * 0.08 * effects.size, RANDOM.nextGaussian() * 0.08 * effects.size, RANDOM.nextGaussian() * 0.08 * effects.size);
			}
		}

		if (effects.sounds) level.playLocalSound(b.x, b.y, b.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35F, 1.6F, false);
	}

	/** Every other ability: two rings spreading out from you. */
	private static void ring(Minecraft client, Vec3 at) {
		VanityConfig config = VanityClient.config();
		AbilityEffects effects = config.abilities;
		if (!config.enabled || !effects.others || effects.style == TrailStyle.NONE) return;
		ClientLevel level = client.level;
		int n = Math.max(8, Math.round(28 * effects.amount));
		lastRingParticles = 0;

		for (int ring = 0; ring < 2; ring++) {
			double speed = (0.16 + ring * 0.08) * effects.size;

			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2 * i / n;
				double x = Math.cos(angle);
				double z = Math.sin(angle);
				lastRingParticles += TrailParticles.add(level, TrailParticles.particle(effects.style, i, n, effects.color, effects.size),
						at.x + x * 0.5, at.y + 0.2 + ring * 0.7, at.z + z * 0.5, x * speed, 0.0, z * speed);
			}
		}

		if (effects.sounds) level.playLocalSound(at.x, at.y + 1, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F, false);
	}

	/** Wither Impact's implosion where you land: particles flying out in every direction. */
	private static void burst(ClientLevel level, Vec3 at, AbilityEffects effects) {
		int n = Math.max(12, Math.round(36 * effects.amount));
		Vec3 c = at.add(0, 1.0, 0);

		for (int i = 0; i < n; i++) {
			double y = 1.0 - 2.0 * (i + 0.5) / n;
			double r = Math.sqrt(1.0 - y * y);
			double a = i * 2.399963;
			double s = 0.25 * effects.size;
			lastTrailParticles += TrailParticles.add(level, TrailParticles.particle(effects.style, i, n, effects.color, effects.size), c.x, c.y, c.z,
					Math.cos(a) * r * s, y * s, Math.sin(a) * r * s);
		}
	}
}
