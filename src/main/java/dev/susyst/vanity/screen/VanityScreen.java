package dev.susyst.vanity.screen;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.ArmorSet;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.VanityCosmetics;

/**
 * The wardrobe. Pick what each armor slot looks like: another armor (wear diamond, show netherite) or any item or block,
 * with a live preview.
 */
public final class VanityScreen extends Screen {
	private static final int ROW = 20;
	private static final int GAP = 4;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int GREEN = 0xFF80FF80;
	private static final int RED = 0xFFFF6060;

	private CosmeticSlot selected = CosmeticSlot.HEAD;
	private float previewYaw;

	private final List<Button> slotButtons = new ArrayList<>();
	private EditBox itemBox;
	private Button setButton;
	private Button modeButton;
	private Button hideButton;
	private Button enabledButton;
	private ValueSlider offsetX;
	private ValueSlider offsetY;
	private ValueSlider offsetZ;
	private ValueSlider rotX;
	private ValueSlider rotY;
	private ValueSlider rotZ;
	private ValueSlider scale;

	private int previewRight;
	private int panelX;
	private int panelW;
	private int statusY;

	public VanityScreen() {
		super(Component.translatable("screen.vanity.title"));
	}

	private static VanityConfig config() {
		return VanityClient.config();
	}

	private SlotSettings current() {
		return config().get(this.selected);
	}

	@Override
	protected void init() {
		this.slotButtons.clear();
		this.previewRight = Math.max(110, (int) (this.width * 0.38F));
		this.panelX = this.previewRight + 8;
		this.panelW = Math.max(180, Math.min(this.width - this.panelX - 10, 300));

		int y = 28;

		// Slot tabs
		int tabW = (this.panelW - GAP * 3) / 4;
		CosmeticSlot[] slots = CosmeticSlot.values();

		for (int i = 0; i < slots.length; i++) {
			CosmeticSlot slot = slots[i];
			Button tab = Button.builder(Component.literal(slot.label), b -> this.select(slot))
					.bounds(this.panelX + i * (tabW + GAP), y, tabW, ROW)
					.build();
			this.slotButtons.add(this.addRenderableWidget(tab));
		}

		y += ROW + GAP;

		// One click: make all four slots look like one armor material
		this.setButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> this.cycleArmorSet())
				.bounds(this.panelX, y, this.panelW, ROW)
				.build());
		this.setButton.setTooltip(Tooltip.create(Component.literal("Click to change how all four armor slots look. Your real armor stays the same; only the look changes.")));

		y += ROW + 8;

		// Item id, "use held item", clear
		int smallW = 40;
		int boxW = this.panelW - 2 * (smallW + GAP);
		this.itemBox = new EditBox(this.font, this.panelX, y, boxW, ROW, Component.literal("Item ID"));
		this.itemBox.setMaxLength(128);
		this.itemBox.setHint(Component.literal("netherite_chestplate"));
		this.itemBox.setValue(this.current().item);
		this.itemBox.setResponder(value -> {
			this.current().item = value.trim();
			if (this.scale != null) this.refresh();
		});
		this.addRenderableWidget(this.itemBox);

		Button held = Button.builder(Component.literal("Held"), b -> this.useHeldItem())
				.bounds(this.panelX + boxW + GAP, y, smallW, ROW)
				.build();
		held.setTooltip(Tooltip.create(Component.literal("Use the item in your main hand")));
		this.addRenderableWidget(held);

		Button clear = Button.builder(Component.literal("Clear"), b -> this.itemBox.setValue(""))
				.bounds(this.panelX + boxW + GAP + smallW + GAP, y, smallW, ROW)
				.build();
		this.addRenderableWidget(clear);

		y += ROW + 2;
		this.statusY = y + 2;
		y += 14;

		// Fit mode and hide-armor toggle
		int halfW = (this.panelW - GAP) / 2;
		this.modeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			this.current().mode = this.current().mode.next();
			this.refresh();
		}).bounds(this.panelX, y, halfW, ROW).build());

		this.hideButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			this.current().hideArmor = !this.current().hideArmor;
			this.refresh();
		}).bounds(this.panelX + halfW + GAP, y, halfW, ROW).build());
		this.hideButton.setTooltip(Tooltip.create(Component.literal("Hide your real armor in this slot while the cosmetic is shown")));

		y += ROW + GAP;

		// Offset (pixels), rotation (degrees), size
		int thirdW = (this.panelW - GAP * 2) / 3;
		this.offsetX = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW, ROW, "Move X", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetX = (float) v));
		this.offsetY = this.addRenderableWidget(new ValueSlider(this.panelX + thirdW + GAP, y, thirdW, ROW, "Move Y", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetY = (float) v));
		this.offsetZ = this.addRenderableWidget(new ValueSlider(this.panelX + 2 * (thirdW + GAP), y, thirdW, ROW, "Move Z", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetZ = (float) v));
		y += ROW + GAP;

		this.rotX = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW, ROW, "Turn X", -180, 180, 5, "%.0f", 0, v -> this.current().rotX = (float) v));
		this.rotY = this.addRenderableWidget(new ValueSlider(this.panelX + thirdW + GAP, y, thirdW, ROW, "Turn Y", -180, 180, 5, "%.0f", 0, v -> this.current().rotY = (float) v));
		this.rotZ = this.addRenderableWidget(new ValueSlider(this.panelX + 2 * (thirdW + GAP), y, thirdW, ROW, "Turn Z", -180, 180, 5, "%.0f", 0, v -> this.current().rotZ = (float) v));
		y += ROW + GAP;

		this.scale = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW * 2 + GAP, ROW, "Size", 0.25, 3.0, 0.05, "%.2f", 1, v -> this.current().scale = (float) v));
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
			this.current().resetTransform();
			this.refresh();
		}).bounds(this.panelX + 2 * (thirdW + GAP), y, thirdW, ROW).build());

		// Bottom row
		int bottomY = this.height - 28;
		this.addRenderableWidget(new ValueSlider(10, bottomY, this.previewRight - 14, ROW, "Turn view", 0, 360, 5, "%.0f", this.previewYaw, v -> this.previewYaw = (float) v));

		this.enabledButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
			config().enabled = !config().enabled;
			this.refresh();
		}).bounds(this.panelX, bottomY, halfW, ROW).build());

		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.panelX + halfW + GAP, bottomY, halfW, ROW)
				.build());

		this.refresh();
	}

	private void select(CosmeticSlot slot) {
		this.selected = slot;
		this.itemBox.setValue(this.current().item);
		this.refresh();
	}

	/** Off, then each armor material in turn, applied to all four slots. */
	private void cycleArmorSet() {
		ArmorSet current = ArmorSet.wornBy(config());
		ArmorSet[] sets = ArmorSet.values();
		ArmorSet next;

		if (current == null) {
			next = ArmorSet.noneWorn(config()) ? sets[0] : null;
		} else {
			next = current.ordinal() + 1 < sets.length ? sets[current.ordinal() + 1] : null;
		}

		for (CosmeticSlot slot : CosmeticSlot.values()) {
			config().get(slot).item = next == null ? "" : next.itemId(slot);
		}

		this.itemBox.setValue(this.current().item);
		this.refresh();
	}

	private void useHeldItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		ItemStack held = player.getMainHandItem();
		this.itemBox.setValue(held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
	}

	/** Syncs every widget with the selected slot's settings. */
	private void refresh() {
		SlotSettings s = this.current();
		CosmeticSlot[] slots = CosmeticSlot.values();

		for (int i = 0; i < this.slotButtons.size(); i++) {
			this.slotButtons.get(i).active = slots[i] != this.selected;
		}

		ArmorSet set = ArmorSet.wornBy(config());
		String setLabel = set != null ? set.label : ArmorSet.noneWorn(config()) ? "Off (real armor)" : "Custom";
		this.setButton.setMessage(Component.literal("Armor look: " + setLabel));

		// Real armor pieces use the vanilla armor model, so fitting and transforms don't apply to them.
		boolean fitted = !VanityCosmetics.isArmorLook(this.selected, s.stack());
		this.modeButton.active = fitted;
		this.hideButton.active = fitted;
		this.offsetX.active = fitted;
		this.offsetY.active = fitted;
		this.offsetZ.active = fitted;
		this.rotX.active = fitted;
		this.rotY.active = fitted;
		this.rotZ.active = fitted;
		this.scale.active = fitted;

		this.modeButton.setMessage(Component.literal("Fit: " + s.mode.label));
		this.modeButton.setTooltip(Tooltip.create(Component.literal(s.mode.description)));
		this.hideButton.setMessage(Component.literal("Hide armor: " + (s.hideArmor ? "ON" : "OFF")));
		this.enabledButton.setMessage(Component.literal("Cosmetics: " + (config().enabled ? "ON" : "OFF")));
		this.offsetX.setRealValue(s.offsetX);
		this.offsetY.setRealValue(s.offsetY);
		this.offsetZ.setRealValue(s.offsetZ);
		this.rotX.setRealValue(s.rotX);
		this.rotY.setRealValue(s.rotY);
		this.rotZ.setRealValue(s.rotZ);
		this.scale.setRealValue(s.scale);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x0 = 10;
		int y0 = 28;
		int x1 = this.previewRight - 4;
		int y1 = this.height - 34;
		graphics.fill(x0, y0, x1, y1, 0x66000000);

		super.extractRenderState(graphics, mouseX, mouseY, a);

		graphics.centeredText(this.font, this.title, this.width / 2, 10, WHITE);

		SlotSettings s = this.current();
		ItemStack stack = s.stack();

		if (s.item.isBlank()) {
			graphics.text(this.font, Component.literal("Showing your real armor."), this.panelX, this.statusY, GRAY);
		} else if (stack.isEmpty()) {
			graphics.text(this.font, Component.literal("Unknown item id"), this.panelX, this.statusY, RED);
		} else if (VanityCosmetics.isArmorLook(this.selected, stack)) {
			graphics.text(this.font, Component.literal("Looks like ").append(stack.getHoverName()), this.panelX, this.statusY, GREEN);
		} else {
			graphics.text(this.font, Component.literal(this.selected.label + ": ").append(stack.getHoverName()).append(" (fitted)"), this.panelX, this.statusY, GREEN);
		}

		LocalPlayer player = this.minecraft.player;

		if (player != null) {
			int size = (int) Math.min((y1 - y0) / 2.6F, (x1 - x0) / 1.4F);
			this.extractPreview(graphics, x0, y0, x1, y1, size, mouseX, mouseY, player);
		}
	}

	/** Same as the inventory's player preview (follows the mouse), plus a turntable angle. */
	private void extractPreview(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float mouseX, float mouseY, LocalPlayer player) {
		float centerX = (x0 + x1) / 2.0F;
		float centerY = (y0 + y1) / 2.0F;
		float xAngle = (float) Math.atan((centerX - mouseX) / 40.0F);
		float yAngle = (float) Math.atan((centerY - mouseY) / 40.0F);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf xRotation = new Quaternionf().rotateX((float) Math.toRadians(yAngle * 20.0F));
		rotation.mul(xRotation);

		var renderer = this.minecraft.getEntityRenderDispatcher().getRenderer(player);
		EntityRenderState renderState = renderer.createRenderState(player, 1.0F);
		renderState.shadowPieces.clear();
		renderState.outlineColor = 0;

		if (renderState instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + xAngle * 20.0F + this.previewYaw;
			living.yRot = xAngle * 20.0F;
			living.xRot = living.pose != Pose.FALL_FLYING ? -yAngle * 20.0F : 0.0F;
			living.boundingBoxWidth /= living.scale;
			living.boundingBoxHeight /= living.scale;
			living.scale = 1.0F;
		}

		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
		graphics.entity(renderState, (float) size, translation, rotation, xRotation, x0, y0, x1, y1);
	}

	@Override
	public void removed() {
		config().save();
		super.removed();
	}
}
