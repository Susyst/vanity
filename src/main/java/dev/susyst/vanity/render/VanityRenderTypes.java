package dev.susyst.vanity.render;

import java.util.function.Function;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import dev.susyst.vanity.mixin.RenderTypeAccessor;

/** Render types Vanity needs that vanilla doesn't have. */
public final class VanityRenderTypes {
	/**
	 * Full-bright, translucent, and nudged toward the camera exactly like vanilla armor, so a glow pass lines up with
	 * (and shows on top of) the armor and trims the game already drew.
	 */
	private static final Function<Identifier, RenderType> ARMOR_GLOW = Util.memoize(texture -> RenderTypeAccessor.vanity$create(
			"vanity_armor_glow",
			RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE)
					.withTexture("Sampler0", texture)
					.useOverlay()
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.sortOnUpload()
					.createRenderSetup()));

	/** Lit and translucent, layered like vanilla armor: see-through armor, and trims redrawn over a glow. */
	private static final Function<Identifier, RenderType> ARMOR_TRANSLUCENT = Util.memoize(texture -> RenderTypeAccessor.vanity$create(
			"vanity_armor_translucent",
			RenderSetup.builder(RenderPipelines.ENTITY_TRANSLUCENT)
					.withTexture("Sampler0", texture)
					.useLightmap()
					.useOverlay()
					.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
					.sortOnUpload()
					.createRenderSetup()));

	private VanityRenderTypes() {
	}

	public static RenderType armorTranslucent(Identifier texture) {
		return ARMOR_TRANSLUCENT.apply(texture);
	}

	public static RenderType armorGlow(Identifier texture) {
		return ARMOR_GLOW.apply(texture);
	}
}
