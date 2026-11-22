package dev.susyst.vanity.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

import dev.susyst.vanity.config.TrailStyle;

/** The particles a swing trail or ability effect is drawn with. */
final class TrailParticles {
	private TrailParticles() {
	}

	/** Particle i of n for a style (Rainbow runs through the colors along the n). */
	static ParticleOptions particle(TrailStyle style, int i, int n, int color, float size) {
		return switch (style) {
			case NONE -> null;
			case COLOR -> new DustParticleOptions(color, 0.9F * size);
			case SPARKLE -> i % 3 == 0 ? ParticleTypes.FIREWORK : ParticleTypes.END_ROD;
			case FLAME -> i % 4 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
			case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
			case FROST -> i % 2 == 0 ? ParticleTypes.SNOWFLAKE : new DustParticleOptions(0xC8F2FF, 0.8F * size);
			case ELECTRIC -> ParticleTypes.ELECTRIC_SPARK;
			case MAGIC -> i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.ENCHANTED_HIT;
			case VOID -> i % 3 == 0 ? ParticleTypes.REVERSE_PORTAL : new DustParticleOptions(0x7A2BD8, 0.9F * size);
			case RAINBOW -> new DustParticleOptions(Mth.hsvToRgb((float) i / Math.max(1, n), 0.85F, 1.0F), 0.9F * size);
		};
	}

	/** Adds a particle; returns 1 if there was one, for counting. */
	static int add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
		if (particle == null) return 0;
		level.addParticle(particle, true, true, x, y, z, vx, vy, vz);
		return 1;
	}
}
