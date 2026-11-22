package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import dev.susyst.vanity.render.SwingTrails;

/** Your arm swinging (attacking, or clicking at nothing), for swing trails. */
@Mixin(LivingEntity.class)
public class LivingEntitySwingMixin {
	@Inject(method = "swing", at = @At("HEAD"), require = 0)
	private void vanity$swing(InteractionHand hand, @Coerce Object animation, boolean sendPacket, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) SwingTrails.onSwing(player, hand);
	}
}
