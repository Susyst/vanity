# Vanity

Client-side armor looks for Minecraft **26.3** (Fabric). Wear diamond armor but show netherite, gold, leather or
any other armor, per slot or as a whole set. Your real armor and its protection don't change; only the look does.
Enchantment shine and armor trims from the armor you're really wearing carry over to the new look.

Any other item or block can be worn too: it's fitted to the armor shape (glass in the Head slot becomes a glass helmet).

## Use

1. Install Fabric Loader 0.20.1+ and Fabric API for 26.3, then drop `vanity-0.10.0.jar` into `mods/`.
2. In game press **V** (rebindable under Controls → Vanity) to open the wardrobe.
3. On the **Look** page, open **All slots look like** to change all four slots at once: Leather, Chainmail, Iron,
   Gold, Copper, Diamond or Netherite (**Real armor** turns it off).
4. For one slot only, pick its tab and type an armor id (`netherite_chestplate`, `golden_helmet`...), or hold one and
   press **Held**. **Clear** goes back to your real armor for that slot.

The wardrobe is a frosted-glass panel over your blurred game. Pick a slot (or **All** to change every piece at
once), then a page. Every choice with more than two options is a dropdown.
- **Look**: what the slot looks like (armor id, Held, Worn, Clear), plus **Invisible** and **No armor: Hide/Show**
  (by default a look disappears when you take that armor off).
- **Style**: **Glint** (Like real / On / Off), **Dye** for leather (colors, RGB sliders, hex), **Opacity**
  (see-through armor), **Glow** (armor that stays bright in the dark) and **Trim glow** (a soft glow on trims and
  designs).
- **Trim**: keep your armor's trim, no trim, or pick any vanilla pattern and material.
- **Design**: custom trims, up to 3 trim patterns stacked, each in any color.
- **Rune**: particle effects on each piece, like SkyBlock runes but bigger: 18 effects in 6 shapes (halo, orbit
  rings, spiral, wings, aura, trail). **Detail** is how many evenly spaced points make the shape (the whole shape is
  always drawn), **Length** how long each point lasts. Only you see them; press F5.

Style and Trim also work on your real armor when no look is set. On a single slot, **All slots** copies that page's
settings to the other slots. Items that aren't armor (like a glass block) are fitted to the armor shape automatically.

The look also shows on the icons of the armor you're wearing, in your inventory (turn off with **Inv icons**). Tooltips
keep the real item's name and add a "Vanity look" line.

Settings save to `config/vanity.json`. Only you see the cosmetics.

### SkyBlock armor and other custom items

**Held** and **Worn** save the exact item, not just its type: custom name, dye color, player-head skin and SkyBlock
data all stay. Wear a SkyBlock piece, open its slot's tab and press **Worn**; you can then wear anything else and keep
that look. Player-head helmets (most SkyBlock helmets) are drawn like a worn head, with their skin.

## How fitting works

`ArmorFit` reads the cube of each body part from your actual player model (so slim arms are handled), grows it the
same amount vanilla armor does (1px for helmet/chest/boots, 0.5px for leggings), then measures the item model's real
bounding box (`ItemStackRenderState#getModelBoundingBox`) and scales/centers it to fill that box.

## Download

Get the jar from [Releases](https://github.com/Susyst/vanity/releases).

## Build and test

Requires Java 25. `./gradlew build` produces `build/libs/vanity-<version>.jar`.
GitHub Actions builds every push and also pushes the jar and log to the `build-output` branch.

`./gradlew runClientGameTest` starts a real client (Fabric client game test), equips glass, solid blocks, a pumpkin
and a sword as cosmetics, and takes screenshots into `build/run/clientGameTest/screenshots`. CI runs it in a virtual
display and pushes the screenshots to the `test-output` branch.
