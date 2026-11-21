package dev.susyst.vanity.config;

import net.minecraft.world.entity.EquipmentSlot;

/** The four armor slots a cosmetic can be worn in. */
public enum CosmeticSlot {
	HEAD("Head", EquipmentSlot.HEAD, "helmet"),
	CHEST("Chest", EquipmentSlot.CHEST, "chestplate"),
	LEGS("Legs", EquipmentSlot.LEGS, "leggings"),
	FEET("Feet", EquipmentSlot.FEET, "boots");

	public final String label;
	public final EquipmentSlot equipmentSlot;
	/** Vanilla item name suffix for armor in this slot, e.g. "chestplate". */
	public final String armorPiece;

	CosmeticSlot(String label, EquipmentSlot equipmentSlot, String armorPiece) {
		this.label = label;
		this.equipmentSlot = equipmentSlot;
		this.armorPiece = armorPiece;
	}
}
