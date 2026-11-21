# Vanity

Client-side cosmetics for Minecraft **26.3** (Fabric). Wear any item or block as armor on your own player.

The point of 0.4: models are **fitted to the real armor shape**, not shown as miniature items. Put glass in the
Head slot and you get a see-through glass helmet around your head. Put it in Chest and you get glass around your
torso and both arms; Legs covers the belt and both legs; Feet covers the lower legs like boots. Every piece is
attached to its body part, so it swings with your arms and legs and follows crouching.

## Use

1. Install Fabric Loader 0.20.1+ and Fabric API for 26.3, then drop `vanity-0.4.0.jar` into `mods/`.
2. In game press **V** (rebindable under Controls → Vanity) to open the wardrobe.
3. Pick a slot tab, type an item id (`glass`, `minecraft:tinted_glass`, `diamond_block`...) or hold an item and press
   **Held**.
4. Choose a fit:
   - **Armor shape**: stretches the model to fill the armor box exactly. Best for blocks.
   - **Fit inside**: scales evenly so the model fits inside the armor box. Best for flat items.
   - **Vanilla size**: normal head-item size, like a pumpkin.
5. Fine-tune with Move X/Y/Z (pixels), Turn X/Y/Z (degrees) and Size. Turning keeps the model filling the armor
   shape. **Hide armor** hides your real armor in that slot.

Settings save to `config/vanity.json`. Only you see the cosmetics.

## How fitting works

`ArmorFit` reads the cube of each body part from your actual player model (so slim arms are handled), grows it the
same amount vanilla armor does (1px for helmet/chest/boots, 0.5px for leggings), then measures the item model's real
bounding box (`ItemStackRenderState#getModelBoundingBox`) and scales/centers it to fill that box.

## Build

Requires Java 25. `./gradlew build` produces `build/libs/vanity-<version>.jar`.
GitHub Actions builds every push and also pushes the jar and log to the `build-output` branch.
