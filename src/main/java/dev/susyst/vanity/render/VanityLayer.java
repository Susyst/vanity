package dev.susyst.vanity.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;

/**
 * Draws fitted cosmetic models on the player (attached to the body parts so they follow animations) and custom trim
 * designs on top of the armor.
 */
public final class VanityLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Draw order after the vanilla armor and trim, so designs sit on top. */
	private static final int DESIGN_ORDER = 10;

	private final ArmorModelSet<PlayerModel> armorModels;

	public VanityLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ArmorModelSet<PlayerModel> armorModels) {
		super(parent);
		this.armorModels = armorModels;
	}

	/** Each design layer is the vanilla trim pattern's grayscale texture, tinted with the layer's color. */
	private void submitDesign(CosmeticSlot slot, TrimLayer[] layers, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state) {
		PlayerModel model = this.armorModels.get(slot.equipmentSlot);
		EquipmentClientInfo.LayerType layerType = slot == CosmeticSlot.LEGS
				? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS
				: EquipmentClientInfo.LayerType.HUMANOID;

		for (int k = 0; k < layers.length; k++) {
			Identifier pattern = Identifier.tryParse(layers[k].pattern);
			if (pattern == null) continue;

			Identifier texture = pattern.withPath(path -> "textures/" + layerType.trimAssetPrefix() + "/" + path + ".png");
			RenderType renderType = RenderTypes.armorTrim(texture, false);
			int color = 0xFF000000 | (layers[k].color & 0xFFFFFF);
			collector.order(DESIGN_ORDER + k).submitModel(model, state, poseStack, renderType, lightCoords, OverlayTexture.NO_OVERLAY, color, (UvMapping) null, state.outlineColor);
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
		if (data == null) return;

		PlayerModel model = this.getParentModel();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			int i = slot.ordinal();

			if (data.designs[i].length > 0) {
				this.submitDesign(slot, data.designs[i], poseStack, submitNodeCollector, lightCoords, state);
			}

			if (!data.active[i]) continue;

			ItemStackRenderState item = data.items[i];
			SlotSettings settings = data.settings[i];
			AABB bounds = item.getModelBoundingBox();

			for (ArmorFit.Target target : ArmorFit.targets(slot, settings.mode)) {
				ModelPart part = target.part(model);
				if (!part.visible) continue;

				poseStack.pushPose();
				model.root().translateAndRotate(poseStack);
				part.translateAndRotate(poseStack);
				ArmorFit.apply(poseStack, target.box(part), bounds, settings);
				item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		}
	}
}
