package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.render.ItemSkins;

/** First person: draw the skin of the items in your hands (the real item still decides how it's used). */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
	@ModifyArg(
			method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V"),
			index = 1)
	private ItemStack vanity$skinHeldItem(ItemStack stack) {
		return VanityClient.config().enabled ? ItemSkins.skinned(stack) : stack;
	}
}
