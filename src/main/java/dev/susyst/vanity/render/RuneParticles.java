package dev.susyst.vanity.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.Rune;
import dev.susyst.vanity.config.RuneEffect;
import dev.susyst.vanity.config.RuneShape;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;

/**
 * Armor runes: particle shapes (halo, orbit rings, spiral, wings, aura, trail) around your armor.
 *
 * <p>A shape is a fixed set of evenly spaced points ({@link Rune#detail}). Each point is redrawn once every
 * {@link Rune#length} ticks, staggered so a few are redrawn each tick. So the whole shape is always there and the
 * number of particles stays steady: low detail is a clean dotted outline, high detail a solid one.
 *
 * <p>Particles go straight into the particle engine with their lifetime set, so only you see them and they show even on
 * low particle settings.
 */
public final class RuneParticles {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final double GOLDEN_ANGLE = 2.399963;

	private RuneParticles() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.isPaused()) return;
		if (player.isInvisible() || player.isSpectator()) return;

		VanityConfig config = VanityClient.config();
		if (!config.enabled) return;
		if (!config.runesInFirstPerson && client.options.getCameraType().isFirstPerson()) return;

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			SlotSettings settings = config.get(slot);
			Rune rune = settings.rune;
			if (!rune.enabled) continue;
			if (settings.onlyWhenWorn && player.getItemBySlot(slot.equipmentSlot).isEmpty()) continue;
			spawn(client, player, slot, rune);
		}
	}

	private static ParticleOptions particle(Rune rune, float time, int point, int points) {
		return switch (rune.effect) {
			case DUST -> new DustParticleOptions(rune.color, rune.size);
			case RAINBOW -> new DustParticleOptions(Mth.hsvToRgb((time * 0.008F + (float) point / points) % 1.0F, 0.85F, 1.0F), rune.size);
			case FLAME -> ParticleTypes.FLAME;
			case SOUL_FLAME -> ParticleTypes.SOUL_FIRE_FLAME;
			case COPPER_FLAME -> ParticleTypes.COPPER_FIRE_FLAME;
			case ENCHANT -> ParticleTypes.ENCHANT;
			case SPARKLE -> ParticleTypes.END_ROD;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case HEARTS -> ParticleTypes.HEART;
			case MUSIC -> ParticleTypes.NOTE;
			case SNOW -> ParticleTypes.SNOWFLAKE;
			case PETALS -> ParticleTypes.CHERRY_LEAVES;
			case VOID -> ParticleTypes.REVERSE_PORTAL;
			case TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
			case GLOW -> ParticleTypes.GLOW;
			case SOULS -> ParticleTypes.SCULK_SOUL;
			case FIREFLIES -> ParticleTypes.FIREFLY;
			case SHINE -> ParticleTypes.WAX_ON;
		};
	}

	/** A point of the shape, in player space: x = right, y = up from the feet, z = forward. */
	private record Point(double x, double y, double z) {
	}

	private static Point shapePoint(RuneShape shape, int k, int n, float t, double height) {
		return switch (shape) {
			case HALO -> {
				double a = Math.PI * 2 * k / n + t * 0.06;
				yield new Point(Math.cos(a) * 0.36, height + 0.3, Math.sin(a) * 0.36);
			}
			case ORBIT -> {
				// Two tilted rings spinning in opposite directions
				boolean second = (k & 1) == 1;
				int m = (n + 1) / 2;
				double a = Math.PI * 2 * (k / 2) / m + (second ? -t : t) * 0.05;
				double x = Math.cos(a) * 0.85;
				double z = Math.sin(a) * 0.85;
				double tilt = second ? -0.45 : 0.45;
				yield second
						? new Point(x * Math.cos(tilt), height * 0.6 + x * Math.sin(tilt), z)
						: new Point(x, height * 0.6 + z * Math.sin(tilt), z * Math.cos(tilt));
			}
			case SPIRAL -> {
				// Two strands winding up the body, turning slowly
				boolean second = (k & 1) == 1;
				int m = (n + 1) / 2;
				double s = (k / 2 + 0.5) / m;
				double a = s * Math.PI * 4 + (second ? Math.PI : 0) + t * 0.06;
				yield new Point(Math.cos(a) * 0.55, s * height * 1.02, Math.sin(a) * 0.55);
			}
			case WINGS -> {
				// Outline of two wings behind the back, flapping
				double side = (k & 1) == 0 ? 1 : -1;
				int m = (n + 1) / 2;
				double u = (k / 2 + 0.5) / m;
				double flap = 0.16 * Math.sin(t * 0.12);
				double top = height * 0.76 + 0.55 * Math.sin(Math.PI * u * 0.9) - 0.4 * u * u;
				// Every third point is a feather tip below the top edge
				double feather = (k / 2) % 3 == 2 ? 0.3 * u : 0.0;
				yield new Point(side * (0.18 + u), top - feather, -0.3 - (0.25 + flap) * u);
			}
			case AURA -> {
				// Points spread evenly around the body, slowly turning
				double a = k * GOLDEN_ANGLE + t * 0.03;
				yield new Point(Math.cos(a) * 0.62, (k + 0.5) / n * height, Math.sin(a) * 0.62);
			}
			case TRAIL -> {
				double a = Math.PI * 2 * k / n + t * 0.05;
				yield new Point(Math.cos(a) * 0.62, 0.06, Math.sin(a) * 0.62);
			}
		};
	}

	private static void spawn(Minecraft client, LocalPlayer player, CosmeticSlot slot, Rune rune) {
		int tick = player.tickCount;
		float time = tick * rune.speed;
		double height = player.getBbHeight();
		double yaw = Math.toRadians(player.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		RuneShape shape = rune.shapeFor(slot);
		int points = rune.detail;
		int length = rune.length;
		boolean moving = player.getDeltaMovement().horizontalDistance() > 0.03;

		for (int k = 0; k < points; k++) {
			// Each point is redrawn once per `length` ticks, staggered across the points
			if ((tick + k * length / points) % length != 0) continue;

			Point p;

			if (shape == RuneShape.TRAIL && moving) {
				// While moving, the trail streams out behind your feet
				p = new Point((RANDOM.nextDouble() - 0.5) * 0.5, 0.08 + RANDOM.nextDouble() * 0.1, -0.25 - RANDOM.nextDouble() * 0.3);
			} else {
				p = shapePoint(shape, k, points, time, height);
			}

			double x = player.getX() + rx * p.x + fx * p.z;
			double y = player.getY() + p.y;
			double z = player.getZ() + rz * p.x + fz * p.z;
			double vx = 0;
			double vy = shape == RuneShape.AURA ? 0.012 : 0;
			double vz = 0;

			// Note particles use the x speed as their color
			if (rune.effect == RuneEffect.MUSIC) vx = (double) k / points;

			Particle particle = client.particleEngine.createParticle(particle(rune, time, k, points), x, y, z, vx, vy, vz);

			if (particle != null) {
				particle.setLifetime(length + 2);
			}
		}
	}
}
