package dev.susyst.vanity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;

import dev.susyst.vanity.render.CustomArmors;

/** Armor you draw yourself (config/vanity/armors) isn't in a resource pack, so its model layers come from here. */
@Mixin(EquipmentAssetManager.class)
public abstract class EquipmentAssetManagerMixin {
	@Inject(method = "get", at = @At("HEAD"), cancellable = true)
	private void vanity$userArmor(ResourceKey<EquipmentAsset> id, CallbackInfoReturnable<EquipmentClientInfo> cir) {
		EquipmentClientInfo info = CustomArmors.userInfo(id);
		if (info != null) cir.setReturnValue(info);
	}
}
