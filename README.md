# Vanity

Client-side armor looks for Minecraft **26.3** (Fabric). Wear diamond armor but show netherite, gold, leather or
any other armor, per slot or as a whole set. Your real armor and its protection don't change; only the look does.
Enchantment shine and armor trims from the armor you're really wearing carry over to the new look.

Any other item or block can be worn too: it's fitted to the armor shape (glass in the Head slot becomes a glass helmet).

## Use

1. Install Fabric Loader 0.20.1+ and Fabric API for 26.3, then drop `vanity-0.15.1.jar` into `mods/`.
2. In game press **V** (rebindable under Controls → Vanity) to open the wardrobe.
3. On the **Look** page, open **All slots look like** to change all four slots at once: Leather, Chainmail, Iron,
   Gold, Copper, Diamond or Netherite (**Real armor** turns it off).
4. For one slot only, pick its tab and type an armor id (`netherite_chestplate`, `golden_helmet`...), or hold one and
   press **Held**. **Clear** goes back to your real armor for that slot.

The wardrobe is a frosted-glass panel over your blurred game. Pick a slot (or **All** to change every piece at
once), then a page. Every choice with more than two options is a dropdown.
- **Look**: what the slot looks like (armor id, Held, Worn, Clear), plus **Invisible** and **No armor: Hide/Show**
  (by default a look disappears when you take that armor off).
- **Style**: **Glint** (Real / On / Off), **Dye** for leather: Solid, **Rainbow**, **Pulse** or **Shimmer**, with a
  **Speed** slider (colors, RGB sliders, hex), a glow animation (**Steady**, **Breathe**, **Rainbow glow**, **Flicker**),
  **Opacity**
  (see-through armor), **Glow** (armor that stays bright in the dark) and **Trim glow** (a soft glow on trims and
  designs).
- **Trim**: keep your armor's trim, no trim, or pick any vanilla pattern and material.
- **Design**: custom trims, up to 3 trim patterns stacked, each in any color. **Colors** animates them on any armor
  (Rainbow, Pulse, Shimmer).
- **Rune**: particle effects on each piece, like SkyBlock runes but bigger: 18 effects in 6 shapes (halo, orbit
  rings, spiral, wings, aura, trail). **Detail** is how many evenly spaced points make the shape (the whole shape is
  always drawn), **Length** how long each point lasts. **React**: a burst of the rune when you get hit, and twice as
  dense, bigger and faster while sprinting (Hit / Sprint / Both / Off). Only you see them; press F5.
- **Outfits**: **Save new** stores all four slots as an outfit (looks, styles, trims, designs and runes). Pick one to
  wear it, rename it, **Update** it with your current look or **Delete** it. Press **N** in game for the next outfit
  (rebindable; there's also an unbound Previous outfit key). Up to 12 outfits. **Auto-wear** puts the picked outfit on
  every time you join the server (or singleplayer world) you're in now.
- **Hand** tab, item skins: press **New skin...** and pick the item to skin, then type a look id (`netherite_sword`),
  press **Held** for what's in your hand, or **Pick** one. The item you hold is drawn as the look in first and
  third person, in your hotbar and inventory, with texture packs. SkyBlock items match by their SkyBlock id, so only
  that item gets the skin; other items match by type. The real item still works the same.

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

## Custom armors

- **Vanity's own designs**, not in vanilla: Frostforged, Obsidian Knight, Ember, Verdant, Royal Guard, Void Walker,
  Sakura and Circuit. Pick them in **All slots look like**, or type `vanity:frostforged` in a slot. They're worn
  through the normal armor renderer, so trims, glint, opacity, glow and runes work on them, and texture packs can
  change them (`assets/vanity/textures/entity/equipment/humanoid/<id>.png`).
  - Parts of them **glow in the dark** (lava cracks, circuit lines, stars, gems), some with a slow pulse.
  - **3D pieces** that change the silhouette: horns, crests, plumes, shoulder plates, ice crystals, leaves, an antenna
    and power core, a glowing halo with floating orbs. **3D: OFF** on the Look page hides them for that slot.
- **Your own armor**: **All slots look like → + Your own armor...** opens `config/vanity/armors`. Put `<name>.png`
  there (a 64x32 armor texture, or 128x64 and up) and optionally `<name>_leggings.png` and `<name>_glow.png` (pixels
  that glow in the dark); it shows up as "<Name> (yours)"
  the next time you open the wardrobe (or type `vanity:user/<name>`). `examples/template.png` shows which pixels go on
  which face, and `examples/example.png` is Frostforged to start from.

## Hit and kill effects

**Hand → Hit & kill**: particles where you hit something (Sparks, Blood, Hearts, Flame, Soul fire, Rainbow, Lightning,
Magic, Notes) and an effect where something you hit dies (Soul burst, Lightning, Firework, Blood, Hearts, Totem,
Explosion, Rainbow ring, Void). Color (for Sparks and Firework), size, amount, sounds, and whether they play on players.
**Try it** plays them in front of you. Only you see them.

## SkyBlock and custom textures

- **Grab key (K)**: in any inventory, chest, menu or the Auction House, hover an item and press **K**. It's saved with
  all its data, so its texture pack look comes along. **Pick** (Look page and Hand tab) shows your grabbed items and
  your own items with their real textures; click one to use it. Right-click a grabbed item to remove it.
- **SkyBlock ids**: type `sb:HYPERION` (or just `HYPERION`) as a look, even for items you don't own. It makes an item
  carrying that SkyBlock id and name, so texture packs and mods that texture SkyBlock items by id show their texture.
  Armor ids are made on leather pieces and held-item ids on the item you hold; add `@stone_sword` to pick the item.

## Build and test

Requires Java 25. `./gradlew build` produces `build/libs/vanity-<version>.jar`.
GitHub Actions builds every push and also pushes the jar and log to the `build-output` branch.
When `main` gets a new `mod_version` with a `changelog/<version>.md`, and both the build and the in-game test pass, it
publishes a GitHub release with that jar on its own.

`./gradlew runClientGameTest` starts a real client (Fabric client game test), equips glass, solid blocks, a pumpkin
and a sword as cosmetics, and takes screenshots into `build/run/clientGameTest/screenshots`. CI runs it in a virtual
display and pushes the screenshots to the `test-output` branch.
