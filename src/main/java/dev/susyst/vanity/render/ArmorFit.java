package dev.susyst.vanity.render;

import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.FitMode;
import dev.susyst.vanity.config.SlotSettings;

/**
 * Works out where vanilla armor sits on each body part, and transforms an arbitrary item model so it fills that space.
 *
 * <p>All boxes are in pixels (1/16 block) in the local space of a {@link ModelPart}, read from the player's real model
 * cubes (so slim arms are handled), then grown by the same amount vanilla armor is: 1px for helmets, chestplates and
 * boots, 0.5px for leggings.
 */
public final class ArmorFit {
	private static final float OUTER_ARMOR = 1.0F;
	private static final float INNER_ARMOR = 0.5F;
	private static final float VANILLA_ITEM_SCALE = 0.625F;
	private static final float EPSILON = 1.0E-4F;
	private static final RandomSource CUBE_PICKER = RandomSource.create(0L);

	private ArmorFit() {
	}

	/** A box in model pixels. */
	public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
		Box grow(float amount) {
			return new Box(this.minX - amount, this.minY - amount, this.minZ - amount, this.maxX + amount, this.maxY + amount, this.maxZ + amount);
		}

		/** Keeps only the bottom {@code height} pixels (model Y points down, so the bottom is maxY). */
		Box bottom(float height) {
			return new Box(this.minX, Math.max(this.minY, this.maxY - height), this.minZ, this.maxX, this.maxY, this.maxZ);
		}

		float centerX() {
			return (this.minX + this.maxX) * 0.5F;
		}

		float centerY() {
			return (this.minY + this.maxY) * 0.5F;
		}

		float centerZ() {
			return (this.minZ + this.maxZ) * 0.5F;
		}

		float sizeX() {
			return this.maxX - this.minX;
		}

		float sizeY() {
			return this.maxY - this.minY;
		}

		float sizeZ() {
			return this.maxZ - this.minZ;
		}
	}

	/** One piece of armor coverage on one body part. */
	public enum Target {
		HEAD(m -> m.head, new Box(-4, -8, -4, 4, 0, 4), b -> b.grow(OUTER_ARMOR)),
		BODY(m -> m.body, new Box(-4, 0, -2, 4, 12, 2), b -> b.grow(OUTER_ARMOR)),
		RIGHT_ARM(m -> m.rightArm, new Box(-3, -2, -2, 1, 10, 2), b -> b.grow(OUTER_ARMOR)),
		LEFT_ARM(m -> m.leftArm, new Box(-1, -2, -2, 3, 10, 2), b -> b.grow(OUTER_ARMOR)),
		/** The belt part of leggings: the lower 4px of the torso. */
		WAIST(m -> m.body, new Box(-4, 0, -2, 4, 12, 2), b -> b.bottom(4.0F).grow(INNER_ARMOR)),
		RIGHT_LEG(m -> m.rightLeg, new Box(-2, 0, -2, 2, 12, 2), b -> b.grow(INNER_ARMOR)),
		LEFT_LEG(m -> m.leftLeg, new Box(-2, 0, -2, 2, 12, 2), b -> b.grow(INNER_ARMOR)),
		/** Boots: the lower 5px of each leg. */
		RIGHT_FOOT(m -> m.rightLeg, new Box(-2, 0, -2, 2, 12, 2), b -> b.bottom(5.0F).grow(OUTER_ARMOR)),
		LEFT_FOOT(m -> m.leftLeg, new Box(-2, 0, -2, 2, 12, 2), b -> b.bottom(5.0F).grow(OUTER_ARMOR));

		private final Function<PlayerModel, ModelPart> part;
		private final Box fallback;
		private final Function<Box, Box> armorShape;

		Target(Function<PlayerModel, ModelPart> part, Box fallback, Function<Box, Box> armorShape) {
			this.part = part;
			this.fallback = fallback;
			this.armorShape = armorShape;
		}

		public ModelPart part(PlayerModel model) {
			return this.part.apply(model);
		}

		/** The armor box for this target, from the part's actual cube. */
		public Box box(ModelPart part) {
			Box base = this.fallback;

			if (!part.isEmpty()) {
				ModelPart.Cube cube = part.getRandomCube(CUBE_PICKER);
				base = new Box(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ);
			}

			return this.armorShape.apply(base);
		}
	}

	private static final List<Target> HEAD_TARGETS = List.of(Target.HEAD);
	private static final List<Target> CHEST_TARGETS = List.of(Target.BODY, Target.RIGHT_ARM, Target.LEFT_ARM);
	private static final List<Target> LEGS_TARGETS = List.of(Target.WAIST, Target.RIGHT_LEG, Target.LEFT_LEG);
	private static final List<Target> FEET_TARGETS = List.of(Target.RIGHT_FOOT, Target.LEFT_FOOT);

	/** The body parts a slot's armor covers. Vanilla-size mode uses one copy on the main part. */
	public static List<Target> targets(CosmeticSlot slot, FitMode mode) {
		if (mode == FitMode.FREE) {
			return switch (slot) {
				case HEAD -> HEAD_TARGETS;
				case CHEST -> List.of(Target.BODY);
				case LEGS -> List.of(Target.WAIST);
				case FEET -> FEET_TARGETS;
			};
		}

		return switch (slot) {
			case HEAD -> HEAD_TARGETS;
			case CHEST -> CHEST_TARGETS;
			case LEGS -> LEGS_TARGETS;
			case FEET -> FEET_TARGETS;
		};
	}

	/**
	 * Transforms the pose stack (already in the body part's local space) so that an item model with the given bounds
	 * is drawn filling {@code box}.
	 */
	public static void apply(PoseStack poseStack, Box box, AABB modelBounds, SlotSettings settings) {
		// Move to the middle of the armor box, then apply the user's offset (in pixels, +Y up).
		poseStack.translate(box.centerX() / 16.0F, box.centerY() / 16.0F, box.centerZ() / 16.0F);
		poseStack.translate(settings.offsetX / 16.0F, -settings.offsetY / 16.0F, settings.offsetZ / 16.0F);

		// Model space has Y pointing down; flip to item space the same way vanilla's CustomHeadLayer does.
		poseStack.rotateDegrees(Axis.YP, 180.0F);
		poseStack.scale(1.0F, -1.0F, -1.0F);

		Quaternionf rotation = new Quaternionf().rotationXYZ(
				(float) Math.toRadians(settings.rotX),
				(float) Math.toRadians(settings.rotY),
				(float) Math.toRadians(settings.rotZ));

		if (settings.mode == FitMode.FREE) {
			float s = VANILLA_ITEM_SCALE * settings.scale;
			poseStack.scale(s, s, s);
			poseStack.rotate(rotation);
			return;
		}

		// Size of the model after the user's rotation, so a turned model still fills the armor box.
		float halfX = (float) (modelBounds.maxX - modelBounds.minX) * 0.5F;
		float halfY = (float) (modelBounds.maxY - modelBounds.minY) * 0.5F;
		float halfZ = (float) (modelBounds.maxZ - modelBounds.minZ) * 0.5F;
		Matrix3f m = new Matrix3f().rotation(rotation);
		float[] modelSize = {
				2.0F * (Math.abs(m.m00()) * halfX + Math.abs(m.m10()) * halfY + Math.abs(m.m20()) * halfZ),
				2.0F * (Math.abs(m.m01()) * halfX + Math.abs(m.m11()) * halfY + Math.abs(m.m21()) * halfZ),
				2.0F * (Math.abs(m.m02()) * halfX + Math.abs(m.m12()) * halfY + Math.abs(m.m22()) * halfZ)
		};
		float[] boxSize = {box.sizeX() / 16.0F, box.sizeY() / 16.0F, box.sizeZ() / 16.0F};

		float uniform = Float.MAX_VALUE;

		for (int i = 0; i < 3; i++) {
			if (modelSize[i] > EPSILON) uniform = Math.min(uniform, boxSize[i] / modelSize[i]);
		}

		if (uniform == Float.MAX_VALUE) uniform = VANILLA_ITEM_SCALE;

		float[] factor = new float[3];

		for (int i = 0; i < 3; i++) {
			boolean stretchAxis = settings.mode == FitMode.STRETCH && modelSize[i] > EPSILON;
			factor[i] = (stretchAxis ? boxSize[i] / modelSize[i] : uniform) * settings.scale;
		}

		poseStack.scale(factor[0], factor[1], factor[2]);
		poseStack.rotate(rotation);

		// Center the model's real bounds on the box.
		poseStack.translate(
				(float) -(modelBounds.minX + modelBounds.maxX) * 0.5F,
				(float) -(modelBounds.minY + modelBounds.maxY) * 0.5F,
				(float) -(modelBounds.minZ + modelBounds.maxZ) * 0.5F);
	}
}
