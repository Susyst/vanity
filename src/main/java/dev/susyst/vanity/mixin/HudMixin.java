package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;

import dev.susyst.vanity.render.Abilities;

/** Text shown above the hotbar (the action bar), however the server sends it, for SkyBlock ability effects. */
@Mixin(Hud.class)
public class HudMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), require = 0)
	private void vanity$overlayMessage(Component message, boolean animateColor, CallbackInfo ci) {
		Abilities.onActionBar(message);
	}
}
