package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import dev.susyst.vanity.render.SwingTrails;

/** Your arm swinging, for swing trails. */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@Inject(method = "swing", at = @At("HEAD"), require = 0)
	private void vanity$swing(InteractionHand hand, CallbackInfo ci) {
		SwingTrails.onSwing((LocalPlayer) (Object) this, hand);
	}
}
