"""Paints Vanity's built-in custom armor designs onto the 64x32 armor texture layout.

Writes src/main/resources/assets/vanity/textures/entity/equipment/{humanoid,humanoid_leggings}/<id>.png,
the matching equipment/<id>.json files, and the template/examples copied into config/vanity/armors/.
Run: python3 tools/make_armor_textures.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "vanity")

# Face rectangles (x, y, w, h) on the 64x32 armor layout
HEAD = {"top": (8, 0, 8, 8), "bottom": (16, 0, 8, 8), "right": (0, 8, 8, 8), "front": (8, 8, 8, 8), "left": (16, 8, 8, 8), "back": (24, 8, 8, 8)}
BODY = {"top": (20, 16, 8, 4), "bottom": (28, 16, 8, 4), "right": (16, 20, 4, 12), "front": (20, 20, 8, 12), "left": (28, 20, 4, 12), "back": (32, 20, 8, 12)}
ARM = {"top": (44, 16, 4, 4), "bottom": (48, 16, 4, 4), "right": (40, 20, 4, 12), "front": (44, 20, 4, 12), "left": (48, 20, 4, 12), "back": (52, 20, 4, 12)}
LEG = {"top": (4, 16, 4, 4), "bottom": (8, 16, 4, 4), "right": (0, 20, 4, 12), "front": (4, 20, 4, 12), "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}
SIDES = ("right", "front", "left", "back")


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


class Design:
    def __init__(self, ident, name, base, edge, accent, icon, seed):
        self.id, self.name, self.base, self.edge, self.accent, self.icon = ident, name, base, edge, accent, icon
        self.rng = random.Random(seed)
        self.visor = "open"

    def base_color(self, x, y, face):
        n = self.rng.uniform(-0.07, 0.07)
        return shade(self.base, 1.0 + n)

    def plate(self, img, rect, face, rows=None):
        """Fill a face: base color, darker 1px edge, lighter top-left bevel. rows=(y0, y1) limits the rows."""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            if rows and not (rows[0] <= y < rows[1]):
                continue
            for x in range(x0, x0 + w):
                c = self.base_color(x, y, face)
                top = rows[0] if rows else y0
                bottom = (rows[1] if rows else y0 + h) - 1
                if x in (x0, x0 + w - 1) or y in (top, bottom):
                    c = mix(c, self.edge, 0.75)
                elif x == x0 + 1 or y == top + 1:
                    c = shade(c, 1.12)
                img.putpixel((x, y), c)

    def pattern(self, img, rect, part, face, rows=None):
        pass

    def inside(self, rect, x, y, rows=None, margin=1):
        x0, y0, w, h = rect
        top = rows[0] if rows else y0
        bottom = rows[1] if rows else y0 + h
        return x0 + margin <= x < x0 + w - margin and top + margin <= y < bottom - margin

    def paint(self):
        a = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        b = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        parts = [(a, HEAD, "head", None), (a, BODY, "body", None), (a, ARM, "arm", None), (a, LEG, "boots", (27, 32)),
                 (b, BODY, "belt", (20, 26)), (b, LEG, "pants", (20, 29))]
        for img, faces, part, rows in parts:
            for face, rect in faces.items():
                if rows and face in ("top", "bottom"):
                    if not (part == "boots" and face == "bottom"):
                        continue
                    self.plate(img, rect, face)
                    self.pattern(img, rect, part, face)
                    continue
                self.plate(img, rect, face, rows)
                self.pattern(img, rect, part, face, rows)
        self.face_opening(a)
        return a, b

    def face_opening(self, img):
        if self.visor == "open":
            for x in range(10, 14):
                for y in range(11, 14):
                    img.putpixel((x, y), (0, 0, 0, 0))
        elif self.visor == "slit":
            for x in range(9, 15):
                img.putpixel((x, 12), shade(self.accent, 1.0))
                img.putpixel((x, 11), shade(self.accent, 0.55))


class Frost(Design):
    def base_color(self, x, y, face):
        c = super().base_color(x, y, face)
        if (x + 2 * y) % 7 == 0:
            c = mix(c, (235, 250, 255), 0.45)
        return c

    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        for _ in range(max(1, w * h // 18)):
            x, y = self.rng.randrange(x0, x0 + w), self.rng.randrange(y0, y0 + h)
            if self.inside(rect, x, y, rows):
                img.putpixel((x, y), (245, 253, 255, 255))


class Obsidian(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        top = rows[0] if rows else y0
        bottom = rows[1] if rows else y0 + h
        if face in SIDES and w >= 4:
            for x in range(x0 + 1, x0 + w - 1):
                img.putpixel((x, top + 1), shade(self.accent, 0.8))
            if bottom - top > 4:
                for y in range(top + 1, bottom - 1):
                    img.putpixel((x0 + 1, y), shade(self.accent, 0.65))
        if face == "front" and part == "body":
            for x, y in ((23, 24), (24, 24), (23, 25), (24, 25)):
                img.putpixel((x, y), shade(self.accent, 1.25))
            img.putpixel((23, 24), (235, 200, 255, 255))
        if face == "front" and part == "head":
            img.putpixel((11, 9), shade(self.accent, 1.3))
            img.putpixel((12, 9), shade(self.accent, 1.3))


class Ember(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        for _ in range(max(1, w * h // 22)):
            x, y = self.rng.randrange(x0 + 1, x0 + w - 1), self.rng.randrange(y0 + 1, y0 + h - 1)
            for step in range(self.rng.randint(3, 7)):
                if not self.inside(rect, x, y, rows):
                    break
                img.putpixel((x, y), (255, 214, 90, 255) if step % 3 == 0 else self.accent)
                x += self.rng.choice((-1, 0, 1))
                y += self.rng.choice((0, 1, 1))


class Verdant(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        phase = self.rng.random() * 6
        for y in range(y0, y0 + h):
            x = x0 + int((w - 1) / 2 + (w / 2 - 1) * math.sin(y * 0.9 + phase))
            if self.inside(rect, x, y, rows):
                img.putpixel((x, y), self.accent)
                if self.rng.random() < 0.35 and self.inside(rect, x + 1, y, rows):
                    img.putpixel((x + 1, y), (160, 226, 96, 255))


class Royal(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        top = rows[0] if rows else y0
        bottom = rows[1] if rows else y0 + h
        gold = self.accent
        if face in SIDES and w >= 4 and bottom - top >= 4:
            for x in range(x0 + 1, x0 + w - 1):
                img.putpixel((x, bottom - 2), shade(gold, 0.85))
        if part == "body" and face == "front":
            for dx, dy in ((0, -2), (-1, -1), (0, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)):
                img.putpixel((24 + dx - (1 if dx > 0 else 0), 25 + dy), gold if abs(dx) + abs(dy) < 2 else shade(gold, 0.8))
        if part == "head" and face in ("top", "front", "back"):
            for y in range(y0, y0 + h):
                img.putpixel((x0 + w // 2 - 1, y), gold)
                img.putpixel((x0 + w // 2, y), shade(gold, 1.1))


class Void(Design):
    def base_color(self, x, y, face):
        t = (y % 16) / 15.0
        c = mix(self.base, (46, 20, 82), t * 0.8)
        return shade(c, 1.0 + self.rng.uniform(-0.08, 0.08))

    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        for _ in range(max(1, w * h // 14)):
            x, y = self.rng.randrange(x0, x0 + w), self.rng.randrange(y0, y0 + h)
            if self.inside(rect, x, y, rows):
                img.putpixel((x, y), self.rng.choice([(255, 255, 255, 255), (210, 180, 255, 255), (150, 110, 240, 255)]))


class Sakura(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        for _ in range(max(1, w * h // 20)):
            x, y = self.rng.randrange(x0 + 1, x0 + w - 1), self.rng.randrange(y0 + 1, y0 + h - 1)
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                if self.inside(rect, x + dx, y + dy, rows):
                    img.putpixel((x + dx, y + dy), (255, 236, 244, 255) if (dx, dy) != (0, 0) else (255, 196, 90, 255))
        if part == "body" and face == "front":
            for i in range(10):
                x, y = 21 + i * 6 // 10, 30 - i
                img.putpixel((x, y), (92, 56, 48, 255))


class Circuit(Design):
    def pattern(self, img, rect, part, face, rows=None):
        x0, y0, w, h = rect
        for _ in range(max(1, w * h // 26)):
            x, y = self.rng.randrange(x0 + 1, x0 + w - 1), self.rng.randrange(y0 + 1, y0 + h - 1)
            horizontal = self.rng.random() < 0.5
            for step in range(self.rng.randint(2, 6)):
                if not self.inside(rect, x, y, rows):
                    break
                img.putpixel((x, y), self.accent)
                if horizontal:
                    x += 1
                else:
                    y += 1
            if self.inside(rect, x, y, rows):
                img.putpixel((x, y), (200, 252, 255, 255))


DESIGNS = [
    Frost("frostforged", "Frostforged", (118, 196, 232), (38, 86, 146), (235, 250, 255), 0x7AC8EB, 11),
    Obsidian("obsidian_knight", "Obsidian Knight", (30, 22, 40), (8, 5, 14), (150, 80, 232), 0x3A2A52, 12),
    Ember("ember", "Ember", (48, 38, 36), (18, 12, 10), (255, 116, 28, 255), 0xC0501A, 13),
    Verdant("verdant", "Verdant", (64, 124, 58), (80, 52, 30), (122, 192, 82, 255), 0x3E7A3A, 14),
    Royal("royal_guard", "Royal Guard", (234, 231, 224), (196, 150, 46), (226, 180, 58, 255), 0xE8E6DE, 15),
    Void("void_walker", "Void Walker", (12, 10, 24), (96, 52, 168), (170, 110, 255, 255), 0x2A1446, 16),
    Sakura("sakura", "Sakura", (244, 172, 202), (196, 92, 134), (255, 236, 244, 255), 0xF2A7C3, 17),
    Circuit("circuit", "Circuit", (44, 50, 58), (22, 26, 32), (62, 226, 255, 255), 0x2C8FA8, 18),
]
DESIGNS[1].visor = "slit"
DESIGNS[5].visor = "slit"
DESIGNS[7].visor = "slit"


def template():
    """Every face in its own color, so you can see which pixels go where."""
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    legs = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    colors = {"top": (250, 220, 70), "bottom": (160, 130, 40), "right": (230, 120, 60), "front": (220, 60, 60), "left": (70, 160, 230), "back": (120, 80, 200)}
    for faces, part_shade in ((HEAD, 1.0), (BODY, 0.85), (ARM, 0.7), (LEG, 0.55)):
        for face, (x0, y0, w, h) in faces.items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    c = shade(colors[face], part_shade * (0.6 if edge else 1.0))
                    if faces is LEG and y < 27 and face not in ("bottom",):
                        legs.putpixel((x, y), c)
                        if face == "top":
                            img.putpixel((x, y), (0, 0, 0, 0))
                            continue
                        continue
                    img.putpixel((x, y), c)
                    if faces is BODY and y < 26 and face not in ("top", "bottom"):
                        legs.putpixel((x, y), c)
    for x in range(10, 14):
        for y in range(11, 14):
            img.putpixel((x, y), (0, 0, 0, 0))
    return img, legs


def main():
    for kind in ("humanoid", "humanoid_leggings"):
        os.makedirs(os.path.join(ROOT, "textures", "entity", "equipment", kind), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "equipment"), exist_ok=True)
    listing = []

    for design in DESIGNS:
        main_layer, legs_layer = design.paint()
        main_layer.save(os.path.join(ROOT, "textures", "entity", "equipment", "humanoid", design.id + ".png"))
        legs_layer.save(os.path.join(ROOT, "textures", "entity", "equipment", "humanoid_leggings", design.id + ".png"))
        with open(os.path.join(ROOT, "equipment", design.id + ".json"), "w") as f:
            json.dump({"layers": {"humanoid": [{"texture": "vanity:" + design.id}], "humanoid_leggings": [{"texture": "vanity:" + design.id}]}}, f, indent=2)
            f.write("\n")
        listing.append({"id": design.id, "name": design.name, "color": design.icon})

    examples = os.path.join(ROOT, "armor_folder")
    os.makedirs(examples, exist_ok=True)
    main_t, legs_t = template()
    main_t.save(os.path.join(examples, "template.png"))
    legs_t.save(os.path.join(examples, "template_leggings.png"))
    frost_a, frost_b = DESIGNS[0].paint()
    frost_a.save(os.path.join(examples, "example.png"))
    frost_b.save(os.path.join(examples, "example_leggings.png"))
    with open(os.path.join(ROOT, "custom_armors.json"), "w") as f:
        json.dump(listing, f, indent=2)
        f.write("\n")
    print("wrote", len(DESIGNS), "designs")


if __name__ == "__main__":
    main()
