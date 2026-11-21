package dev.susyst.vanity.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * Armor runes: particles around your armor every tick, in shapes (halo, orbit rings, spiral, wings, aura, trail).
 * Spawned client-side, so only you see them, and as always-visible particles so they show even on low particle settings.
 */
public final class RuneParticles {
	private static final RandomSource RANDOM = RandomSource.create();

	private RuneParticles() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || client.isPaused()) return;
		if (player.isInvisible() || player.isSpectator()) return;

		VanityConfig config = VanityClient.config();
		if (!config.enabled) return;
		if (!config.runesInFirstPerson && client.options.getCameraType().isFirstPerson()) return;

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			SlotSettings settings = config.get(slot);
			Rune rune = settings.rune;
			if (!rune.enabled) continue;
			if (settings.onlyWhenWorn && player.getItemBySlot(slot.equipmentSlot).isEmpty()) continue;
			spawn(level, player, slot, rune);
		}
	}

	private static ParticleOptions particle(Rune rune, float time) {
		return switch (rune.effect) {
			case DUST -> new DustParticleOptions(rune.color, rune.size);
			case RAINBOW -> new DustParticleOptions(Mth.hsvToRgb((time * 0.01F + RANDOM.nextFloat() * 0.08F) % 1.0F, 0.85F, 1.0F), rune.size);
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

	/** Some particles fly off on their own; slow them down so the shape stays readable. */
	private static double drift(RuneEffect effect) {
		return switch (effect) {
			case DUST, RAINBOW, FLAME, SOUL_FLAME, COPPER_FLAME, SPARKLE, LIGHTNING, GLOW, SHINE -> 0.0;
			default -> 0.01;
		};
	}

	private static void spawn(ClientLevel level, LocalPlayer player, CosmeticSlot slot, Rune rune) {
		float time = player.tickCount * rune.speed;
		double height = player.getBbHeight();
		double yaw = Math.toRadians(player.yBodyRot);
		// Player-relative axes: forward, right
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -fz;
		double rz = fx;
		RuneShape shape = rune.shapeFor(slot);
		int count = rune.amount;

		for (int k = 0; k < count; k++) {
			double side;
			double up;
			double forward;
			double vy = 0.0;

			switch (shape) {
				case HALO -> {
					double a = time * 0.15 + Math.PI * 2 * k / count + RANDOM.nextFloat() * 0.2;
					side = Math.cos(a) * 0.34;
					forward = Math.sin(a) * 0.34;
					up = height + 0.28;
				}
				case ORBIT -> {
					// Two tilted rings spinning in opposite directions around the chest
					boolean second = k % 2 == 1;
					double a = (second ? -time : time) * 0.13 + Math.PI * 2 * k / count;
					double x = Math.cos(a) * 0.78;
					double z = Math.sin(a) * 0.78;
					double tilt = second ? -0.45 : 0.45;
					side = second ? x * Math.cos(tilt) : x;
					forward = second ? z : z * Math.cos(tilt);
					up = height * 0.6 + (second ? x : z) * Math.sin(tilt);
				}
				case SPIRAL -> {
					boolean strand = k % 2 == 1;
					double phase = (time * 0.035 + (double) k / count * 0.5) % 1.0;
					double a = time * 0.25 + (strand ? Math.PI : 0) + phase * Math.PI * 4;
					side = Math.cos(a) * 0.55;
					forward = Math.sin(a) * 0.55;
					up = phase * height * 1.05;
				}
				case WINGS -> {
					double wingSide = k % 2 == 0 ? 1 : -1;
					double u = RANDOM.nextDouble();
					double flap = 0.18 * Math.sin(time * 0.16);
					double fill = RANDOM.nextDouble() * 0.4 * u;
					side = wingSide * (0.18 + 1.0 * u);
					up = height * 0.76 + 0.55 * Math.sin(Math.PI * u * 0.9) - 0.4 * u * u - fill;
					forward = -0.3 - (0.25 + flap) * u;
				}
				case AURA -> {
					double a = RANDOM.nextDouble() * Math.PI * 2;
					double r = 0.35 + RANDOM.nextDouble() * 0.35;
					side = Math.cos(a) * r;
					forward = Math.sin(a) * r;
					up = RANDOM.nextDouble() * height;
					vy = 0.02;
				}
				case TRAIL -> {
					double speed = player.getDeltaMovement().horizontalDistance();

					if (speed > 0.03) {
						side = (RANDOM.nextDouble() - 0.5) * 0.5;
						forward = -0.25 - RANDOM.nextDouble() * 0.3;
						up = 0.08 + RANDOM.nextDouble() * 0.1;
					} else {
						double a = time * 0.12 + Math.PI * 2 * k / count;
						side = Math.cos(a) * 0.62;
						forward = Math.sin(a) * 0.62;
						up = 0.06;
					}
				}
				default -> {
					side = 0;
					forward = 0;
					up = height * 0.5;
				}
			}

			double x = player.getX() + rx * side + fx * forward;
			double y = player.getY() + up;
			double z = player.getZ() + rz * side + fz * forward;
			double drift = drift(rune.effect);
			double vx = (RANDOM.nextDouble() - 0.5) * drift;
			double vz = (RANDOM.nextDouble() - 0.5) * drift;

			if (rune.effect == RuneEffect.MUSIC) {
				// Note particles use the x speed as their color
				vx = RANDOM.nextDouble();
				vy = 0;
				vz = 0;
			}

			level.addAlwaysVisibleParticle(particle(rune, time), x, y, z, vx, vy, vz);
		}
	}
}
