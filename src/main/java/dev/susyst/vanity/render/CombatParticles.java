package dev.susyst.vanity.render;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CombatEffects;
import dev.susyst.vanity.config.HitEffect;
import dev.susyst.vanity.config.KillEffect;
import dev.susyst.vanity.config.VanityConfig;

/** Hit effects when you hit something, and kill effects when something you hit recently dies. Only you see them. */
public final class CombatParticles {
	private static final RandomSource RANDOM = RandomSource.create();
	/** Something you hit counts as your kill if it dies within this many ticks. */
	private static final int KILL_WINDOW = 100;
	private static final Map<Integer, Long> RECENT = new LinkedHashMap<>();
	private static int previewTicks = -1;
	private static Vec3 previewAt = Vec3.ZERO;

	/** For tests: how many particles the last hit / kill effect made. */
	public static int lastHitParticles;
	public static int lastKillParticles;

	private CombatParticles() {
	}

	/** Called when you attack an entity. */
	public static void onAttack(Minecraft client, Entity target) {
		VanityConfig config = VanityClient.config();
		if (!config.enabled || !(target instanceof LivingEntity living) || !living.isAlive()) return;
		CombatEffects effects = config.combat;
		if (target instanceof Player && !effects.onPlayers) return;
		if (effects.kill != KillEffect.NONE) RECENT.put(target.getId(), client.level.getGameTime());
		hit(client.level, center(target), effects);
	}

	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			RECENT.clear();
			return;
		}

		if (previewTicks >= 0 && previewTicks-- == 0) kill(level, previewAt, VanityClient.config().combat);
		if (RECENT.isEmpty()) return;
		long now = level.getGameTime();
		CombatEffects effects = VanityClient.config().combat;

		for (Iterator<Map.Entry<Integer, Long>> it = RECENT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity entity = level.getEntity(entry.getKey());

			if (entity == null || now - entry.getValue() > KILL_WINDOW) {
				it.remove();
			} else if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
				it.remove();
				if (VanityClient.config().enabled && effects.kill != KillEffect.NONE) kill(level, center(entity), effects);
			}
		}
	}

	/** Wardrobe "Try it": a hit now and a kill a moment later, three blocks in front of you. */
	public static void preview(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) return;
		Vec3 look = player.getLookAngle();
		previewAt = player.getEyePosition().add(look.x * 3, look.y * 3 - 0.6, look.z * 3);
		hit(client.level, previewAt, VanityClient.config().combat);
		previewTicks = 8;
	}

	private static Vec3 center(Entity entity) {
		return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ());
	}

	private static int count(CombatEffects effects, int base) {
		return Math.max(1, Math.round(base * effects.amount));
	}

	// ---- hit ----

	private static void hit(ClientLevel level, Vec3 at, CombatEffects effects) {
		if (effects.hit == HitEffect.NONE) return;
		int n = count(effects, 10);
		float size = effects.size;
		lastHitParticles = 0;

		for (int i = 0; i < n; i++) {
			double vx = RANDOM.nextGaussian() * 0.15 * size;
			double vy = 0.05 + RANDOM.nextDouble() * 0.2 * size;
			double vz = RANDOM.nextGaussian() * 0.15 * size;
			double x = at.x + RANDOM.nextGaussian() * 0.2 * size;
			double y = at.y + RANDOM.nextGaussian() * 0.25 * size;
			double z = at.z + RANDOM.nextGaussian() * 0.2 * size;

			ParticleOptions particle = switch (effects.hit) {
				case SPARKS -> new DustParticleOptions(effects.color, 1.1F * size);
				case BLOOD -> i % 3 == 0 ? new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()) : new DustParticleOptions(0x8A0A12, 1.2F * size);
				case HEARTS -> ParticleTypes.HEART;
				case FLAME -> ParticleTypes.FLAME;
				case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
				case RAINBOW -> new DustParticleOptions(Mth.hsvToRgb((float) i / n, 0.85F, 1.0F), 1.1F * size);
				case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
				case MAGIC -> i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.ENCHANTED_HIT;
				case NOTES -> ParticleTypes.NOTE;
				case NONE -> null;
			};

			if (particle == null) continue;
			if (effects.hit == HitEffect.FLAME || effects.hit == HitEffect.SOUL || effects.hit == HitEffect.HEARTS) {
				vx *= 0.3;
				vy *= 0.4;
				vz *= 0.3;
			}

			if (effects.hit == HitEffect.NOTES) vx = RANDOM.nextDouble();
			level.addParticle(particle, true, true, x, y, z, vx, vy, vz);
			lastHitParticles++;
		}
	}

	// ---- kill ----

	private static void kill(ClientLevel level, Vec3 at, CombatEffects effects) {
		float size = effects.size;
		lastKillParticles = 0;

		switch (effects.kill) {
			case NONE -> {
			}
			case SOUL_BURST -> {
				burst(level, at, ParticleTypes.SOUL, count(effects, 20), 0.12 * size, 0.08);
				burst(level, at, ParticleTypes.SCULK_SOUL, count(effects, 10), 0.08 * size, 0.12);
				burst(level, at, ParticleTypes.SOUL_FIRE_FLAME, count(effects, 14), 0.1 * size, 0.02);
				sound(level, at, effects, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.8F);
			}
			case LIGHTNING -> {
				for (int i = 0; i < count(effects, 40); i++) {
					double h = RANDOM.nextDouble() * 6.0 * size;
					double wobble = Math.sin(h * 3.1) * 0.25;
					add(level, ParticleTypes.ELECTRIC_SPARK, at.x + wobble + RANDOM.nextGaussian() * 0.05, at.y - 0.6 + h, at.z + RANDOM.nextGaussian() * 0.05, 0, 0, 0);
				}

				burst(level, at, ParticleTypes.END_ROD, count(effects, 14), 0.16 * size, 0.05);
				add(level, ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE6F2FF), at.x, at.y, at.z, 0, 0, 0);
				sound(level, at, effects, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
			}
			case FIREWORK -> {
				int color = effects.color;
				int fade = Mth.hsvToRgb(RANDOM.nextFloat(), 0.4F, 1.0F);
				FireworkExplosion.Shape shape = size >= 1.5F ? FireworkExplosion.Shape.LARGE_BALL : FireworkExplosion.Shape.SMALL_BALL;
				level.createFireworks(at.x, at.y + 0.4, at.z, 0, 0, 0, List.of(new FireworkExplosion(shape, IntList.of(color), IntList.of(fade), true, true)), effects.sounds);
				lastKillParticles = 1;
			}
			case BLOOD -> {
				burst(level, at, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), count(effects, 30), 0.2 * size, 0.15);
				burst(level, at, new DustParticleOptions(0x7A0710, 1.6F * size), count(effects, 30), 0.15 * size, 0.05);
				sound(level, at, effects, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
			}
			case HEARTS -> {
				for (int i = 0; i < count(effects, 16); i++) {
					double a = i * 2.399963;
					add(level, ParticleTypes.HEART, at.x + Math.cos(a) * 0.4 * size, at.y + RANDOM.nextDouble() * 0.6, at.z + Math.sin(a) * 0.4 * size, 0, 0.1, 0);
				}

				sound(level, at, effects, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
			}
			case TOTEM -> {
				burst(level, at, ParticleTypes.TOTEM_OF_UNDYING, count(effects, 50), 0.35 * size, 0.25);
				sound(level, at, effects, SoundEvents.TOTEM_USE, 0.5F, 1.2F);
			}
			case EXPLOSION -> {
				add(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, size, 0, 0);
				burst(level, at, ParticleTypes.LARGE_SMOKE, count(effects, 12), 0.08 * size, 0.04);
				sound(level, at, effects, SoundEvents.GENERIC_EXPLODE.value(), 0.4F, 1.3F);
			}
			case RAINBOW_RING -> {
				int n = count(effects, 36);

				for (int ring = 0; ring < 2; ring++) {
					for (int i = 0; i < n; i++) {
						double a = Math.PI * 2 * i / n;
						double speed = (0.22 + ring * 0.1) * size;
						add(level, new DustParticleOptions(Mth.hsvToRgb((float) i / n, 0.85F, 1.0F), 1.3F * size), at.x, at.y - 0.3 + ring * 0.5, at.z,
								Math.cos(a) * speed, 0.0, Math.sin(a) * speed);
					}
				}

				sound(level, at, effects, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.9F);
			}
			case VOID -> {
				burst(level, at, ParticleTypes.REVERSE_PORTAL, count(effects, 40), 0.2 * size, 0.1);
				burst(level, at, ParticleTypes.SQUID_INK, count(effects, 12), 0.1 * size, 0.05);
				burst(level, at, ParticleTypes.PORTAL, count(effects, 30), 0.6 * size, 0.2);
				sound(level, at, effects, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F);
			}
		}
	}

	/** Particles flying out from a point in every direction. */
	private static void burst(ClientLevel level, Vec3 at, ParticleOptions particle, int n, double speed, double up) {
		for (int i = 0; i < n; i++) {
			double y = 1.0 - 2.0 * (i + 0.5) / n;
			double r = Math.sqrt(1.0 - y * y);
			double a = i * 2.399963;
			double s = speed * (0.6 + 0.8 * RANDOM.nextDouble());
			add(level, particle, at.x, at.y, at.z, Math.cos(a) * r * s, y * s + up, Math.sin(a) * r * s);
		}
	}

	private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		level.addParticle(particle, true, true, x, y, z, vx, vy, vz);
		lastKillParticles++;
	}

	private static void sound(ClientLevel level, Vec3 at, CombatEffects effects, SoundEvent sound, float volume, float pitch) {
		if (effects.sounds) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
	}
}
