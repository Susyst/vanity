package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;

import dev.susyst.vanity.render.Abilities;

/** Text above the hotbar sent as its own packet (servers can also send it as a chat message, see VanityClient). */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	// At the end, so it runs once on the game thread (the network thread's first call hands it over and stops early)
	@Inject(method = "handleSetActionBarText", at = @At("TAIL"), require = 0)
	private void vanity$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
		Abilities.onActionBar(packet.text());
	}
}
