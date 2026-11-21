package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.Avatar;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.render.ItemSkins;
import dev.susyst.vanity.render.VanityCosmetics;
import dev.susyst.vanity.render.VanityRenderData;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {
	@Unique
	private ItemModelResolver vanity$itemModelResolver;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void vanity$captureItemModelResolver(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
		this.vanity$itemModelResolver = context.getItemModelResolver();
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
	private void vanity$extractCosmetics(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		VanityRenderData data = state.getData(VanityClient.RENDER_DATA);

		if (!VanityCosmetics.appliesTo(entity)) {
			if (data != null) data.clear();
			return;
		}

		if (data == null) {
			data = new VanityRenderData();
			state.setData(VanityClient.RENDER_DATA, data);
		}

		VanityCosmetics.extract(data, state, entity, this.vanity$itemModelResolver);
		if (VanityClient.config().enabled) ItemSkins.applyToHands(state, entity, this.vanity$itemModelResolver);
	}
}
