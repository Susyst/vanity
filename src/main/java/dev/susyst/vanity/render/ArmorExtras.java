package dev.susyst.vanity.render;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

import dev.susyst.vanity.config.CosmeticSlot;

/**
 * 3D pieces on top of a custom armor (horns, crests, shoulder plates, crystals, a halo...), from
 * assets/vanity/armor_extras/<id>.json. Each piece rides on a body part, so it moves with it.
 */
public final class ArmorExtras {
	private static final Logger LOGGER = LoggerFactory.getLogger("vanity");

	/** Which part of the player a piece is attached to. */
	public enum Bone {
		HEAD(CosmeticSlot.HEAD), BODY(CosmeticSlot.CHEST), RIGHT_ARM(CosmeticSlot.CHEST), LEFT_ARM(CosmeticSlot.CHEST),
		RIGHT_LEG(CosmeticSlot.LEGS), LEFT_LEG(CosmeticSlot.LEGS);

		/** The armor piece the add-on belongs to: horns come with the helmet, shoulder plates with the chestplate. */
		public final CosmeticSlot slot;

		Bone(CosmeticSlot slot) {
			this.slot = slot;
		}

		ModelPart part(PlayerModel model) {
			return switch (this) {
				case HEAD -> model.head;
				case BODY -> model.body;
				case RIGHT_ARM -> model.rightArm;
				case LEFT_ARM -> model.leftArm;
				case RIGHT_LEG -> model.rightLeg;
				case LEFT_LEG -> model.leftLeg;
			};
		}
	}

	/**
	 * One piece: the body part it rides on, the armor piece it comes with, whether it glows, and how it moves: spin
	 * (degrees a second), bob (pixels), sway (degrees, for capes and banners) and orbit (radius, radians a second).
	 */
	public record Piece(Bone bone, CosmeticSlot slot, ModelPart part, boolean glow, float spin, float bob, float sway, float orbitRadius, float orbitSpeed,
			float phase, float baseX, float baseY, float baseZ, float baseXRot, float baseYRot) {
	}

	private ArmorExtras() {
	}

	public static List<Piece> load(String id) {
		try (InputStream in = ArmorExtras.class.getResourceAsStream("/assets/vanity/armor_extras/" + id + ".json")) {
			if (in == null) return List.of();
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray size = root.getAsJsonArray("texture_size");
			JsonArray parts = root.getAsJsonArray("parts");
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition meshRoot = mesh.getRoot();

			for (JsonElement element : parts) {
				JsonObject p = element.getAsJsonObject();
				float[] box = floats(p.getAsJsonArray("box"));
				float[] pivot = floats(p.getAsJsonArray("pivot"));
				float[] rot = floats(p.getAsJsonArray("rot"));
				int[] tex = {p.getAsJsonArray("tex").get(0).getAsInt(), p.getAsJsonArray("tex").get(1).getAsInt()};
				boolean mirror = p.has("mirror") && p.get("mirror").getAsBoolean();
				meshRoot.addOrReplaceChild(p.get("name").getAsString(),
						CubeListBuilder.create().texOffs(tex[0], tex[1]).mirror(mirror).addBox(box[0], box[1], box[2], box[3], box[4], box[5]),
						PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0] * Mth.DEG_TO_RAD, rot[1] * Mth.DEG_TO_RAD, rot[2] * Mth.DEG_TO_RAD));
			}

			ModelPart baked = LayerDefinition.create(mesh, size.get(0).getAsInt(), size.get(1).getAsInt()).bakeRoot();
			List<Piece> pieces = new ArrayList<>();

			for (JsonElement element : parts) {
				JsonObject p = element.getAsJsonObject();
				ModelPart part = baked.getChild(p.get("name").getAsString());
				Bone bone = Bone.valueOf(p.get("bone").getAsString().toUpperCase(java.util.Locale.ROOT));
				CosmeticSlot slot = p.has("slot") ? CosmeticSlot.valueOf(p.get("slot").getAsString().toUpperCase(java.util.Locale.ROOT)) : bone.slot;
				JsonArray orbit = p.has("orbit") ? p.getAsJsonArray("orbit") : null;
				pieces.add(new Piece(bone, slot, part, p.get("glow").getAsBoolean(), number(p, "spin"), number(p, "bob"), number(p, "sway"),
						orbit != null ? orbit.get(0).getAsFloat() : 0.0F, orbit != null ? orbit.get(1).getAsFloat() : 0.0F, number(p, "phase"),
						part.x, part.y, part.z, part.xRot, part.yRot));
			}

			return List.copyOf(pieces);
		} catch (Exception e) {
			LOGGER.error("Vanity: couldn't load 3D pieces for {}", id, e);
			return List.of();
		}
	}

	private static float number(JsonObject object, String key) {
		return object.has(key) ? object.get(key).getAsFloat() : 0.0F;
	}

	private static float[] floats(JsonArray array) {
		float[] values = new float[array.size()];
		for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
		return values;
	}

	/** Draws the armor's pieces that belong to {@code slot}. */
	public static void submit(CustomArmors.Armor armor, CosmeticSlot slot, PlayerModel model, PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, float opacity, float ageInTicks, int outlineColor, int order) {
		boolean seeThrough = opacity < 0.995F;
		RenderType type = seeThrough ? VanityRenderTypes.armorTranslucent(armor.extrasTexture()) : RenderTypes.armorCutoutNoCull(armor.extrasTexture());
		int color = Math.round(Mth.clamp(opacity, 0.0F, 1.0F) * 255.0F) << 24 | 0xFFFFFF;
		float seconds = ageInTicks / 20.0F;

		for (Piece piece : armor.extras()) {
			if (piece.slot() != slot) continue;
			ModelPart bone = piece.bone().part(model);
			if (!bone.visible) continue;

			ModelPart part = piece.part();
			part.yRot = piece.baseYRot() + piece.spin() * seconds * Mth.DEG_TO_RAD;
			part.xRot = piece.baseXRot() + piece.sway() * Mth.DEG_TO_RAD * Mth.sin(seconds * 1.7F + piece.phase());
			part.y = piece.baseY() + piece.bob() * Mth.sin(seconds * 2.2F + piece.phase());

			if (piece.orbitRadius() > 0.0F) {
				float angle = seconds * piece.orbitSpeed() + piece.phase();
				part.x = piece.baseX() + piece.orbitRadius() * Mth.cos(angle);
				part.z = piece.baseZ() + piece.orbitRadius() * Mth.sin(angle);
			}

			poseStack.pushPose();
			model.root().translateAndRotate(poseStack);
			bone.translateAndRotate(poseStack);
			collector.order(order).submitModelPart(part, poseStack, type, piece.glow() ? LightCoordsUtil.FULL_BRIGHT : lightCoords, OverlayTexture.NO_OVERLAY,
					(UvMapping) null, color, outlineColor);
			poseStack.popPose();
		}
	}
}
