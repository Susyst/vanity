package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.render.VanityCosmetics;

/** Shows the look on the inventory icons of the armor you're wearing. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {
	@ModifyVariable(
			method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
			at = @At("HEAD"),
			argsOnly = true)
	private ItemStack vanity$showLookOnIcon(ItemStack stack) {
		return VanityCosmetics.inventoryIcon(stack);
	}
}
