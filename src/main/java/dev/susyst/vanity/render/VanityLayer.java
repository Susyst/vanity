package dev.susyst.vanity.render;

import java.util.List;
import java.util.Optional;

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
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.phys.AABB;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.TrimLayer;
import dev.susyst.vanity.config.Trims;

/**
 * Draws what the vanilla armor layer can't: see-through and glowing armor, glowing trims, custom trim designs, and
 * fitted cosmetic items (attached to the body parts so they follow animations).
 */
public final class VanityLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** Draw order after the vanilla armor and trim, so these passes sit on top. */
	private static final int ARMOR_ORDER = 6;
	private static final int DESIGN_ORDER = 12;
	private static final int GLOW_ORDER = 18;

	private final ArmorModelSet<PlayerModel> armorModels;
	private final EquipmentAssetManager equipmentAssets;

	public VanityLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ArmorModelSet<PlayerModel> armorModels, EquipmentAssetManager equipmentAssets) {
		super(parent);
		this.armorModels = armorModels;
		this.equipmentAssets = equipmentAssets;
	}

	private static int withAlpha(int color, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		return a << 24 | (color & 0xFFFFFF);
	}

	private static EquipmentClientInfo.LayerType layerType(CosmeticSlot slot) {
		return slot == CosmeticSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
	}

	/** The grayscale vanilla trim pattern texture, tinted by color when drawn. */
	private static Identifier trimTexture(Identifier pattern, EquipmentClientInfo.LayerType layerType) {
		return pattern.withPath(path -> "textures/" + layerType.trimAssetPrefix() + "/" + path + ".png");
	}

	/** Same coloring rule as vanilla: dyeable layers use the dye (or their undyed color), others are white. */
	private static int layerColor(EquipmentClientInfo.Layer layer, int dyeColor) {
		Optional<EquipmentClientInfo.Dyeable> dyeable = layer.dyeable();
		if (dyeable.isEmpty()) return 0xFFFFFFFF;
		if (dyeColor != 0) return 0xFF000000 | dyeColor;
		return dyeable.get().colorWhenUndyed().map(c -> 0xFF000000 | c).orElse(0);
	}

	private static int trimColor(ArmorTrim trim) {
		for (String name : Trims.MATERIALS) {
			if (trim.material().is(Identifier.withDefaultNamespace(name))) return Trims.materialColor(name);
		}

		return 0xFFFFFF;
	}

	/** See-through armor (instead of the vanilla pass) and glowing armor/trims (on top of it). */
	private void submitArmor(CosmeticSlot slot, VanityRenderData data, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state) {
		int i = slot.ordinal();
		ItemStack stack = data.armor[i];
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) return;

		ResourceKey<EquipmentAsset> asset = equippable.assetId().get();
		EquipmentClientInfo.LayerType layerType = layerType(slot);
		List<EquipmentClientInfo.Layer> layers = this.equipmentAssets.get(asset).getLayers(layerType);
		PlayerModel model = this.armorModels.get(slot.equipmentSlot);
		int dye = DyedItemColor.getOrDefault(stack, 0);
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		float opacity = data.opacity[i];
		int order = ARMOR_ORDER;

		if (opacity < 0.995F) {
			for (EquipmentClientInfo.Layer layer : layers) {
				int color = layerColor(layer, dye);
				if (color == 0) continue;
				RenderType type = RenderTypes.entityTranslucent(layer.getTextureLocation(layerType));
				collector.order(order++).submitModel(model, state, poseStack, type, lightCoords, OverlayTexture.NO_OVERLAY, withAlpha(color, opacity), (UvMapping) null, state.outlineColor);
			}

			if (trim != null) {
				Identifier texture = trimTexture(trim.pattern().value().assetId(), layerType);
				collector.order(order++).submitModel(model, state, poseStack, RenderTypes.entityTranslucent(texture), lightCoords, OverlayTexture.NO_OVERLAY, withAlpha(trimColor(trim), opacity), (UvMapping) null, state.outlineColor);
			}

			if (stack.hasFoil()) {
				collector.order(order++).submitModel(model, state, poseStack, RenderTypes.trimmedArmorGlint(), lightCoords, OverlayTexture.NO_OVERLAY, -1, (UvMapping) null, 0);
			}
		}

		// Glow: a full-bright copy on top, blended in by the glow amount
		float glow = data.glow[i];

		if (glow > 0.005F) {
			for (EquipmentClientInfo.Layer layer : layers) {
				int color = layerColor(layer, dye);
				if (color == 0) continue;
				RenderType type = RenderTypes.entityTranslucentEmissive(layer.getTextureLocation(layerType));
				collector.order(GLOW_ORDER).submitModel(model, state, poseStack, type, lightCoords, OverlayTexture.NO_OVERLAY, withAlpha(color, glow * opacity), (UvMapping) null, 0);
			}
		}

		if (data.trimGlow[i] && trim != null) {
			Identifier texture = trimTexture(trim.pattern().value().assetId(), layerType);
			collector.order(GLOW_ORDER + 1).submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(texture), lightCoords, OverlayTexture.NO_OVERLAY, withAlpha(trimColor(trim), opacity), (UvMapping) null, 0);
		}
	}

	/** Each design layer is a vanilla trim pattern texture tinted with the layer's color; glowing if trim glow is on. */
	private void submitDesign(CosmeticSlot slot, VanityRenderData data, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state) {
		int i = slot.ordinal();
		TrimLayer[] layers = data.designs[i];
		PlayerModel model = this.armorModels.get(slot.equipmentSlot);
		EquipmentClientInfo.LayerType layerType = layerType(slot);
		float opacity = data.opacity[i];
		boolean glow = data.trimGlow[i];

		for (int k = 0; k < layers.length; k++) {
			Identifier pattern = Identifier.tryParse(layers[k].pattern);
			if (pattern == null) continue;

			Identifier texture = trimTexture(pattern, layerType);
			RenderType type = glow ? RenderTypes.entityTranslucentEmissive(texture)
					: opacity < 0.995F ? RenderTypes.entityTranslucent(texture)
					: RenderTypes.armorTrim(texture, false);
			int color = withAlpha(layers[k].color, opacity);
			collector.order(DESIGN_ORDER + k).submitModel(model, state, poseStack, type, lightCoords, OverlayTexture.NO_OVERLAY, color, (UvMapping) null, state.outlineColor);
		}
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		VanityRenderData data = state.getData(VanityClient.RENDER_DATA);
		if (data == null) return;

		PlayerModel model = this.getParentModel();

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			int i = slot.ordinal();

			if (!data.armor[i].isEmpty()) {
				this.submitArmor(slot, data, poseStack, submitNodeCollector, lightCoords, state);
			}

			if (data.designs[i].length > 0) {
				this.submitDesign(slot, data, poseStack, submitNodeCollector, lightCoords, state);
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
