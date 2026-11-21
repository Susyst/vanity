package dev.susyst.vanity.screen;

import java.util.ArrayList;
import java.util.List;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import dev.susyst.vanity.VanityClient;
import dev.susyst.vanity.config.ArmorSet;
import dev.susyst.vanity.config.CosmeticSlot;
import dev.susyst.vanity.config.SlotSettings;
import dev.susyst.vanity.config.VanityConfig;
import dev.susyst.vanity.render.VanityCosmetics;

/**
 * The wardrobe. Pick what each armor slot looks like: another armor (wear diamond, show netherite), a SkyBlock piece,
 * or any item or block, with a live preview. Armor looks get glint, trim and dye options; other items get fit options.
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
	/** Shown for armor looks (and empty slots). */
	private final List<AbstractWidget> armorWidgets = new ArrayList<>();
	/** Shown for other items, which are fitted to the armor shape. */
	private final List<AbstractWidget> fitWidgets = new ArrayList<>();

	private EditBox itemBox;
	private Button setButton;
	private Button invisibleButton;
	private Button wornOnlyButton;
	private Button glintButton;
	private Button trimButton;
	private Button dyeButton;
	private ValueSlider dyeRed;
	private ValueSlider dyeGreen;
	private ValueSlider dyeBlue;
	private Button modeButton;
	private Button hideButton;
	private ValueSlider scale;
	private ValueSlider offsetX;
	private ValueSlider offsetY;
	private ValueSlider offsetZ;
	private ValueSlider rotX;
	private ValueSlider rotY;
	private ValueSlider rotZ;
	private Button enabledButton;
	private Button inventoryButton;

	/** True while widgets are being updated from code, so the item box and sliders don't treat it as user input. */
	private boolean syncing;
	private boolean initialized;
	private int previewRight;
	private int panelX;
	private int panelW;
	private int statusY;

	public VanityScreen() {
		this(CosmeticSlot.HEAD);
	}

	public VanityScreen(CosmeticSlot initialSlot) {
		super(Component.translatable("screen.vanity.title"));
		this.selected = initialSlot;
	}

	private static VanityConfig config() {
		return VanityClient.config();
	}

	private SlotSettings current() {
		return config().get(this.selected);
	}

	private static Tooltip tip(String text) {
		return Tooltip.create(Component.literal(text));
	}

	private Button button(int x, int y, int w, Button.OnPress onPress) {
		return this.addRenderableWidget(Button.builder(Component.empty(), onPress).bounds(x, y, w, ROW).build());
	}

	@Override
	protected void init() {
		this.initialized = false;
		this.slotButtons.clear();
		this.armorWidgets.clear();
		this.fitWidgets.clear();
		this.previewRight = Math.max(110, (int) (this.width * 0.38F));
		this.panelX = this.previewRight + 8;
		this.panelW = Math.max(180, Math.min(this.width - this.panelX - 10, 300));
		int thirdW = (this.panelW - GAP * 2) / 3;
		int x2 = this.panelX + thirdW + GAP;
		int x3 = this.panelX + 2 * (thirdW + GAP);

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
		this.setButton = this.button(this.panelX, y, this.panelW, b -> this.cycleArmorSet());
		this.setButton.setTooltip(tip("Click to change how all four armor slots look. Your real armor stays the same; only the look changes."));
		y += ROW + 6;

		// Item id, "use held item", "use worn item", clear
		int smallW = 36;
		int boxW = this.panelW - 3 * (smallW + GAP);
		this.itemBox = new EditBox(this.font, this.panelX, y, boxW, ROW, Component.literal("Item ID"));
		this.itemBox.setMaxLength(128);
		this.itemBox.setHint(Component.literal("netherite_chestplate"));
		this.itemBox.setValue(this.current().item);
		this.itemBox.moveCursorToStart(false);
		this.itemBox.setResponder(value -> {
			if (this.syncing) return;
			// Typing an id switches the slot to a plain item.
			this.current().setLook(value.trim());
			this.refresh();
		});
		this.addRenderableWidget(this.itemBox);

		Button held = Button.builder(Component.literal("Held"), b -> this.useHeldItem())
				.bounds(this.panelX + boxW + GAP, y, smallW, ROW).build();
		held.setTooltip(tip("Use the exact item in your main hand, including its name, dye color, head skin and SkyBlock data"));
		this.addRenderableWidget(held);

		Button worn = Button.builder(Component.literal("Worn"), b -> this.useWornItem())
				.bounds(this.panelX + boxW + GAP + smallW + GAP, y, smallW, ROW).build();
		worn.setTooltip(tip("Save the look of what you're wearing in this slot right now (e.g. a SkyBlock armor piece). Then you can wear something else and keep this look."));
		this.addRenderableWidget(worn);

		Button clear = Button.builder(Component.literal("Clear"), b -> {
			this.current().clearLook();
			this.syncItemBox();
			this.refresh();
		}).bounds(this.panelX + boxW + GAP + 2 * (smallW + GAP), y, smallW, ROW).build();
		clear.setTooltip(tip("Go back to your real armor in this slot"));
		this.addRenderableWidget(clear);

		y += ROW + 2;
		this.statusY = y + 2;
		y += 14;

		// Options for every slot
		this.invisibleButton = this.button(this.panelX, y, thirdW, b -> {
			this.current().invisible = !this.current().invisible;
			this.refresh();
		});
		this.invisibleButton.setTooltip(tip("Show nothing in this slot, not even your real armor"));

		this.wornOnlyButton = this.button(x2, y, thirdW, b -> {
			this.current().onlyWhenWorn = !this.current().onlyWhenWorn;
			this.refresh();
		});
		this.wornOnlyButton.setTooltip(tip("Hide: the look disappears when you take the armor off this slot. Show: the look stays even with nothing equipped."));

		Button reset = this.button(x3, y, thirdW, b -> {
			SlotSettings s = this.current();
			if (this.showsArmorOptions()) {
				s.resetArmorStyle();
			} else {
				s.resetTransform();
			}
			this.refresh();
		});
		reset.setMessage(Component.literal("Reset"));
		reset.setTooltip(tip("Reset this slot's options below"));
		y += ROW + GAP;

		int optionsY = y;

		// Armor look options: glint, trim, dye
		this.glintButton = this.button(this.panelX, y, thirdW, b -> {
			this.current().glint = this.current().glint.next();
			this.refresh();
		});
		this.glintButton.setTooltip(tip("Enchantment shine. Like real: shine if the armor you're wearing is enchanted."));
		this.trimButton = this.button(x2, y, thirdW, b -> {
			this.current().keepTrim = !this.current().keepTrim;
			this.refresh();
		});
		this.trimButton.setTooltip(tip("Keep the trim of the armor you're wearing on the look, or show no trim"));
		this.dyeButton = this.button(x3, y, thirdW, b -> {
			this.current().customDye = !this.current().customDye;
			this.refresh();
		});
		this.dyeButton.setTooltip(tip("Recolor leather looks with the sliders below"));
		y += ROW + GAP;

		this.dyeRed = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW, ROW, "Red", 0, 255, 1, "%.0f", 0, v -> this.setDyeChannel(16, v)));
		this.dyeGreen = this.addRenderableWidget(new ValueSlider(x2, y, thirdW, ROW, "Green", 0, 255, 1, "%.0f", 0, v -> this.setDyeChannel(8, v)));
		this.dyeBlue = this.addRenderableWidget(new ValueSlider(x3, y, thirdW, ROW, "Blue", 0, 255, 1, "%.0f", 0, v -> this.setDyeChannel(0, v)));
		this.armorWidgets.addAll(List.of(this.glintButton, this.trimButton, this.dyeButton, this.dyeRed, this.dyeGreen, this.dyeBlue));

		// Fit options for non-armor items, in the same space
		y = optionsY;
		this.modeButton = this.button(this.panelX, y, thirdW, b -> {
			this.current().mode = this.current().mode.next();
			this.refresh();
		});
		this.hideButton = this.button(x2, y, thirdW, b -> {
			this.current().hideArmor = !this.current().hideArmor;
			this.refresh();
		});
		this.hideButton.setTooltip(tip("Hide your real armor in this slot while the item is shown"));
		this.scale = this.addRenderableWidget(new ValueSlider(x3, y, thirdW, ROW, "Size", 0.25, 3.0, 0.05, "%.2f", 1, v -> this.current().scale = (float) v));
		y += ROW + GAP;

		this.offsetX = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW, ROW, "Move X", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetX = (float) v));
		this.offsetY = this.addRenderableWidget(new ValueSlider(x2, y, thirdW, ROW, "Move Y", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetY = (float) v));
		this.offsetZ = this.addRenderableWidget(new ValueSlider(x3, y, thirdW, ROW, "Move Z", -16, 16, 0.5, "%.1f", 0, v -> this.current().offsetZ = (float) v));
		y += ROW + GAP;

		this.rotX = this.addRenderableWidget(new ValueSlider(this.panelX, y, thirdW, ROW, "Turn X", -180, 180, 5, "%.0f", 0, v -> this.current().rotX = (float) v));
		this.rotY = this.addRenderableWidget(new ValueSlider(x2, y, thirdW, ROW, "Turn Y", -180, 180, 5, "%.0f", 0, v -> this.current().rotY = (float) v));
		this.rotZ = this.addRenderableWidget(new ValueSlider(x3, y, thirdW, ROW, "Turn Z", -180, 180, 5, "%.0f", 0, v -> this.current().rotZ = (float) v));
		this.fitWidgets.addAll(List.of(this.modeButton, this.hideButton, this.scale, this.offsetX, this.offsetY, this.offsetZ, this.rotX, this.rotY, this.rotZ));

		// Bottom row
		int bottomY = this.height - 28;
		this.addRenderableWidget(new ValueSlider(10, bottomY, this.previewRight - 14, ROW, "Turn view", 0, 360, 5, "%.0f", this.previewYaw, v -> this.previewYaw = (float) v));

		this.enabledButton = this.button(this.panelX, bottomY, thirdW, b -> {
			config().enabled = !config().enabled;
			this.refresh();
		});
		this.enabledButton.setTooltip(tip("Turn all Vanity looks on or off"));

		this.inventoryButton = this.button(x2, bottomY, thirdW, b -> {
			config().showInInventory = !config().showInInventory;
			this.refresh();
		});
		this.inventoryButton.setTooltip(tip("Also show the looks on your worn armor's icons in the inventory"));

		Button done = this.button(x3, bottomY, thirdW, b -> this.onClose());
		done.setMessage(Component.literal("Done"));

		this.initialized = true;
		this.refresh();
	}

	private void setDyeChannel(int shift, double value) {
		if (this.syncing) return;
		SlotSettings s = this.current();
		int channel = (int) Math.round(value) & 0xFF;
		s.dyeColor = (s.dyeColor & ~(0xFF << shift)) | (channel << shift);
		s.customDye = true;
		this.refresh();
	}

	private void select(CosmeticSlot slot) {
		this.selected = slot;
		this.syncItemBox();
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
			config().get(slot).setLook(next == null ? "" : next.itemId(slot));
		}

		this.syncItemBox();
		this.refresh();
	}

	private void useHeldItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.useItem(player.getMainHandItem(), player);
	}

	private void useWornItem() {
		LocalPlayer player = this.minecraft.player;
		if (player == null) return;
		this.useItem(player.getItemBySlot(this.selected.equipmentSlot), player);
	}

	private void useItem(ItemStack stack, LocalPlayer player) {
		this.current().setLook(stack, player.registryAccess());
		this.syncItemBox();
		this.refresh();
	}

	/** Shows the selected slot's item id in the box without treating it as typing. */
	private void syncItemBox() {
		this.syncing = true;
		this.itemBox.setValue(this.current().item);
		this.itemBox.moveCursorToStart(false);
		this.syncing = false;
	}

	/** Armor looks (and empty slots) show glint/trim/dye options; other items show fit options. */
	private boolean showsArmorOptions() {
		ItemStack stack = this.current().stack();
		return stack.isEmpty() || VanityCosmetics.usesVanillaLook(this.selected, stack);
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}

	/** Syncs every widget with the selected slot's settings. */
	private void refresh() {
		if (!this.initialized) return;
		this.syncing = true;

		SlotSettings s = this.current();
		ItemStack stack = s.stack();
		CosmeticSlot[] slots = CosmeticSlot.values();

		for (int i = 0; i < this.slotButtons.size(); i++) {
			this.slotButtons.get(i).active = slots[i] != this.selected;
		}

		ArmorSet set = ArmorSet.wornBy(config());
		String setLabel = set != null ? set.label : ArmorSet.noneWorn(config()) ? "Off (real armor)" : "Custom";
		this.setButton.setMessage(Component.literal("Armor look: " + setLabel));

		this.invisibleButton.setMessage(Component.literal("Invisible: " + onOff(s.invisible)));
		this.wornOnlyButton.setMessage(Component.literal("No armor: " + (s.onlyWhenWorn ? "Hide" : "Show")));

		boolean armorOptions = this.showsArmorOptions();
		for (AbstractWidget widget : this.armorWidgets) widget.visible = armorOptions;
		for (AbstractWidget widget : this.fitWidgets) widget.visible = !armorOptions;

		// Armor options
		boolean armorLook = VanityCosmetics.isArmorLook(this.selected, stack) && !s.invisible;
		boolean dyeable = armorLook && VanityCosmetics.isDyeable(stack);
		this.glintButton.active = armorLook;
		this.trimButton.active = armorLook;
		this.dyeButton.active = dyeable;
		this.dyeRed.active = dyeable;
		this.dyeGreen.active = dyeable;
		this.dyeBlue.active = dyeable;
		this.glintButton.setMessage(Component.literal("Glint: " + s.glint.label));
		this.trimButton.setMessage(Component.literal("Trim: " + (s.keepTrim ? "Keep" : "Hide")));

		MutableComponent dyeLabel = Component.literal("Dye: " + onOff(s.customDye));
		if (s.customDye) dyeLabel.append(" ").append(Component.literal("██").withColor(s.dyeColor));
		this.dyeButton.setMessage(dyeLabel);
		this.dyeRed.setRealValue((s.dyeColor >> 16) & 0xFF);
		this.dyeGreen.setRealValue((s.dyeColor >> 8) & 0xFF);
		this.dyeBlue.setRealValue(s.dyeColor & 0xFF);

		// Fit options
		boolean fitActive = !s.invisible;
		for (AbstractWidget widget : this.fitWidgets) widget.active = fitActive;
		this.modeButton.setMessage(Component.literal("Fit: " + s.mode.label));
		this.modeButton.setTooltip(armorOptions ? null : tip(s.mode.description));
		this.hideButton.setMessage(Component.literal("Hide armor: " + onOff(s.hideArmor)));
		this.offsetX.setRealValue(s.offsetX);
		this.offsetY.setRealValue(s.offsetY);
		this.offsetZ.setRealValue(s.offsetZ);
		this.rotX.setRealValue(s.rotX);
		this.rotY.setRealValue(s.rotY);
		this.rotZ.setRealValue(s.rotZ);
		this.scale.setRealValue(s.scale);

		this.enabledButton.setMessage(Component.literal("Looks: " + onOff(config().enabled)));
		this.inventoryButton.setMessage(Component.literal("Inv icons: " + onOff(config().showInInventory)));

		this.syncing = false;
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

		if (s.invisible) {
			graphics.text(this.font, Component.literal(this.selected.label + " is invisible."), this.panelX, this.statusY, GRAY);
		} else if (s.item.isBlank()) {
			graphics.text(this.font, Component.literal("Showing your real armor."), this.panelX, this.statusY, GRAY);
		} else if (stack.isEmpty()) {
			graphics.text(this.font, Component.literal("Unknown item id"), this.panelX, this.statusY, RED);
		} else if (VanityCosmetics.usesVanillaLook(this.selected, stack)) {
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
