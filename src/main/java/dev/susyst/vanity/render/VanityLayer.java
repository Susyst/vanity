package dev.susyst.vanity.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;

/** Draws cosmetic models on the player, attached to the body parts so they follow animations. */
public final class VanityLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public VanityLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
		if (data == null) return;

		PlayerModel model = this.getParentModel();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			int i = slot.ordinal();
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
