package dev.susyst.vanity.render;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.SwingShape;
import dev.susyst.vanity.config.SwingTrail;
import dev.susyst.vanity.config.TrailStyle;
import dev.susyst.vanity.config.VanityConfig;

/**
 * A trail of particles where your weapon swings: an arc in front of you, drawn over a few ticks so it sweeps across
 * like a slash. It follows where you look, so it lines up in first and third person. Only you see it.
 */
public final class SwingTrails {
	private static final RandomSource RANDOM = RandomSource.create();
	/** Ticks a trail takes to sweep across. */
	private static final int FRAMES = 4;
	/** Half of the arc, in radians (70 degrees each way from straight ahead). */
	private static final double HALF_ARC = Math.toRadians(70);

	private static boolean wasSwinging;
	private static int lastSwingTime;
	private static int frame = -1;
	private static int combo = 1;

	/** For tests: how many particles the last trail made so far, and how many trails started. */
	public static int lastParticles;
	public static int trails;

	private SwingTrails() {
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			frame = -1;
			wasSwinging = false;
			return;
		}

		// A new swing: the arm starts swinging, or starts over in the middle of a swing
		boolean started = player.swinging && (!wasSwinging || player.swingTime < lastSwingTime);
		wasSwinging = player.swinging;
		lastSwingTime = player.swingTime;
		VanityConfig config = VanityClient.config();
		SwingTrail trail = config.swing;

		if (started && config.enabled && trail.style != TrailStyle.NONE && player.swingingArm == InteractionHand.MAIN_HAND
				&& (!trail.weaponsOnly || isWeapon(player.getMainHandItem()))) {
			start(level, player, trail);
		}

		if (frame >= 0) {
			draw(level, player, trail, frame);
			if (++frame >= FRAMES) frame = -1;
		}
	}

	/** Wardrobe "Try it": one trail now. */
	public static void preview(Minecraft client) {
		if (client.player != null && client.level != null) start(client.level, client.player, VanityClient.config().swing);
	}

	/** Swords, axes, tridents, maces, and SkyBlock items whose rarity line says SWORD or DAGGER. */
	public static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) return true;
		if (ItemSkins.skyblockId(stack).isEmpty()) return false;
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return false;

		for (Component line : lore.lines()) {
			String text = line.getString().toUpperCase(Locale.ROOT);
			if (text.contains("SWORD") || text.contains("DAGGER")) return true;
		}

		return false;
	}

	private static void start(ClientLevel level, LocalPlayer player, SwingTrail trail) {
		if (trail.style == TrailStyle.NONE) return;
		frame = 0;
		lastParticles = 0;
		trails++;
		if (trail.shape == SwingShape.COMBO) combo = -combo;

		if (trail.sounds) {
			level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3F,
					1.5F + RANDOM.nextFloat() * 0.2F, false);
		}
	}

	/** One tick's piece of the arc. */
	private static void draw(ClientLevel level, LocalPlayer player, SwingTrail trail, int k) {
		double yaw = Math.toRadians(player.getYRot());
		Vec3 forward = player.getLookAngle();
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(forward);

		// The direction the arc sweeps along, from its start (+) to its end (-)
		Vec3 sweep = switch (trail.shape) {
			case DIAGONAL -> right.add(up.scale(0.7)).normalize();
			case FLAT -> right;
			case OVERHEAD -> up;
			case COMBO -> right.add(up.scale(0.7 * combo)).normalize();
		};

		Vec3 center = player.getEyePosition().add(up.scale(-0.25)).add(forward.scale(0.15));
		double radius = 1.2 + 0.5 * trail.size;
		int perFrame = Math.max(2, Math.round(9 * trail.amount));
		int total = perFrame * FRAMES;

		for (int j = 0; j < perFrame; j++) {
			int i = k * perFrame + j;
			double t = (i + RANDOM.nextDouble()) / total;
			double angle = HALF_ARC - 2 * HALF_ARC * t;
			// The outer edge, and every other particle a little inside it, so the arc has some width
			double r = radius * (j % 2 == 0 ? 1.0 : 0.88);
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			Vec3 at = center.add(forward.scale(cos * r)).add(sweep.scale(sin * r));
			// Drift the way the swing goes
			Vec3 v = forward.scale(sin).subtract(sweep.scale(cos)).scale(0.03 * trail.size);
			double jitter = 0.03 * trail.size;
			lastParticles += TrailParticles.add(level, TrailParticles.particle(trail.style, i, total, trail.color, trail.size),
					at.x + RANDOM.nextGaussian() * jitter, at.y + RANDOM.nextGaussian() * jitter, at.z + RANDOM.nextGaussian() * jitter, v.x, v.y, v.z);
		}
	}
}
