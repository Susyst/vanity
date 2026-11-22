"""Paints Vanity's built-in custom armors in HD (4 texels per armor pixel) and sculpts their 3D pieces.

For each design this writes, under src/main/resources/assets/vanity/:
  textures/entity/equipment/humanoid/<id>.png            helmet, chestplate, boots (256x128 = 4x the 64x32 layout)
  textures/entity/equipment/humanoid_leggings/<id>.png   leggings
  textures/entity/equipment/humanoid[_leggings]/<id>_glow.png   parts that glow in the dark
  textures/entity/equipment/extras/<id>.png              texture for the 3D pieces (512x512 = 4x 128x128)
  armor_extras/<id>.json                                 the 3D pieces (read by ArmorExtras.java)
  equipment/<id>.json                                    armor model layers
plus custom_armors.json and the template/example for config/vanity/armors/examples/.

Run: python3 tools/make_armor_textures.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "vanity")
S = 4  # texels per armor pixel
CLEAR = (0, 0, 0, 0)
EXTRAS_SIZE = 128  # logical size of the 3D pieces' texture

# Face rectangles (x, y, w, h) on the 64x32 armor layout, in armor pixels
HEAD = {"top": (8, 0, 8, 8), "bottom": (16, 0, 8, 8), "right": (0, 8, 8, 8), "front": (8, 8, 8, 8), "left": (16, 8, 8, 8), "back": (24, 8, 8, 8)}
BODY = {"top": (20, 16, 8, 4), "bottom": (28, 16, 8, 4), "right": (16, 20, 4, 12), "front": (20, 20, 8, 12), "left": (28, 20, 4, 12), "back": (32, 20, 8, 12)}
ARM = {"top": (44, 16, 4, 4), "bottom": (48, 16, 4, 4), "right": (40, 20, 4, 12), "front": (44, 20, 4, 12), "left": (48, 20, 4, 12), "back": (52, 20, 4, 12)}
LEG = {"top": (4, 16, 4, 4), "bottom": (8, 16, 4, 4), "right": (0, 20, 4, 12), "front": (4, 20, 4, 12), "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}
SIDES = ("right", "front", "left", "back")


# ---------------------------------------------------------------------------------------------- colors

def clamp(v):
    return max(0, min(255, int(round(v))))


def rgba(c, a=255):
    return (clamp(c[0]), clamp(c[1]), clamp(c[2]), a)


def shade(c, f):
    return rgba((c[0] * f, c[1] * f, c[2] * f), c[3] if len(c) > 3 else 255)


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return rgba((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t))


class Noise:
    """Smooth value noise."""

    def __init__(self, rng, cell):
        self.rng, self.cell, self.grid = rng, cell, {}

    def at(self, gx, gy):
        if (gx, gy) not in self.grid:
            self.grid[(gx, gy)] = self.rng.random()
        return self.grid[(gx, gy)]

    def __call__(self, x, y):
        fx, fy = x / self.cell, y / self.cell
        x0, y0 = math.floor(fx), math.floor(fy)
        tx, ty = fx - x0, fy - y0
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = self.at(x0, y0) + (self.at(x0 + 1, y0) - self.at(x0, y0)) * tx
        b = self.at(x0, y0 + 1) + (self.at(x0 + 1, y0 + 1) - self.at(x0, y0 + 1)) * tx
        return a + (b - a) * ty


# ---------------------------------------------------------------------------------------------- face canvas

class Face:
    """One face of a box, drawn in armor pixels (floats allowed) at S texels per pixel. rows limits the painted rows."""

    def __init__(self, design, rect, part, name, rows=None, scale=S):
        self.d = design
        self.x0, self.y0, self.lw, self.lh = rect
        self.part, self.name = part, name
        self.r0, self.r1 = rows if rows else (0, self.lh)
        self.s = scale
        self.W, self.H = max(1, int(round(self.lw * scale))), max(1, int(round(self.lh * scale)))
        self.img = Image.new("RGBA", (self.W, self.H), CLEAR)
        self.glow = Image.new("RGBA", (self.W, self.H), CLEAR)
        self.used_glow = False

    def X(self, x):
        return x * self.s

    def Y(self, y):
        return y * self.s

    def rows_px(self):
        return int(round(self.r0 * self.s)), int(round(self.r1 * self.s))

    def inside(self, px, py):
        a, b = self.rows_px()
        return 0 <= px < self.W and a <= py < b

    def get(self, px, py):
        return self.img.getpixel((px, py))

    def put(self, px, py, c):
        if self.inside(px, py):
            self.img.putpixel((px, py), c)

    def lit(self, px, py, c, strength=1.0):
        if self.inside(px, py):
            self.img.putpixel((px, py), rgba(c))
            self.glow.putpixel((px, py), rgba(c, clamp(255 * strength)))
            self.used_glow = True

    def tint(self, px, py, f):
        if self.inside(px, py):
            c = self.get(px, py)
            if c[3] > 0:
                self.img.putpixel((px, py), shade(c, f))

    def fill(self, material):
        a, b = self.rows_px()
        for py in range(a, b):
            for px in range(self.W):
                self.img.putpixel((px, py), material(self, px, py))

    def mask(self, polygon=None, rect=None, ellipse=None):
        m = Image.new("L", (self.W, self.H), 0)
        dr = ImageDraw.Draw(m)
        if rect:
            x0, y0, x1, y1 = rect
            dr.rectangle([self.X(x0), self.Y(y0), self.X(x1) - 1, self.Y(y1) - 1], fill=255)
        if polygon:
            dr.polygon([(self.X(x), self.Y(y)) for x, y in polygon], fill=255)
        if ellipse:
            x0, y0, x1, y1 = ellipse
            dr.ellipse([self.X(x0), self.Y(y0), self.X(x1) - 1, self.Y(y1) - 1], fill=255)
        return m

    def plate(self, m, tint=1.0, light=1.28, dark=0.62, gap=0.55, color=None, material=None):
        """A raised plate shaped like mask m: optional color/material, lit top-left edge, dark bottom-right edge, and a
        dark gap just outside it."""
        mp = m.load()
        a, b = self.rows_px()
        for py in range(a, b):
            for px in range(self.W):
                if not mp[px, py]:
                    continue
                if material:
                    self.img.putpixel((px, py), material(self, px, py))
                elif color:
                    self.img.putpixel((px, py), rgba(color))
                if tint != 1.0:
                    self.tint(px, py, tint)
                up = py == 0 or not mp[px, py - 1]
                left = px == 0 or not mp[px - 1, py]
                down = py == self.H - 1 or not mp[px, py + 1]
                right = px == self.W - 1 or not mp[px + 1, py]
                if up or left:
                    self.tint(px, py, light)
                elif down or right:
                    self.tint(px, py, dark)
        if gap is not None:
            for py in range(a, b):
                for px in range(self.W):
                    if mp[px, py]:
                        continue
                    if any(0 <= px + dx < self.W and 0 <= py + dy < self.H and mp[px + dx, py + dy] for dx, dy in ((0, -1), (-1, 0))):
                        self.tint(px, py, gap)

    def _layer(self, draw_fn, glow):
        tmp = Image.new("RGBA", (self.W, self.H), CLEAR)
        draw_fn(ImageDraw.Draw(tmp))
        a, b = self.rows_px()
        src = tmp.load()
        for py in range(a, b):
            for px in range(self.W):
                c = src[px, py]
                if c[3]:
                    if glow is not None:
                        self.lit(px, py, c, glow)
                    else:
                        self.img.putpixel((px, py), c)

    def line(self, points, color, width=1, glow=None):
        self._layer(lambda dr: dr.line([(self.X(x), self.Y(y)) for x, y in points], fill=rgba(color), width=width), glow)

    def poly(self, points, color, glow=None):
        self._layer(lambda dr: dr.polygon([(self.X(x), self.Y(y)) for x, y in points], fill=rgba(color)), glow)

    def oval(self, box, color, glow=None):
        x0, y0, x1, y1 = box
        self._layer(lambda dr: dr.ellipse([self.X(x0), self.Y(y0), self.X(x1) - 1, self.Y(y1) - 1], fill=rgba(color)), glow)

    def clear(self, m):
        mp = m.load()
        for py in range(self.H):
            for px in range(self.W):
                if mp[px, py]:
                    self.img.putpixel((px, py), CLEAR)
                    self.glow.putpixel((px, py), CLEAR)

    def engrave(self, points, depth=0.55, lift=1.25):
        """A cut line: darker, with a light texel just under it."""
        tmp = Image.new("L", (self.W, self.H), 0)
        ImageDraw.Draw(tmp).line([(self.X(x), self.Y(y)) for x, y in points], fill=255, width=1)
        t = tmp.load()
        for py in range(self.H):
            for px in range(self.W):
                if t[px, py]:
                    self.tint(px, py, depth)
                    if py + 1 < self.H and not t[px, py + 1]:
                        self.tint(px, py + 1, lift)

    def rivet(self, x, y, color, r=0.42):
        cx, cy = self.X(x), self.Y(y)
        rr = r * self.s
        for py in range(int(cy - rr) - 1, int(cy + rr) + 2):
            for px in range(int(cx - rr) - 1, int(cx + rr) + 2):
                dx, dy = px + 0.5 - cx, py + 0.5 - cy
                dist = math.hypot(dx, dy)
                if dist <= rr:
                    f = 1.35 if dx + dy < -rr * 0.4 else (0.7 if dx + dy > rr * 0.5 else 1.0)
                    self.put(px, py, shade(rgba(color), f))
                elif dist <= rr + 0.9 and dx + dy > 0:
                    self.tint(px, py, 0.7)


# ---------------------------------------------------------------------------------------------- design base

class Design:
    """A full armor set. Subclasses set the palette, material, visor and details, and sculpt the 3D pieces."""
    visor = "arch"

    def __init__(self, ident, name, pal, icon, seed, glow_pulse=False):
        self.id, self.name, self.p, self.icon = ident, name, pal, icon
        self.rng = random.Random(seed)
        self.n1 = Noise(random.Random(seed * 3 + 1), 6.0)
        self.n2 = Noise(random.Random(seed * 3 + 2), 2.0)
        self.glow_pulse = glow_pulse
        self.has_glow = False

    # ---- materials: base color of a texel ----

    def material(self, f, px, py):
        a, b = f.rows_px()
        t = (py - a) / max(1, b - a - 1)
        c = mix(self.p["light"], self.p["mid"], min(1.0, t * 1.3))
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        n = (self.n1(gx, gy) - 0.5) * 0.16 + (self.n2(gx, gy) - 0.5) * 0.06
        return shade(c, 1.0 + n)

    def trim_material(self, f, px, py):
        c = self.p["trim"]
        n = (self.n2(px + 300, py) - 0.5) * 0.12
        return shade(c, 1.0 + n + (0.12 if (px + py) % 9 == 0 else 0))

    def under_material(self, f, px, py):
        """Chainmail under the plates."""
        c = shade(self.p["mid"], 0.55)
        ring = (px % 3 == 1 and py % 2 == 0) or (px % 3 == 0 and py % 2 == 1)
        return shade(c, 1.35 if ring else 0.85)

    # ---- helpers ----

    def band(self, f, y0, y1, tint=1.0, trim=False, curve=0.0):
        """A horizontal plate across the face; curve bends its bottom edge down in the middle."""
        w = f.lw
        pts = [(0, y0), (w, y0)]
        steps = 8
        for i in range(steps + 1):
            x = w - w * i / steps
            pts.append((x, y1 + curve * math.sin(math.pi * i / steps)))
        f.plate(f.mask(polygon=pts), tint=tint, material=self.trim_material if trim else None)

    # ---- pieces ----

    def helmet(self, faces):
        p = self.p
        for name, f in faces.items():
            if name == "bottom":
                continue
            f.fill(self.material)
            if name == "top":
                f.plate(f.mask(rect=(0.4, 0.4, 7.6, 7.6)), tint=1.05)
                f.plate(f.mask(rect=(3.3, 0, 4.7, 8)), material=self.trim_material)
            elif name == "front":
                f.plate(f.mask(rect=(0, 0, 8, 8)), gap=None)
                f.plate(f.mask(rect=(0, 0, 8, 1.25)), material=self.trim_material)
                for x in (1.0, 2.6, 4.0, 5.4, 7.0):
                    f.rivet(x, 0.62, p["trim_light"], 0.3)
                self.visor_front(f)
            elif name in ("right", "left"):
                f.plate(f.mask(rect=(0, 0, 8, 8)), gap=None)
                f.plate(f.mask(rect=(0, 0, 8, 1.25)), material=self.trim_material)
                f.plate(f.mask(polygon=[(0.6, 5.6), (7.4, 5.0), (7.4, 7.5), (0.6, 7.5)]), tint=0.92)
                f.plate(f.mask(ellipse=(2.6, 2.2, 5.4, 5.0)), tint=1.1)
                f.rivet(4.0, 3.6, p["trim_light"], 0.45)
            elif name == "back":
                f.plate(f.mask(rect=(0, 0, 8, 8)), gap=None)
                f.plate(f.mask(rect=(0, 0, 8, 1.25)), material=self.trim_material)
                self.band(f, 4.2, 5.6, 0.95, curve=0.3)
                self.band(f, 5.6, 7.0, 0.9, curve=0.3)
                self.band(f, 7.0, 8.0, 0.85)
        self.helmet_details(faces)

    def visor_front(self, f):
        p = self.p
        if self.visor == "arch":
            f.plate(f.mask(polygon=[(0.8, 2.0), (7.2, 2.0), (7.2, 7.4), (0.8, 7.4)]), tint=0.96)
            f.clear(f.mask(polygon=[(1.9, 6.4), (1.9, 3.6), (2.6, 2.6), (4.0, 2.2), (5.4, 2.6), (6.1, 3.6), (6.1, 6.4)]))
            f.plate(f.mask(rect=(3.55, 1.4, 4.45, 4.4)), material=self.trim_material)
        elif self.visor == "great":
            f.plate(f.mask(polygon=[(0.5, 1.6), (7.5, 1.6), (7.5, 7.6), (0.5, 7.6)]), tint=0.98)
            f.poly([(1.1, 3.0), (3.6, 3.0), (3.6, 3.65), (1.1, 3.65)], p["visor"])
            f.poly([(4.4, 3.0), (6.9, 3.0), (6.9, 3.65), (4.4, 3.65)], p["visor"])
            f.plate(f.mask(rect=(3.6, 1.4, 4.4, 7.6)), tint=1.15)
            for gx in range(3):
                for gy in range(3):
                    f.oval((5.0 + gx * 0.6, 4.6 + gy * 0.7, 5.35 + gx * 0.6, 4.95 + gy * 0.7), p["visor"])
            self.visor_glow(f)
        elif self.visor == "slit":
            f.plate(f.mask(polygon=[(0.5, 1.6), (7.5, 1.6), (7.5, 7.6), (0.5, 7.6)]), tint=0.98)
            f.poly([(0.9, 3.0), (7.1, 3.0), (6.6, 3.9), (1.4, 3.9)], p["visor"])
            f.plate(f.mask(polygon=[(1.2, 4.6), (6.8, 4.6), (5.6, 7.4), (2.4, 7.4)]), tint=0.9)
            for i in range(4):
                f.engrave([(2.6 + i * 0.95, 5.0), (2.9 + i * 0.75, 7.0)])
            self.visor_glow(f)
        elif self.visor == "screen":
            f.plate(f.mask(polygon=[(0.5, 1.6), (7.5, 1.6), (7.5, 7.6), (0.5, 7.6)]), tint=0.98)
            f.plate(f.mask(polygon=[(0.7, 2.4), (7.3, 2.4), (6.8, 4.8), (1.2, 4.8)]), color=p["visor"], light=1.6, dark=0.8)
            self.visor_glow(f)
        elif self.visor == "kabuto":
            # Open eyes under the brim, then a lacquered face mask (menpo): nose, white moustache, a mouth slit, a chin tuft
            f.clear(f.mask(polygon=[(1.2, 2.2), (6.8, 2.2), (6.8, 4.4), (1.2, 4.4)]))
            f.plate(f.mask(polygon=[(0.4, 4.4), (7.6, 4.4), (7.6, 6.4), (6.6, 8.0), (1.4, 8.0), (0.4, 6.4)]), color=p["visor"], light=1.45, dark=0.6)
            f.plate(f.mask(polygon=[(3.5, 4.3), (4.5, 4.3), (4.9, 5.7), (3.1, 5.7)]), color=shade(p["visor"], 1.45), light=1.5)
            hair = (238, 234, 228)
            f.poly([(3.9, 5.8), (2.4, 5.9), (1.4, 6.6), (2.6, 6.4), (3.9, 6.2)], hair)
            f.poly([(4.1, 5.8), (5.6, 5.9), (6.6, 6.6), (5.4, 6.4), (4.1, 6.2)], hair)
            f.line([(2.9, 6.95), (5.1, 6.95)], (14, 4, 8), 1)
            f.poly([(3.5, 7.25), (4.5, 7.25), (4.0, 8.0)], hair)

    def visor_glow(self, f):
        g = self.p["glow"]
        if self.visor in ("great", "slit"):
            f.oval((1.9, 3.0, 3.0, 3.8), g, glow=1.0)
            f.oval((5.0, 3.0, 6.1, 3.8), g, glow=1.0)
        elif self.visor == "screen":
            for x in range(4, 27, 2):
                f.lit(x, 13, g, 0.9)
            f.line([(1.4, 3.1), (6.6, 3.1)], g, 1, glow=1.0)

    def helmet_details(self, faces):
        pass

    def chestplate(self, body, arm):
        p = self.p
        for name, f in body.items():
            f.fill(self.material)
            if name == "bottom":
                f.plate(f.mask(rect=(0, 0, 8, 4)), tint=0.55, gap=None)
                continue
            if name == "top":
                f.plate(f.mask(rect=(0, 0, 8, 4)), gap=None)
                f.plate(f.mask(rect=(2.2, 0.6, 5.8, 3.4)), material=self.trim_material)
                continue
            if name == "front":
                f.plate(f.mask(rect=(0, 0, 8, 1.2)), material=self.trim_material)
                # Breastplate: two curved chest plates over banded abdomen plates
                f.plate(f.mask(polygon=[(0.3, 1.2), (3.9, 1.2), (3.9, 5.4), (2.6, 6.1), (0.3, 5.6)]), tint=1.06)
                f.plate(f.mask(polygon=[(4.1, 1.2), (7.7, 1.2), (7.7, 5.6), (5.4, 6.1), (4.1, 5.4)]), tint=1.06)
                for i, (y0, y1) in enumerate(((6.1, 7.3), (7.3, 8.5), (8.5, 9.7))):
                    self.band(f, y0, y1, 0.98 - 0.04 * i, curve=0.35)
                f.plate(f.mask(rect=(0, 9.9, 8, 12)), material=self.trim_material, tint=0.95)
                for x in (0.9, 7.1):
                    f.rivet(x, 1.9, p["trim_light"], 0.35)
            elif name == "back":
                f.plate(f.mask(rect=(0, 0, 8, 1.2)), material=self.trim_material)
                f.plate(f.mask(polygon=[(0.3, 1.2), (3.8, 1.2), (3.8, 6.2), (0.3, 5.4)]))
                f.plate(f.mask(polygon=[(4.2, 1.2), (7.7, 1.2), (7.7, 5.4), (4.2, 6.2)]))
                f.plate(f.mask(rect=(3.6, 1.2, 4.4, 9.8)), tint=1.18)
                for y0, y1 in ((6.6, 7.9), (7.9, 9.2)):
                    self.band(f, y0, y1, 0.95, curve=0.25)
                f.plate(f.mask(rect=(0, 9.9, 8, 12)), material=self.trim_material, tint=0.9)
            else:
                f.plate(f.mask(rect=(0, 0, 4, 1.2)), material=self.trim_material)
                for y0, y1 in ((1.2, 4.0), (4.0, 6.6), (6.6, 9.0), (9.0, 10.0)):
                    self.band(f, y0, y1, 1.0 if y0 < 4 else 0.94)
                f.plate(f.mask(rect=(0, 9.9, 4, 12)), material=self.trim_material, tint=0.9)
        for name, f in arm.items():
            f.fill(self.material)
            if name == "top":
                f.plate(f.mask(rect=(0, 0, 4, 4)), tint=1.08)
                f.plate(f.mask(rect=(0, 0, 4, 0.8)), material=self.trim_material)
                continue
            if name == "bottom":
                f.plate(f.mask(rect=(0, 0, 4, 4)), tint=0.5, gap=None)
                continue
            for i, (y0, y1) in enumerate(((0, 1.5), (1.5, 2.8), (2.8, 4.0))):
                self.band(f, y0, y1, 1.08 - 0.05 * i, trim=(i == 0), curve=0.35)
            f.plate(f.mask(rect=(0, 4.0, 4, 6.4)), material=self.under_material, light=1.0, dark=1.0)
            if name in ("right", "left"):
                f.plate(f.mask(ellipse=(0.6, 5.6, 3.4, 8.0)), tint=1.1)
                f.rivet(2.0, 6.8, p["trim_light"], 0.38)
            f.plate(f.mask(polygon=[(0, 7.4), (4, 7.2), (4, 11.0), (0, 11.0)]))
            f.engrave([(0.2, 8.6), (3.8, 8.5)])
            f.engrave([(0.2, 9.9), (3.8, 9.8)])
            f.plate(f.mask(rect=(0, 11.0, 4, 12)), material=self.trim_material, tint=0.95)
        self.chest_details(body, arm)

    def chest_details(self, body, arm):
        pass

    def boots(self, leg):
        for name, f in leg.items():
            if name == "top":
                continue
            f.fill(self.material)
            if name == "bottom":
                f.plate(f.mask(rect=(0, 0, 4, 4)), tint=0.45, gap=None)
                continue
            f.plate(f.mask(rect=(0, 6, 4, 6.9)), material=self.trim_material)
            f.plate(f.mask(rect=(0, 6.9, 4, 9.3)), tint=1.02)
            for y0, y1 in ((9.3, 10.3), (10.3, 11.2)):
                self.band(f, y0, y1, 0.95, curve=0.2)
            f.plate(f.mask(rect=(0, 11.2, 4, 12)), tint=0.55, gap=None)
            if name == "front":
                f.plate(f.mask(ellipse=(0.4, 8.4, 3.6, 11.6)), tint=1.12)
        self.boot_details(leg)

    def boot_details(self, leg):
        pass

    def leggings(self, body, leg):
        p = self.p
        for name, f in body.items():
            if name in ("top", "bottom"):
                continue
            f.fill(self.material)
            f.plate(f.mask(rect=(0, 0, f.lw, 1.4)), color=p["belt"], light=1.3, dark=0.7)
            f.engrave([(0, 0.7), (f.lw, 0.7)], depth=0.8)
            if name == "front":
                f.plate(f.mask(rect=(3.2, 0.1, 4.8, 1.35)), material=self.trim_material, light=1.5)
                f.plate(f.mask(polygon=[(0.2, 1.4), (3.8, 1.4), (3.8, 5.2), (2.0, 5.9), (0.2, 5.2)]), tint=1.04)
                f.plate(f.mask(polygon=[(4.2, 1.4), (7.8, 1.4), (7.8, 5.2), (6.0, 5.9), (4.2, 5.2)]), tint=1.04)
            else:
                f.plate(f.mask(polygon=[(0.2, 1.4), (f.lw - 0.2, 1.4), (f.lw - 0.2, 5.4), (0.2, 5.4)]))
        for name, f in leg.items():
            if name in ("top", "bottom"):
                continue
            f.fill(self.material)
            for i, (y0, y1) in enumerate(((0, 1.6), (1.6, 3.1), (3.1, 4.6))):
                self.band(f, y0, y1, 1.04 - 0.03 * i, curve=0.2)
            f.plate(f.mask(ellipse=(0.2, 4.4, 3.8, 7.2)), tint=1.12)
            if name == "front":
                f.rivet(2.0, 5.8, p["trim_light"], 0.42)
            f.plate(f.mask(rect=(0, 7.0, 4, 9)), tint=0.96)
            f.engrave([(0.3, 8.0), (3.7, 8.0)])
        self.legging_details(body, leg)

    def legging_details(self, body, leg):
        pass

    # ---- 3D pieces ----

    def extras(self):
        return []

    def paint_extra(self, f, paint):
        """Paints one face of a 3D piece."""
        p = self.p
        if paint == "glow":
            g = p["glow"]
            f.fill(lambda ff, px, py: mix(shade(g, 1.15), shade(g, 0.8), py / max(1, ff.H - 1)))
            return
        base = {"dark": p["dark"], "trim": p["trim"], "light": p["light"]}.get(paint)
        if base is None:
            f.fill(self.material)
        else:
            f.fill(lambda ff, px, py: shade(base, 1.0 + (self.n2(px + 900, py + ff.y0 * 4) - 0.5) * 0.12 + 0.18 * (1 - py / max(1, ff.H - 1))))
        if f.W >= 4 and f.H >= 4:
            for px in range(f.W):
                f.tint(px, 0, 1.3)
                f.tint(px, f.H - 1, 0.65)
            for py in range(f.H):
                f.tint(0, py, 1.15)
                f.tint(f.W - 1, py, 0.75)

    # ---- output ----

    def paint(self):
        out = {k: Image.new("RGBA", (64 * S, 32 * S), CLEAR) for k in ("a", "b", "ga", "gb")}
        groups = []

        def faces(table, part, rows, target):
            fs = {n: Face(self, r, part, n, None if n in ("top", "bottom") else rows) for n, r in table.items()}
            groups.append((fs, target))
            return fs

        self.helmet(faces(HEAD, "head", None, "a"))
        self.chestplate(faces(BODY, "body", None, "a"), faces(ARM, "arm", None, "a"))
        self.boots(faces(LEG, "boots", (6, 12), "a"))
        self.leggings(faces(BODY, "belt", (0, 6), "b"), faces(LEG, "pants", (0, 9), "b"))

        for fs, target in groups:
            for f in fs.values():
                a, b = f.rows_px()
                if b <= a:
                    continue
                out[target].alpha_composite(f.img.crop((0, a, f.W, b)), (f.x0 * S, f.y0 * S + a))
                if f.used_glow:
                    self.has_glow = True
                    out["g" + target].alpha_composite(f.glow.crop((0, a, f.W, b)), (f.x0 * S, f.y0 * S + a))
        return out["a"], out["b"], out["ga"], out["gb"]


# ---------------------------------------------------------------------------------------------- 3D sculpting helpers

def euler_from_axes(xa, ya, za):
    """Euler angles (degrees, ModelPart order R = Rz * Ry * Rx) for the rotation taking local x/y/z to xa/ya/za."""
    b = math.asin(max(-1.0, min(1.0, -xa[2])))
    a = math.atan2(ya[2], za[2])
    c = math.atan2(xa[1], xa[0])
    return [round(math.degrees(a), 3), round(math.degrees(b), 3), round(math.degrees(c), 3)]


def norm(v):
    length = math.sqrt(sum(x * x for x in v)) or 1.0
    return [x / length for x in v]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def orient(d, roll_hint=(0.0, 0.0, -1.0)):
    """Rotation turning a box that points up (local -y) to point along d."""
    yaxis = norm([-d[0], -d[1], -d[2]])
    c = cross(yaxis, roll_hint)
    if sum(x * x for x in c) < 1e-6:
        c = cross(yaxis, (1.0, 0.0, 0.0))
    xaxis = norm(c)
    zaxis = cross(xaxis, yaxis)
    return euler_from_axes(xaxis, yaxis, zaxis)


def segment(bone, pivot, d, length, w, t, paint, name, hint=(0.0, 0.0, -1.0), **extra):
    """A box from pivot towards d: length long, w wide and t thick. hint picks which way the width faces."""
    piece = {"name": name, "bone": bone, "box": [-w / 2, -length, -t / 2, w, length, t], "pivot": list(pivot), "rot": orient(norm(d), hint), "paint": paint}
    piece.update(extra)
    return piece


def chain(bone, start, dirs, lengths, widths, paint, name, tip_paint=None, overlap=0.35, **extra):
    """A curved, tapering piece (horn, plume, antler) made of segments following dirs."""
    pieces = []
    pos = list(start)
    for i, (d, length, w) in enumerate(zip(dirs, lengths, widths)):
        d = norm(d)
        last = i == len(dirs) - 1
        pieces.append(segment(bone, pos, d, length + (0 if last else overlap), w, w, tip_paint if (last and tip_paint) else paint, "%s%d" % (name, i), **extra))
        pos = [pos[k] + d[k] * length for k in range(3)]
    return pieces


def ring(bone, center, radius, count, thickness, paint, name, tilt=0.0, **extra):
    """A ring of count segments around center, tilted forward by tilt degrees."""
    pieces = []
    seg_len = 2 * radius * math.tan(math.pi / count) + 0.3
    ct, st = math.cos(math.radians(tilt)), math.sin(math.radians(tilt))

    def tilted(v):
        return [v[0], v[1] * ct - v[2] * st, v[1] * st + v[2] * ct]

    for i in range(count):
        ang = 2 * math.pi * (i + 0.5) / count
        local = tilted([math.cos(ang) * radius, 0.0, math.sin(ang) * radius])
        pos = [center[k] + local[k] for k in range(3)]
        xaxis = norm(tilted([-math.sin(ang), 0.0, math.cos(ang)]))
        yaxis = norm(tilted([0.0, 1.0, 0.0]))
        zaxis = cross(xaxis, yaxis)
        p = {"name": "%s%d" % (name, i), "bone": bone, "box": [-seg_len / 2, -thickness / 2, -thickness / 2, seg_len, thickness, thickness],
             "pivot": pos, "rot": euler_from_axes(xaxis, yaxis, zaxis), "paint": paint}
        p.update(extra)
        pieces.append(p)
    return pieces


def plate_piece(bone, pivot, box, rot, paint, name, **extra):
    p = {"name": name, "bone": bone, "box": box, "pivot": pivot, "rot": rot, "paint": paint}
    p.update(extra)
    return p


def pauldron(bone, name, paint, top_paint, levels=3, width=6.4, depth=7.2, drop=1.1, tilt=-14, y=-3.6, x=-4.6):
    """Layered shoulder plates, each smaller and lower, tilted down over the arm (mirrored onto the other arm)."""
    pieces = []
    for i in range(levels):
        w = width - i * 0.7
        dp = depth - i * 0.6
        pieces.append(plate_piece(bone, [0, 0, 0], [x + i * 0.25, y + i * drop, -dp / 2, w, 1.3, dp], [0, 0, tilt - i * 4], top_paint if i == 0 else paint,
                                  "%s%d" % (name, i), mirror=True))
    return pieces


# Armor surfaces, in each bone's own space (the armor is the body part grown by 1, or 0.5 for leggings)
ARM_C = (-1.0, 0.0)  # right arm center (x, z); its armor spans x -4..2, z -3..3, y -3..11; outside is -x
LEG_C = (0.0, 0.0)   # right leg center; boots span x -3..3, z -3..3 down to y 13 (the ground); leggings x/z -2.5..2.5; outside is -x


def flare_ring(bone, center, half_x, half_z, hinge_y, height, flare, paint, name, sides=("front", "back", "outer", "inner"), up=True, t=0.5, **extra):
    """Plates around a limb or the head, hinged at hinge_y and rising (up) or hanging (down) for height, their free edges
    leaning out by flare degrees: flared skirts, a samurai neck guard."""
    pieces = []
    cx, cz = center
    f = math.radians(flare)
    vy = -math.cos(f) if up else math.cos(f)
    normals = {"front": (0, -1), "back": (0, 1), "outer": (-1, 0), "inner": (1, 0)}
    for side in sides:
        nx, nz = normals[side]
        d = [nx * math.sin(f), vy, nz * math.sin(f)]
        if nz:
            pos, width, hint = [cx, hinge_y, cz + nz * (half_z + t / 2)], 2 * half_x + t, (0.0, 0.0, -1.0)
        else:
            pos, width, hint = [cx + nx * (half_x + t / 2), hinge_y, cz], 2 * half_z + t, (1.0, 0.0, 0.0)
        pieces.append(segment(bone, pos, d, height, width, t, paint, "%s_%s" % (name, side), hint=hint, **extra))
    return pieces


def keel(bone, y0, height, z, angle, paint, name, half=4.8, t=0.6, **extra):
    """Two plates meeting in a ridge down the middle and sinking into the armor at their outer edges, so a flat front
    becomes a pointed breastplate."""
    return [plate_piece(bone, [0, y0, z - t], [0, 0, -t / 2, half, height, t], [0, -angle, 0], paint, name, mirror=True, **extra)]


def knee_cop(bone, y, z, paint, name, fan_paint=None, **extra):
    """A domed knee guard: a front plate, plates sloping back above and below it, and a diamond fan on the outside."""
    pieces = [plate_piece(bone, [0, y, z - 0.4], [-1.9, -1.5, -0.4, 3.8, 3.0, 0.8], [0, 0, 0], paint, name, mirror=True, **extra),
              segment(bone, [0, y - 1.35, z - 0.35], [0, -1, 0.55], 1.7, 3.2, 0.6, paint, name + "_up", mirror=True, **extra),
              segment(bone, [0, y + 1.35, z - 0.35], [0, 1, 0.5], 1.5, 3.2, 0.6, paint, name + "_down", mirror=True, **extra)]
    if fan_paint:
        pieces.append(plate_piece(bone, [-2.85, y, z + 1.3], [-0.25, -1.3, -1.3, 0.5, 2.6, 2.6], [45, 0, 0], fan_paint, name + "_fan", mirror=True, **extra))
    return pieces


def sabaton(bone, paint, name, style="round", **extra):
    """A foot for the boot: a toe cap reaching forward on the ground and a plate sloping down over the instep.
    style: round (broad toe), pointed (long gothic toe), claw (three claws)."""
    extra.setdefault("slot", "feet")
    wide = 2.7 if style == "round" else 2.5
    pieces = [plate_piece(bone, [0, 13.0, -3.0], [-wide, -1.5, -2.0, 2 * wide, 1.5, 2.0], [0, 0, 0], paint, name, mirror=True, **extra),
              segment(bone, [0, 10.1, -3.15], [0, 1.15, -1.9], 2.3, 2 * wide - 0.2, 0.5, paint, name + "_instep", mirror=True, **extra)]
    if style == "pointed":
        pieces.append(plate_piece(bone, [0, 13.0, -5.0], [-1.6, -1.1, -1.7, 3.2, 1.1, 1.7], [0, 0, 0], paint, name + "_tip", mirror=True, **extra))
        pieces.append(plate_piece(bone, [0, 13.0, -6.7], [-0.7, -0.7, -1.4, 1.4, 0.7, 1.4], [0, 0, 0], paint, name + "_point", mirror=True, **extra))
    return pieces


def helix(bone, center, radius, y0, y1, turns, paint, name, width=0.55, phase=0.0, steps=16, **extra):
    """A vine (or cable) winding around a limb from y0 to y1, hugging its square sides radius out from the middle."""
    pts = []
    for i in range(steps + 1):
        a = phase + 2 * math.pi * turns * i / steps
        r = radius / max(abs(math.cos(a)), abs(math.sin(a)))
        pts.append([center[0] + math.cos(a) * r, y0 + (y1 - y0) * i / steps, center[1] + math.sin(a) * r])
    pieces = []
    for i in range(steps):
        a, b = pts[i], pts[i + 1]
        d = [b[k] - a[k] for k in range(3)]
        length = math.sqrt(sum(v * v for v in d))
        pieces.append(segment(bone, a, d, length + 0.3, width, width, paint, "%s%d" % (name, i), **extra))
    return pieces, pts


def spin_ring(bone, center, radius, count, height, t, paint, name, speed=1.6, **extra):
    """A flat ring of count plates around a vertical axis through center that keeps turning (speed radians a second)."""
    seg_len = 2 * radius * math.tan(math.pi / count) + 0.25
    pieces = []
    for i in range(count):
        p = {"name": "%s%d" % (name, i), "bone": bone, "box": [-seg_len / 2, -height / 2, -t / 2, seg_len, height, t], "pivot": list(center),
             "rot": [0, 0, 0], "paint": paint, "orbit": [radius, speed], "phase": 2 * math.pi * i / count, "face_orbit": True}
        p.update(extra)
        pieces.append(p)
    return pieces


# ---------------------------------------------------------------------------------------------- designs

class Obsidian(Design):
    """Heavy knight: great helm, curved horns, layered spiked pauldrons, knee spikes, a tattered cape."""
    visor = "great"

    def material(self, f, px, py):
        c = super().material(f, px, py)
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        if (gx + gy * 2) % 23 < 2:
            c = mix(c, (96, 70, 140), 0.45)
        return mix(c, (60, 30, 96), self.n1(gx + 77, gy) * 0.25)

    def chest_details(self, body, arm):
        f = body["front"]
        g = self.p["glow"]
        f.poly([(4.0, 2.2), (5.1, 3.6), (4.0, 5.0), (2.9, 3.6)], shade(g, 0.8), glow=1.0)
        f.poly([(4.0, 2.7), (4.6, 3.6), (4.0, 4.5), (3.4, 3.6)], (255, 230, 255), glow=1.0)
        for pts in (((3.0, 3.6), (1.8, 4.4), (1.2, 5.6)), ((5.0, 3.6), (6.2, 4.6), (6.6, 5.7)), ((4.0, 5.0), (4.2, 6.6))):
            f.line(pts, shade(g, 0.85), 1, glow=0.8)
        for face in arm.values():
            if face.name in SIDES:
                face.line([(0, 2.8), (4, 2.8)], shade(g, 0.8), 1, glow=0.6)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name == "front":
                face.oval((1.5, 5.3, 2.5, 6.3), self.p["glow"], glow=0.9)

    def extras(self):
        horn = chain("head", [-4.4, -6.6, -0.6], [[-1, -0.22, 0], [-0.82, -0.6, -0.05], [-0.38, -0.95, -0.2], [0.05, -1, -0.38], [0.32, -0.9, -0.45]],
                     [2.4, 2.1, 2.0, 1.8, 1.4], [2.4, 2.0, 1.6, 1.15, 0.7], "dark", "horn", tip_paint="glow", mirror=True)
        crest = [plate_piece("head", [0, -9.2, 0], [-0.4, -1.4, -4.8, 0.8, 1.4, 9.6], [0, 0, 0], "dark", "ridge")]
        shoulders = pauldron("right_arm", "pauldron", "main", "trim", levels=3)
        spikes = []
        for i, z in enumerate((-2.4, 0.0, 2.4)):
            spikes += chain("right_arm", [-3.6, -3.4, z], [[-0.55, -1, 0], [-0.4, -1, 0]], [1.6, 1.2], [1.4, 0.7], "dark", "spike%d_" % i, mirror=True)
        knees = chain("right_leg", [0, 5.2, -2.6], [[0, -0.35, -1], [0, -0.6, -1]], [1.4, 1.1], [1.6, 0.8], "dark", "knee_spike", slot="legs", mirror=True)
        knees += knee_cop("right_leg", 5.4, -2.5, "main", "knee", fan_paint="dark", slot="legs")
        cape = [plate_piece("body", [0, 0.4, 2.7], [-4.6, 0, 0, 9.2, 13.5, 0.6], [9, 0, 0], "cloth", "cape", sway=4.0)]
        for i, x in enumerate((-4.2, -1.8, 0.6, 3.0)):
            cape.append(plate_piece("body", [0, 0.4, 2.7], [x, 13.4, 0, 1.4 + (i % 2) * 0.4, 1.6 + (i % 3) * 0.6, 0.6], [9, 0, 0], "cloth", "cape_tatter%d" % i, sway=4.0))
        # A brow and a ridge standing off the great helm's face
        helm = [plate_piece("head", [0, -5.75, -5.0], [-4.7, -0.45, -0.8, 9.4, 0.8, 0.8], [0, 0, 0], "dark", "brow"),
                plate_piece("head", [0, -8.9, -5.0], [-0.45, 0, -0.9, 0.9, 9.5, 0.9], [0, 0, 0], "main", "face_ridge")]
        # Spikes along the forearms and off each elbow
        arms = []
        for i, z in enumerate((-1.4, 1.4)):
            arms += chain("right_arm", [-3.8, 8.0, z], [[-1, -0.5, 0], [-0.7, -1, 0]], [1.4, 0.9], [0.9, 0.45], "dark", "forearm_spike%d_" % i, tip_paint="glow", mirror=True)
        arms += chain("right_arm", [-1.0, 4.4, 3.2], [[0, 0.3, 1], [0, 0.65, 1]], [1.4, 1.0], [1.3, 0.6], "dark", "elbow", tip_paint="glow", mirror=True)
        # A pointed belly plate, long pointed sabatons and spurs
        body = keel("body", 6.0, 4.0, -3.0, 16, "main", "plackart")
        feet = sabaton("right_leg", "main", "sabaton", style="pointed")
        feet.append(segment("right_leg", [0, 12.0, 2.9], [0, 0.15, 1], 1.8, 0.4, 0.4, "trim", "spur", slot="feet", mirror=True))
        feet.append(plate_piece("right_leg", [0, 12.25, 4.7], [-0.15, -0.6, -0.6, 0.3, 1.2, 1.2], [45, 0, 0], "glow", "spur_rowel", slot="feet", mirror=True))
        return horn + crest + helm + shoulders + spikes + arms + body + knees + feet + cape

    def paint_extra(self, f, paint):
        if paint == "cloth":
            f.fill(lambda ff, px, py: shade(mix((40, 14, 60), (18, 6, 28), py / max(1, ff.H - 1)), 1.0 + (self.n2(px + 40, py) - 0.5) * 0.25))
            for py in range(0, f.H, 6):
                for px in range(f.W):
                    f.tint(px, py, 0.85)
            if f.H > 20:
                for px in range(f.W):
                    f.lit(px, 2, (170, 100, 255), 0.6)
            return
        super().paint_extra(f, paint)


class Frost(Design):
    """Ice armor: faceted plates, frost, a crystal crown and crystal clusters, icicles."""
    visor = "arch"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        c = super().material(f, px, py)
        facet = (math.floor((gx * 0.9 + gy * 0.5) / 7) + math.floor((gx * 0.4 - gy * 0.8) / 9)) % 3
        c = shade(c, (1.12, 1.0, 0.9)[facet])
        if self.n2(gx + 5, gy + 9) > 0.86:
            c = mix(c, (245, 252, 255), 0.7)
        return c

    def chest_details(self, body, arm):
        body["front"].poly([(4.0, 2.0), (4.9, 3.4), (4.0, 4.8), (3.1, 3.4)], (210, 248, 255), glow=1.0)
        for face in list(body.values()) + list(arm.values()):
            if face.name in SIDES:
                for _ in range(3):
                    x, y = self.rng.uniform(0.4, face.lw - 0.4), self.rng.uniform(1.4, face.lh - 2)
                    face.line([(x, y), (x + self.rng.uniform(-0.8, 0.8), y + self.rng.uniform(0.6, 1.6))], (236, 250, 255), 1, glow=0.5)

    def helmet_details(self, faces):
        faces["front"].line([(1.0, 1.0), (7.0, 1.0)], (215, 248, 255), 1, glow=0.7)

    def extras(self):
        pieces = []
        crown = [(0.0, -9.0, -1.2, [0.0, -1, -0.15], 4.6, 1.6), (-2.2, -8.9, -1.6, [-0.35, -1, -0.15], 3.4, 1.3), (2.2, -8.9, -1.6, [0.35, -1, -0.15], 3.4, 1.3),
                 (-3.6, -8.6, -0.4, [-0.6, -1, 0.0], 2.4, 1.0), (3.6, -8.6, -0.4, [0.6, -1, 0.0], 2.4, 1.0), (-1.2, -8.8, 1.4, [-0.15, -1, 0.4], 2.6, 1.1),
                 (1.3, -8.8, 1.6, [0.2, -1, 0.45], 2.2, 1.0)]
        for i, (x, y, z, d, length, w) in enumerate(crown):
            pieces += chain("head", [x, y, z], [d, d], [length * 0.6, length * 0.4], [w, w * 0.55], "crystal", "crown%d_" % i, glow=True)
        cluster = [(-3.0, -3.2, -1.0, [-0.5, -1, -0.2], 3.2, 1.4), (-2.0, -3.4, 0.8, [-0.2, -1, 0.3], 2.4, 1.1), (-3.8, -2.6, 1.2, [-0.9, -0.8, 0.3], 2.0, 0.9),
                   (-1.4, -3.3, -1.6, [-0.1, -1, -0.4], 1.6, 0.8)]
        for i, (x, y, z, d, length, w) in enumerate(cluster):
            pieces += chain("right_arm", [x, y, z], [d, d], [length * 0.6, length * 0.4], [w, w * 0.5], "crystal", "shoulder%d_" % i, glow=True, mirror=True)
        # Ice breaking out of the forearms
        for i, z in enumerate((-1.6, 1.2)):
            pieces += chain("right_arm", [-3.8, 8.2, z], [[-0.8, -1, 0.15 * z], [-0.45, -1, 0.15 * z]], [1.5, 0.9], [0.9, 0.45], "crystal", "forearm_shard%d_" % i,
                            glow=True, mirror=True)
        # A faceted crystal heart standing out of the chest, and shards growing up out of the back
        pieces.append(plate_piece("body", [0, 3.0, -3.3], [-1.3, -1.3, -0.6, 2.6, 2.6, 1.2], [0, 0, 45], "crystal", "heart", glow=True))
        for i, (dx, dy) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
            pieces.append(segment("body", [dx * 0.8, 3.0 + dy * 0.8, -3.5], [dx, dy, -0.5], 1.2, 0.5, 0.5, "crystal", "heart_shard%d" % i, glow=True))
        for i, (x, length, w) in enumerate(((-2.2, 4.4, 1.4), (-0.8, 3.2, 1.0))):
            pieces += chain("body", [x, 2.6 + i * 2.4, 3.0], [[-0.3, -1, 0.75], [-0.15, -1, 0.95]], [length * 0.6, length * 0.4], [w, w * 0.45], "crystal",
                            "back_shard%d_" % i, glow=True, mirror=True)
        for i, x in enumerate((-3.0, -1.4, 0.4, 2.2, 3.4)):
            pieces.append(segment("body", [x, 11.8, -2.6], [0, 1, -0.05], 1.2 + (i % 3) * 0.5, 0.7, 0.7, "crystal", "icicle%d" % i, glow=True))
        # Domed knees with a shard, and armored feet
        pieces += knee_cop("right_leg", 5.6, -2.5, "main", "knee", fan_paint="crystal", slot="legs")
        pieces += chain("right_leg", [0, 4.6, -3.4], [[0, -0.7, -1], [0, -1, -0.6]], [1.2, 0.8], [0.9, 0.45], "crystal", "knee_shard", glow=True, slot="legs", mirror=True)
        pieces += chain("right_leg", [-2.4, 9.4, -1.2], [[-0.8, -1, -0.4]], [1.5], [0.8], "crystal", "boot", glow=True, slot="feet", mirror=True)
        pieces += sabaton("right_leg", "main", "sabaton")
        return pieces

    def paint_extra(self, f, paint):
        if paint == "crystal":
            def ice(ff, px, py):
                c = mix((240, 253, 255), (96, 186, 238), py / max(1, ff.H - 1))
                if px == 0 or (px + py) % 5 == 0:
                    c = mix(c, (255, 255, 255), 0.45)
                return c
            f.fill(ice)
            return
        super().paint_extra(f, paint)


class Ember(Design):
    """Charred plates with glowing lava cracks, swept flame spikes, molten shoulder horns, a magma core."""
    visor = "slit"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        c = super().material(f, px, py)
        c = shade(c, 0.85 + self.n2(gx * 1.7, gy * 1.7) * 0.3)
        heat = self.n1(gx * 0.7 + 11, gy * 0.7)
        if heat > 0.72:
            c = mix(c, (120, 40, 14), (heat - 0.72) * 2.5)
        return c

    def cracks(self, f, n):
        for _ in range(n):
            x, y = self.rng.uniform(0.3, f.lw - 0.3), self.rng.uniform(max(f.r0, 0.5), max(f.r0 + 0.6, f.r1 - 0.5))
            pts = [(x, y)]
            for _ in range(self.rng.randint(3, 6)):
                x += self.rng.uniform(-0.9, 0.9)
                y += self.rng.uniform(0.2, 1.0)
                pts.append((x, y))
            f.line(pts, (255, 110, 26), 2, glow=0.9)
            f.line(pts, (255, 222, 120), 1, glow=1.0)

    def helmet_details(self, faces):
        for name in ("right", "left", "back", "top"):
            self.cracks(faces[name], 2)
        self.cracks(faces["front"], 1)

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name != "bottom":
                self.cracks(face, max(1, int(face.lw * face.lh / 14)))
        f = body["front"]
        f.oval((2.9, 2.2, 5.1, 4.4), (255, 140, 40), glow=1.0)
        f.oval((3.4, 2.7, 4.6, 3.9), (255, 236, 150), glow=1.0)

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.cracks(face, 1)

    def legging_details(self, body, leg):
        for face in list(body.values()) + list(leg.values()):
            if face.name in SIDES:
                self.cracks(face, 1)

    def extras(self):
        pieces = []
        for i, (z, tilt) in enumerate(((-2.4, 0.15), (0.0, 0.35), (2.4, 0.6))):
            pieces += chain("head", [-3.0 + i * 0.4, -8.6, z], [[-0.45, -1, tilt], [-0.35, -0.9, tilt + 0.45], [-0.2, -0.7, tilt + 0.9]], [1.8, 1.4, 1.1],
                            [1.5, 1.1, 0.6], "dark", "flame%d_" % i, tip_paint="glow", mirror=True)
        for i, (z, length) in enumerate(((-1.8, 2.6), (0.2, 3.4), (2.0, 2.2))):
            pieces += chain("right_arm", [-3.4, -3.4, z], [[-0.7, -1, 0], [-0.35, -1, 0.1]], [length * 0.6, length * 0.4], [1.4, 0.7], "dark", "spike%d_" % i,
                            tip_paint="glow", mirror=True)
        pieces += pauldron("right_arm", "pauldron", "main", "dark", levels=2, width=6.0, depth=6.8)
        pieces.append(plate_piece("body", [0, 3.4, -2.9], [-1.4, -1.4, -0.6, 2.8, 2.8, 0.6], [0, 0, 45], "glow", "core"))
        for i, x in enumerate((-3.8, -0.9, 1.9)):
            pieces.append(plate_piece("body", [0, 10.6, -2.7], [x, 0, -0.3, 2.6, 3.4, 0.6], [-10, 0, 0], "main", "tasset%d" % i, slot="legs"))
        # Flame fins swept back along the arms
        for i in range(3):
            pieces += chain("right_arm", [-3.9, 4.4 + i * 1.9, 0.4], [[-0.55, -0.35, 1], [-0.25, -0.75, 1]], [1.6 - i * 0.15, 1.1 - i * 0.1], [1.2, 0.6], "dark",
                            "fin%d_" % i, tip_paint="glow", mirror=True)
        # A row of spines down the back
        for i in range(4):
            pieces += chain("body", [0, 1.2 + i * 2.7, 3.1], [[0, -0.45, 1], [0, -0.9, 0.7]], [1.9 - i * 0.25, 1.3 - i * 0.15], [1.5 - i * 0.15, 0.7], "dark",
                            "dorsal%d_" % i, tip_paint="glow")
        # Spiked knees and clawed feet
        pieces += knee_cop("right_leg", 5.5, -2.5, "dark", "knee", slot="legs")
        pieces += chain("right_leg", [0, 4.8, -3.4], [[0, -0.6, -1], [0, -1, -0.5]], [1.3, 0.9], [1.0, 0.5], "dark", "knee_spike", tip_paint="glow", slot="legs", mirror=True)
        pieces += sabaton("right_leg", "main", "sabaton", style="claw")
        for i, x in enumerate((-1.6, 0.0, 1.6)):
            pieces += chain("right_leg", [x, 12.3, -4.8], [[0, 0.1, -1], [0, 0.7, -0.7]], [1.1, 0.8], [0.8, 0.4], "dark", "claw%d_" % i, tip_paint="glow",
                            slot="feet", mirror=True)
        return pieces


class Verdant(Design):
    """Living wood and moss: bark plates, vines, branching antlers with leaves, mushrooms, a leaf skirt."""
    visor = "arch"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        moss = self.n1(gx * 0.8, gy * 0.8)
        bark = shade(self.p["bark"], 0.85 + 0.3 * ((gx + int(self.n2(gx, gy) * 6)) % 5 == 0))
        return mix(bark, shade(self.p["mid"], 0.9 + self.n2(gx * 2, gy * 2) * 0.3), min(1.0, max(0.0, (moss - 0.32) * 3)))

    def trim_material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        return shade(self.p["bark"], 0.8 + 0.35 * (gx % 4 == 0) + (self.n2(gx, gy) - 0.5) * 0.2)

    def vines(self, f):
        phase = self.rng.random() * 6
        pts = [(f.lw / 2 + (f.lw / 2 - 0.5) * math.sin(y * 0.8 + phase), y) for y in [f.r0 + i * 0.25 for i in range(int((f.r1 - f.r0) * 4))]]
        f.line(pts, (88, 150, 56), 2)
        for i in range(0, len(pts), 6):
            x, y = pts[i]
            f.poly([(x, y), (x + 0.9, y - 0.4), (x + 1.2, y + 0.3)], (130, 200, 80))

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name in SIDES and face.lh >= 8:
                self.vines(face)
        for x, y in ((2.0, 3.0), (6.0, 4.2), (3.0, 8.6), (5.6, 9.0)):
            body["front"].oval((x - 0.3, y - 0.3, x + 0.3, y + 0.3), (190, 255, 150), glow=0.65)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.vines(face)

    def extras(self):
        pieces = []
        pieces += chain("head", [-3.2, -8.6, -0.6], [[-0.5, -1, 0.1], [-0.8, -0.8, 0.2], [-0.4, -1, 0.3]], [1.8, 1.6, 1.4], [1.1, 0.9, 0.7], "bark", "antler", mirror=True)
        pieces += chain("head", [-4.3, -10.4, -0.4], [[-0.1, -1, -0.5], [0.15, -1, -0.6]], [1.2, 1.0], [0.8, 0.55], "bark", "fork", mirror=True)
        for i, (pos, d) in enumerate((([-6.0, -12.8, 0.9], [-0.6, -0.5, 0.5]), ([-4.2, -12.6, -1.6], [0.0, -0.7, -0.7]), ([-2.6, -9.8, -2.4], [-0.3, -0.3, -1]))):
            pieces.append(segment("head", pos, d, 1.8, 1.6, 0.4, "leaf", "antler_leaf%d" % i, mirror=True))
        for i in range(7):
            a = -math.pi * 0.85 + i * math.pi * 1.7 / 6
            pieces.append(segment("head", [math.sin(a) * 4.4, -8.8, -math.cos(a) * 4.4], [math.sin(a) * 0.7, -1, -math.cos(a) * 0.7], 2.2, 2.0, 0.4, "leaf", "crown%d" % i))
        pieces += pauldron("right_arm", "moss", "main", "bark", levels=2, width=6.0, depth=6.6)
        for i, (x, z, s) in enumerate(((-1.6, 1.6, 1.0), (-2.8, -1.4, 0.7))):
            pieces.append(segment("right_arm", [x, -3.8, z], [0, -1, 0], 1.4 * s, 0.6 * s, 0.6 * s, "light", "stem%d" % i, mirror=True))
            pieces.append(plate_piece("right_arm", [x, -3.8 - 1.4 * s, z], [-1.4 * s, -0.8 * s, -1.4 * s, 2.8 * s, 0.8 * s, 2.8 * s], [0, 0, 0], "glow", "cap%d" % i, mirror=True))
        for i, z in enumerate((-2.0, 0.6, 2.6)):
            pieces.append(segment("right_arm", [-4.4, -2.6, z], [-0.8, -0.3, z * 0.15], 2.2, 1.8, 0.4, "leaf", "shoulder_leaf%d" % i, mirror=True))
        for i in range(6):
            a = -math.pi * 0.9 + i * math.pi * 1.8 / 5
            pieces.append(segment("body", [math.sin(a) * 4.0, 11.6, -math.cos(a) * 2.4], [math.sin(a) * 0.4, 1, -math.cos(a) * 0.4], 2.4, 2.0, 0.4, "leaf", "skirt%d" % i, slot="legs"))
        # Vines winding up the arms and legs with leaves on them
        vine, pts = helix("right_arm", ARM_C, 3.35, 0.6, 7.8, 1.15, "vine", "vine", phase=0.6, mirror=True)
        pieces += vine
        for i in (5, 11):
            x, y, z = pts[i]
            pieces.append(segment("right_arm", [x, y, z], [x - ARM_C[0], -0.5, z - ARM_C[1]], 1.9, 1.5, 0.35, "leaf", "vine_leaf%d" % i, mirror=True))
        vine, pts = helix("right_leg", LEG_C, 2.85, 0.4, 6.2, 0.8, "vine", "leg_vine", phase=2.2, steps=12, slot="legs", mirror=True)
        pieces += vine
        x, y, z = pts[7]
        pieces.append(segment("right_leg", [x, y, z], [x, -0.5, z], 1.8, 1.4, 0.35, "leaf", "leg_leaf", slot="legs", mirror=True))
        # A glowing mushroom on each knee
        pieces.append(segment("right_leg", [-1.6, 5.4, -2.6], [-0.4, -1, -0.5], 1.3, 0.5, 0.5, "light", "knee_stem", slot="legs", mirror=True))
        pieces.append(plate_piece("right_leg", [-2.05, 4.25, -3.2], [-1.0, -0.6, -1.0, 2.0, 0.6, 2.0], [0, 0, 0], "glow", "knee_cap", slot="legs", mirror=True))
        # Roots spreading over the ground from the boots
        for i, a in enumerate((140, 195, 250, 295)):
            r = math.radians(a)
            dx, dz = math.cos(r), math.sin(r)
            pieces += chain("right_leg", [dx * 2.6, 12.3, dz * 2.6], [[dx, 0.35, dz], [dx, 0.12, dz]], [1.7, 1.3], [1.0, 0.55], "bark", "root%d_" % i, slot="feet",
                            mirror=True)
        return pieces

    def paint_extra(self, f, paint):
        if paint == "leaf":
            def leaf(ff, px, py):
                c = mix((150, 214, 92), (64, 128, 46), py / max(1, ff.H - 1))
                if abs(px - ff.W / 2) < 0.8:
                    c = shade(c, 0.8)
                return c
            f.fill(leaf)
            if f.W > 3:
                for py in range(f.H):
                    for px in range(f.W):
                        edge = abs(px + 0.5 - f.W / 2) / (f.W / 2)
                        if edge > 1.0 - 0.6 * math.sin(math.pi * (py + 0.5) / f.H):
                            f.put(px, py, CLEAR)
            return
        if paint == "bark":
            f.fill(lambda ff, px, py: shade(self.p["bark"], 0.8 + 0.35 * (px % 3 == 0) + (self.n2(px + 70, py * 0.5) - 0.5) * 0.3))
            return
        if paint == "vine":
            f.fill(lambda ff, px, py: shade(mix((106, 168, 64), (58, 104, 40), px / max(1, ff.W - 1)), 0.9 + 0.2 * ((py // 2) % 2)))
            return
        super().paint_extra(f, paint)


class Royal(Design):
    """Polished silver with gold filigree, a tall curved plume, layered gold-rimmed pauldrons, a royal cape."""
    visor = "arch"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        return shade(super().material(f, px, py), 1.0 + 0.1 * math.sin((gx - gy * 0.8) * 0.18))

    def filigree(self, f, x0, y0, x1, y1):
        steps = 16
        pts = [(x0 + (x1 - x0) * i / steps, (y0 + y1) / 2 + (y1 - y0) / 2 * math.sin(i * math.pi / 4)) for i in range(steps + 1)]
        f.line(pts, self.p["trim"], 1)
        for i in range(0, steps + 1, 4):
            x, y = pts[i]
            f.oval((x - 0.22, y - 0.22, x + 0.22, y + 0.22), self.p["trim_light"])

    def chest_details(self, body, arm):
        f = body["front"]
        g, gl = self.p["trim"], self.p["trim_light"]
        f.plate(f.mask(polygon=[(2.6, 2.2), (5.4, 2.2), (5.4, 4.4), (4.0, 5.6), (2.6, 4.4)]), color=(36, 60, 150), light=1.5, dark=0.6)
        f.poly([(2.9, 1.8), (3.4, 1.2), (3.8, 1.7), (4.2, 1.1), (4.6, 1.7), (5.0, 1.2), (5.2, 1.9), (2.9, 1.9)], gl)
        f.line([(2.6, 2.2), (5.4, 2.2), (5.4, 4.4), (4.0, 5.6), (2.6, 4.4), (2.6, 2.2)], g, 1)
        f.oval((3.6, 3.0, 4.4, 3.8), gl)
        self.filigree(f, 0.4, 10.4, 7.6, 11.4)
        self.filigree(body["back"], 0.4, 10.4, 7.6, 11.4)
        for face in arm.values():
            if face.name in SIDES:
                self.filigree(face, 0.2, 11.1, 3.8, 11.9)

    def helmet_details(self, faces):
        for name in ("right", "left"):
            self.filigree(faces[name], 0.4, 6.3, 7.6, 7.2)

    def extras(self):
        pieces = chain("head", [0, -9.0, -2.6], [[0, -1, -0.5], [0, -1, 0.1], [0, -0.6, 0.8], [0, 0.1, 1], [0, 0.6, 0.8]], [1.8, 1.8, 1.8, 1.7, 1.6],
                       [1.6, 1.8, 1.8, 1.6, 1.2], "plume", "plume")
        pieces.append(plate_piece("head", [0, -9.1, 0], [-0.6, -1.2, -4.0, 1.2, 1.2, 8.0], [0, 0, 0], "trim", "crest_base"))
        pieces += pauldron("right_arm", "pauldron", "main", "trim", levels=3, width=6.6, depth=7.4)
        for i in range(3):
            pieces.append(plate_piece("right_arm", [0, 0, 0], [-4.75 + i * 0.25, -3.6 + i * 1.1 + 1.15, -3.75 + i * 0.3, 6.6 - i * 0.7, 0.4, 7.5 - i * 0.6], [0, 0, -14 - i * 4],
                                      "trim", "pauldron_rim%d" % i, mirror=True))
        pieces.append(plate_piece("body", [0, 0.2, 2.8], [-4.8, 0, 0, 9.6, 14.5, 0.5], [7, 0, 0], "cape", "cape", sway=3.0))
        pieces.append(plate_piece("body", [0, 0.2, 2.8], [-4.9, -0.4, -0.2, 9.8, 1.2, 0.9], [7, 0, 0], "trim", "cape_clasp", sway=3.0))
        for i, x in enumerate((-3.6, -1.2, 1.2)):
            pieces.append(plate_piece("body", [0, 10.4, -2.7], [x, 0, -0.3, 2.4, 3.2, 0.5], [-8, 0, 0], "main", "tasset%d" % i, slot="legs"))
        # A ridged breastplate with the crest as a real shield on it
        pieces += keel("body", 1.0, 8.8, -3.0, 12, "main", "breastplate")
        pieces.append(plate_piece("body", [0, 1.3, -3.9], [-0.35, 0, -0.3, 0.7, 8.4, 0.4], [0, 0, 0], "trim", "breast_ridge"))
        pieces.append(plate_piece("body", [0, 1.8, -4.3], [-1.6, 0, -0.3, 3.2, 2.4, 0.5], [0, 0, 0], "shield", "shield"))
        pieces.append(plate_piece("body", [0, 4.2, -4.2], [-1.13, -1.13, -0.3, 2.26, 2.26, 0.5], [0, 0, 45], "shield", "shield_point"))
        # Elbow discs with fans
        pieces.append(plate_piece("right_arm", [-1.0, 4.6, 3.35], [-1.1, -1.1, -0.3, 2.2, 2.2, 0.6], [0, 0, 45], "trim", "couter", mirror=True))
        pieces.append(plate_piece("right_arm", [-4.3, 4.6, 0.8], [-0.25, -1.3, -1.3, 0.5, 2.6, 2.6], [45, 0, 0], "main", "couter_fan", mirror=True))
        # Knee cops with gold fans, broad sabatons with a gold toe
        pieces += knee_cop("right_leg", 5.5, -2.5, "main", "knee", fan_paint="trim", slot="legs")
        pieces += sabaton("right_leg", "main", "sabaton")
        pieces.append(plate_piece("right_leg", [0, 13.0, -5.0], [-2.8, -1.6, -0.4, 5.6, 1.6, 0.45], [0, 0, 0], "trim", "sabaton_rim", slot="feet", mirror=True))
        return pieces

    def paint_extra(self, f, paint):
        if paint == "shield":
            f.fill(lambda ff, px, py: shade(mix((52, 82, 186), (24, 40, 118), py / max(1, ff.H - 1)), 1.0 + (self.n2(px, py + 400) - 0.5) * 0.1))
            gold = rgba(self.p["trim"])
            if f.W >= 6 and f.H >= 6:
                for px in range(f.W):
                    f.put(px, 0, gold)
                    f.put(px, f.H - 1, gold)
                for py in range(f.H):
                    f.put(0, py, gold)
                    f.put(f.W - 1, py, gold)
            if f.W >= 10 and f.H >= 8:
                cx, cy = f.W // 2, f.H // 2
                for i in range(-2, 3):
                    f.put(cx + i, cy, rgba(self.p["trim_light"]))
                    f.put(cx, cy + i, rgba(self.p["trim_light"]))
            return
        if paint == "plume":
            f.fill(lambda ff, px, py: shade(mix((236, 72, 82), (150, 16, 32), py / max(1, ff.H - 1)), 0.85 + 0.3 * ((px + py) % 3 == 0)))
            return
        if paint == "cape":
            f.fill(lambda ff, px, py: shade(mix((54, 70, 170), (24, 30, 96), py / max(1, ff.H - 1)), 1.0 + (self.n2(px, py) - 0.5) * 0.15))
            if f.H > 20 and f.W > 20:
                for px in range(f.W):
                    for py in (f.H - 6, f.H - 5):
                        f.put(px, py, rgba(self.p["trim"]))
                for py in range(f.H):
                    for px in (2, 3, f.W - 4, f.W - 3):
                        f.put(px, py, rgba(self.p["trim"]))
            return
        super().paint_extra(f, paint)


class Void(Design):
    """Starfield armor: nebula plates, a glowing halo, orbiting orbs, spectral wings."""
    visor = "slit"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        c = mix(self.p["mid"], (60, 22, 110), max(0.0, self.n1(gx * 0.6 + 50, gy * 0.6) - 0.35) * 1.6)
        c = mix(c, (20, 40, 110), max(0.0, self.n1(gx * 0.5, gy * 0.5 + 99) - 0.6) * 1.5)
        return shade(c, 0.95 + self.n2(gx, gy) * 0.1)

    def trim_material(self, f, px, py):
        return shade(self.p["trim"], 0.9 + (self.n2(px, py) - 0.5) * 0.2)

    def stars(self, f, n):
        for _ in range(n):
            x, y = self.rng.uniform(0.2, f.lw - 0.2), self.rng.uniform(f.r0 + 0.2, f.r1 - 0.2)
            c = self.rng.choice([(255, 255, 255), (220, 196, 255), (170, 140, 255), (150, 200, 255)])
            if self.rng.random() < 0.2:
                f.line([(x - 0.4, y), (x + 0.4, y)], c, 1, glow=0.9)
                f.line([(x, y - 0.4), (x, y + 0.4)], c, 1, glow=0.9)
            else:
                f.lit(int(f.X(x)), int(f.Y(y)), c, self.rng.choice([0.6, 0.85, 1.0]))

    def helmet_details(self, faces):
        for name in ("top", "right", "left", "back"):
            self.stars(faces[name], 12)

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name != "bottom":
                self.stars(face, int(face.lw * face.lh * 0.8))

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.stars(face, 6)

    def legging_details(self, body, leg):
        for face in list(body.values()) + list(leg.values()):
            if face.name in SIDES:
                self.stars(face, 8)

    def extras(self):
        pieces = ring("head", [0, -12.8, 0], 4.6, 10, 0.8, "glow", "halo", tilt=20, bob=0.6)
        # A thin ring turning around the shoulders, with orbs riding it
        pieces += spin_ring("body", [0, -2.6, 0], 9.0, 18, 0.35, 0.3, "glow", "orbit_ring", speed=0.8)
        for i in range(2):
            pieces.append(plate_piece("body", [0, -2.6, 0], [-0.9, -0.9, -0.9, 1.8, 1.8, 1.8], [45, 45, 0], "glow", "orb%d" % i, orbit=[9.0, 1.4], phase=i * math.pi))
            pieces.append(plate_piece("body", [0, -2.6, 0], [-0.5, -0.5, -0.5, 1.0, 1.0, 1.0], [45, 45, 0], "glow", "orb_small%d" % i, orbit=[9.0, 1.4], phase=i * math.pi + 0.5))
        for i in range(5):
            a = math.radians(-20 - i * 18)
            pieces.append(segment("body", [-1.6, 2.4, 3.0], [-math.cos(a), math.sin(a), 0.35], 6.5 - i * 0.7, 1.4, 0.3, "wing", "wing%d" % i, glow=True, mirror=True,
                                  sway=3.0, phase=i * 0.3))
        # Shards drifting over the shoulders and in front of the knees
        for i, (x, y, z, s) in enumerate(((-2.6, -5.4, -0.4, 1.0), (-0.6, -6.4, 0.8, 0.6), (-4.0, -4.6, 1.2, 0.5))):
            pieces.append(plate_piece("right_arm", [x, y, z], [-0.8 * s, -1.4 * s, -0.8 * s, 1.6 * s, 2.8 * s, 1.6 * s], [0, 45, 20], "glow", "shard%d" % i, bob=0.6,
                                      phase=i * 1.3, mirror=True))
        pieces.append(plate_piece("right_leg", [0, 5.4, -4.6], [-0.7, -1.2, -0.7, 1.4, 2.4, 1.4], [0, 45, 0], "glow", "knee_shard", bob=0.4, slot="legs", mirror=True))
        # Pointed boots
        pieces += sabaton("right_leg", "main", "sabaton", style="pointed")
        return pieces

    def paint_extra(self, f, paint):
        if paint == "wing":
            f.fill(lambda ff, px, py: mix((210, 180, 255), (120, 60, 220), px / max(1, ff.W - 1)))
            return
        super().paint_extra(f, paint)


class Sakura(Design):
    """Samurai armor: lacquered lamellar plates laced in red, a kabuto with a crescent crest, sode shoulders, a banner."""
    visor = "kabuto"

    def material(self, f, px, py):
        gx, gy = px + f.x0 * 4, py + f.y0 * 4
        return shade(super().material(f, px, py), 1.0 + 0.08 * math.sin((gx + gy) * 0.25))

    def lames(self, f, y0, y1, rows):
        """Rows of lacquered scales with lacing between them."""
        h = (y1 - y0) / rows
        lace = self.p["lace"]
        for r in range(rows):
            top = y0 + r * h
            f.plate(f.mask(rect=(0, top, f.lw, top + h - 0.25)), tint=1.0 - 0.02 * r, light=1.25, dark=0.75)
            for i in range(int(f.lw * 2)):
                x = (i + 0.5) / 2
                f.line([(x - 0.15, top + h - 0.25), (x + 0.15, top + h + 0.1)], lace, 1)

    def chestplate(self, body, arm):
        for name, f in body.items():
            f.fill(self.material)
            if name in ("top", "bottom"):
                f.plate(f.mask(rect=(0, 0, 8, 4)), tint=0.95 if name == "top" else 0.5, gap=None)
                continue
            f.plate(f.mask(rect=(0, 0, f.lw, 1.2)), material=self.trim_material)
            self.lames(f, 1.2, 11.0, 6)
            f.plate(f.mask(rect=(0, 11.0, f.lw, 12)), color=self.p["lace"], light=1.3)
        for name, f in arm.items():
            f.fill(self.material)
            if name in ("top", "bottom"):
                f.plate(f.mask(rect=(0, 0, 4, 4)), tint=1.0 if name == "top" else 0.5, gap=None)
                continue
            f.plate(f.mask(rect=(0, 0, 4, 0.9)), material=self.trim_material)
            self.lames(f, 0.9, 5.0, 3)
            f.plate(f.mask(rect=(0, 5.0, 4, 7.4)), material=self.under_material, light=1, dark=1)
            self.lames(f, 7.4, 11.2, 3)
            f.plate(f.mask(rect=(0, 11.2, 4, 12)), material=self.trim_material)
        self.chest_details(body, arm)

    def chest_details(self, body, arm):
        f = body["front"]
        f.line([(0.6, 10.4), (2.0, 8.4), (3.6, 7.6), (5.2, 5.6), (6.8, 4.8)], (92, 54, 46), 2)
        for x, y in ((2.0, 8.2), (3.8, 7.3), (5.2, 5.4), (6.6, 4.6), (4.6, 6.6)):
            for dx, dy in ((0, -0.35), (0.35, 0), (0, 0.35), (-0.35, 0)):
                f.oval((x + dx - 0.25, y + dy - 0.25, x + dx + 0.25, y + dy + 0.25), (255, 238, 246))
            f.oval((x - 0.15, y - 0.15, x + 0.15, y + 0.15), (255, 200, 90))

    def helmet_details(self, faces):
        for name in ("right", "left", "back"):
            f = faces[name]
            f.fill(self.material)
            f.plate(f.mask(rect=(0, 0, 8, 1.3)), material=self.trim_material)
            self.lames(f, 1.3, 8.0, 4)

    def leggings(self, body, leg):
        for name, f in body.items():
            if name in ("top", "bottom"):
                continue
            f.fill(self.material)
            f.plate(f.mask(rect=(0, 0, f.lw, 1.4)), color=self.p["belt"], light=1.3)
            self.lames(f, 1.4, 6.0, 3)
        for name, f in leg.items():
            if name in ("top", "bottom"):
                continue
            f.fill(self.material)
            self.lames(f, 0, 9.0, 5)

    def extras(self):
        pieces = chain("head", [-0.6, -7.6, -5.0], [[-0.4, -1, -0.15], [-0.75, -0.7, -0.1], [-1, -0.25, 0]], [1.8, 1.6, 1.4], [0.9, 0.8, 0.6], "trim", "crest", mirror=True)
        pieces.append(plate_piece("head", [0, -7.6, -5.25], [-0.9, -0.9, -0.3, 1.8, 1.8, 0.6], [0, 0, 45], "trim", "crest_disc"))
        # The neck guard (shikoro): laced plates flaring down and out round the back and sides, with its front corners
        # turned back into wings beside the face (fukigaeshi)
        half, hinge = 5.0, -3.6
        for i, (height, flare) in enumerate(((1.9, 22), (1.9, 34), (1.6, 46))):
            sides = ("back", "outer", "inner") if i < 2 else ("back",)
            pieces += flare_ring("head", (0, 0), half, half, hinge, height, flare, "lacquer", "shikoro%d" % i, sides=sides, up=False)
            half += height * math.sin(math.radians(flare))
            hinge += height * math.cos(math.radians(flare)) - 0.12
        pieces.append(plate_piece("head", [-5.25, -3.9, -4.9], [-0.3, -0.2, 0, 0.6, 2.8, 2.2], [0, -62, -14], "flap", "fukigaeshi", mirror=True))
        # Shin splints (suneate), knee guards, plates over the backs of the hands (tekko)
        for i, (x, ry) in enumerate(((-1.6, 22), (0.0, 0), (1.6, -22))):
            pieces.append(plate_piece("right_leg", [x, 6.6, -3.25], [-0.6, 0, -0.3, 1.2, 5.6, 0.6], [0, ry, 0], "lacquer", "suneate%d" % i, slot="feet", mirror=True))
        pieces += knee_cop("right_leg", 5.4, -2.5, "lacquer", "knee", slot="legs")
        pieces.append(plate_piece("right_arm", [-4.35, 7.4, 0], [-0.3, 0, -2.2, 0.6, 3.4, 4.4], [0, 0, -6], "lacquer", "tekko", mirror=True))
        for i in range(3):
            pieces.append(plate_piece("right_arm", [-4.2, -2.6, 0], [-0.5 - i * 0.05, i * 1.9, -3.6, 0.7, 2.1, 7.2], [0, 0, 10 + i * 2], "lacquer", "sode%d" % i, mirror=True))
        for i, (x, z, ry) in enumerate(((-2.4, -3.0, 0), (2.4, -3.0, 0), (-4.4, 0, 90), (4.4, 0, 90), (0, 3.0, 0))):
            rx = -8 if z < 0 else (8 if z > 0 else 0)
            rz = (8 if x > 0 else -8) if ry else 0
            pieces.append(plate_piece("body", [x, 10.8, z], [-2.2, 0, -0.3, 4.4, 3.6, 0.6], [rx, ry, rz], "lacquer", "kusazuri%d" % i, slot="legs"))
        pieces.append(segment("body", [3.0, 6.0, 3.0], [0, -1, 0.05], 16.0, 0.5, 0.5, "dark", "banner_pole"))
        pieces.append(plate_piece("body", [3.0, -6.0, 3.3], [0.25, -3.6, -0.1, 4.2, 6.4, 0.2], [0, 0, 0], "banner", "banner", sway=2.5))
        return pieces

    def paint_extra(self, f, paint):
        if paint == "lacquer":
            f.fill(self.material)
            for py in range(f.H):
                if py % 8 in (6, 7):
                    for px in range(f.W):
                        f.put(px, py, rgba(self.p["lace"]) if (px // 2) % 2 == 0 else shade(rgba(self.p["lace"]), 1.3))
            return
        if paint == "flap":
            f.fill(lambda ff, px, py: shade(mix((120, 24, 44), (70, 12, 26), py / max(1, ff.H - 1)), 1.0 + (self.n2(px + 600, py) - 0.5) * 0.1))
            gold = rgba(self.p["trim"])
            if f.W >= 6 and f.H >= 6:
                for px in range(f.W):
                    f.put(px, 0, gold)
                    f.put(px, f.H - 1, gold)
                for py in range(f.H):
                    f.put(0, py, gold)
                    f.put(f.W - 1, py, gold)
                cx, cy = f.W // 2, f.H // 2
                for py in range(f.H):
                    for px in range(f.W):
                        if math.hypot(px + 0.5 - cx, py + 0.5 - cy) < 1.6:
                            f.put(px, py, rgba(self.p["trim_light"]))
            return
        if paint == "banner":
            f.fill(lambda ff, px, py: rgba((248, 240, 236)))
            if f.W > 10 and f.H > 10:
                cx, cy, r = f.W / 2, f.H / 2, min(f.W, f.H) * 0.28
                for py in range(f.H):
                    for px in range(f.W):
                        d = math.hypot(px - cx, py - cy)
                        if d < r:
                            f.put(px, py, rgba((226, 120, 160)))
                        elif d < r + 1.2:
                            f.put(px, py, rgba((170, 40, 80)))
            return
        super().paint_extra(f, paint)


class Circuit(Design):
    """Sleek tech armor: gunmetal panels, glowing traces and screens, antenna, back power core, jet boots."""
    visor = "screen"

    def material(self, f, px, py):
        gy = py + f.y0 * 4
        return shade(super().material(f, px, py), 0.96 + 0.08 * (gy % 3 == 0))

    def panel(self, f, x0, y0, x1, y1):
        f.plate(f.mask(rect=(x0, y0, x1, y1)), tint=1.04, light=1.35, dark=0.55)
        f.rivet(x0 + 0.35, y0 + 0.35, self.p["trim_light"], 0.18)
        f.rivet(x1 - 0.35, y1 - 0.35, self.p["trim_light"], 0.18)

    def traces(self, f, n):
        g = self.p["glow"]
        for _ in range(n):
            x, y = round(self.rng.uniform(0.5, f.lw - 0.5) * 2) / 2, round(self.rng.uniform(f.r0 + 0.5, f.r1 - 0.5) * 2) / 2
            pts = [(x, y)]
            for _ in range(self.rng.randint(2, 4)):
                if self.rng.random() < 0.5:
                    x = min(f.lw - 0.3, max(0.3, x + self.rng.choice((-1, 1)) * self.rng.uniform(0.8, 2.2)))
                else:
                    y = min(f.r1 - 0.3, max(f.r0 + 0.3, y + self.rng.uniform(0.8, 2.0)))
                pts.append((x, y))
            f.line(pts, g, 1, glow=0.85)
            ex, ey = pts[-1]
            f.oval((ex - 0.28, ey - 0.28, ex + 0.28, ey + 0.28), (210, 252, 255), glow=1.0)

    def helmet_details(self, faces):
        for name in ("right", "left", "back", "top"):
            self.traces(faces[name], 2)

    def chest_details(self, body, arm):
        f = body["front"]
        self.panel(f, 1.0, 1.6, 7.0, 5.6)
        f.plate(f.mask(ellipse=(2.8, 2.2, 5.2, 4.8)), color=(16, 40, 50), light=1.6, dark=0.5)
        f.oval((3.3, 2.7, 4.7, 4.3), self.p["glow"], glow=1.0)
        f.oval((3.7, 3.1, 4.3, 3.9), (220, 255, 255), glow=1.0)
        for face in list(body.values()) + list(arm.values()):
            if face.name in SIDES and face is not f:
                self.traces(face, max(1, int(face.lw * face.lh / 18)))
        self.traces(f, 2)

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                face.line([(0.4, 7.6), (3.6, 7.6)], self.p["glow"], 1, glow=0.9)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.traces(face, 1)

    def extras(self):
        pieces = chain("head", [-4.6, -7.4, 1.4], [[-0.1, -1, 0.05], [-0.05, -1, 0.1]], [3.0, 2.6], [0.8, 0.5], "dark", "antenna")
        pieces.append(plate_piece("head", [-4.9, -13.2, 1.8], [-0.7, -0.7, -0.7, 1.4, 1.4, 1.4], [0, 0, 0], "glow", "antenna_tip", bob=0.3))
        pieces.append(plate_piece("head", [-5.0, -4.6, 0], [-1.0, -1.6, -1.8, 1.0, 3.2, 3.6], [0, 0, 0], "main", "ear", mirror=True))
        pieces.append(plate_piece("head", [-6.05, -4.6, 0], [0, -0.9, -1.0, 0.3, 1.8, 2.0], [0, 0, 0], "glow", "ear_light", mirror=True))
        pieces.append(plate_piece("head", [0, -5.6, -5.0], [-4.6, -1.0, -0.6, 9.2, 2.6, 0.8], [8, 0, 0], "screen", "visor"))
        pieces.append(plate_piece("body", [0, 1.4, 3.0], [-2.8, 0, 0, 5.6, 7.0, 2.4], [0, 0, 0], "main", "core"))
        for i in range(3):
            pieces.append(plate_piece("body", [0, 2.2 + i * 2.0, 5.3], [-1.8, 0, 0, 3.6, 1.4, 0.4], [0, 0, 0], "glow", "cell%d" % i))
        pieces.append(segment("body", [-2.4, 1.6, 4.0], [-0.6, -1, -0.2], 2.6, 0.6, 0.6, "dark", "cable", mirror=True))
        pieces += pauldron("right_arm", "shoulder", "main", "dark", levels=2, width=6.0, depth=6.4)
        pieces.append(plate_piece("right_arm", [0, 0, 0], [-4.7, -2.6, -2.2, 0.4, 1.4, 4.4], [0, 0, -14], "glow", "strip", mirror=True))
        pieces.append(plate_piece("right_arm", [-3.3, 8.0, 0], [-0.4, -1.5, -1.8, 0.6, 3.0, 3.6], [0, 0, 0], "main", "bracer", mirror=True))
        pieces.append(plate_piece("right_arm", [-3.75, 8.0, 0], [-0.2, -1.0, -1.3, 0.25, 2.0, 2.6], [0, 0, 0], "glow", "screen", mirror=True))
        pieces.append(plate_piece("right_leg", [0, 10.6, 2.6], [-1.4, -1.6, 0, 2.8, 2.8, 1.2], [0, 0, 0], "main", "jet", slot="feet", mirror=True))
        pieces.append(plate_piece("right_leg", [0, 11.6, 3.9], [-0.9, -0.9, 0, 1.8, 1.8, 0.3], [0, 0, 0], "glow", "jet_flame", slot="feet", mirror=True))
        # A drone circling over the head (comes with the helmet)
        drone = dict(orbit=[9.5, 1.1], face_orbit=True, slot="head", bob=0.5)
        pieces.append(plate_piece("body", [0, -11.0, 0], [-1.3, -0.8, -1.1, 2.6, 1.6, 2.2], [0, 0, 0], "main", "drone", **drone))
        pieces.append(plate_piece("body", [0, -11.0, 0], [-0.55, -0.35, -1.35, 1.1, 0.7, 0.3], [0, 0, 0], "glow", "drone_eye", **drone))
        pieces.append(plate_piece("body", [0, -11.0, 0], [-2.3, -1.15, -0.35, 4.6, 0.35, 0.7], [0, 0, 0], "dark", "drone_rotor", **drone))
        # A cannon on the right shoulder only
        pieces.append(plate_piece("right_arm", [-2.2, -3.0, 0.6], [-1.3, -1.4, -1.5, 2.6, 1.4, 3.0], [0, 0, 0], "main", "cannon_mount"))
        pieces.append(segment("right_arm", [-2.2, -3.9, -0.4], [0, 0, -1], 4.6, 1.1, 1.1, "dark", "cannon"))
        pieces.append(plate_piece("right_arm", [-2.2, -3.9, -5.0], [-0.45, -0.45, -0.2, 0.9, 0.9, 0.25], [0, 0, 0], "glow", "cannon_muzzle"))
        # Knee pads with a light strip, plated feet
        pieces += knee_cop("right_leg", 5.5, -2.5, "main", "knee", slot="legs")
        pieces.append(plate_piece("right_leg", [0, 5.5, -3.35], [-1.2, -0.25, -0.1, 2.4, 0.5, 0.15], [0, 0, 0], "glow", "knee_light", slot="legs", mirror=True))
        pieces += sabaton("right_leg", "main", "sabaton")
        return pieces

    def paint_extra(self, f, paint):
        if paint == "screen":
            f.fill(lambda ff, px, py: rgba((10, 30, 40)))
            for py in range(1, f.H, 3):
                for px in range(f.W):
                    f.lit(px, py, self.p["glow"], 0.55)
            return
        super().paint_extra(f, paint)


def pal(light, mid, dark, trim, trim_light, glow, visor=None, belt=None, **extra):
    p = {"light": light, "mid": mid, "dark": dark, "trim": trim, "trim_light": trim_light, "glow": glow, "visor": visor or dark, "belt": belt or trim}
    p.update(extra)
    return p


DESIGNS = [
    Frost("frostforged", "Frostforged", pal((190, 232, 252), (110, 182, 226), (34, 78, 134), (70, 130, 190), (230, 248, 255), (160, 236, 255)), 0x7AC8EB, 11),
    Obsidian("obsidian_knight", "Obsidian Knight", pal((58, 42, 74), (30, 22, 42), (10, 6, 16), (56, 36, 84), (196, 160, 255), (196, 120, 255), visor=(4, 2, 8)), 0x3A2A52, 12, glow_pulse=True),
    Ember("ember", "Ember", pal((76, 62, 58), (46, 38, 36), (18, 12, 10), (120, 52, 24), (255, 186, 90), (255, 140, 40), visor=(10, 4, 2)), 0xC0501A, 13, glow_pulse=True),
    Verdant("verdant", "Verdant", pal((112, 172, 84), (66, 124, 58), (42, 66, 28), (96, 64, 36), (190, 230, 120), (190, 255, 150), belt=(110, 74, 40), bark=(104, 72, 42)), 0x3E7A3A, 14),
    Royal("royal_guard", "Royal Guard", pal((250, 250, 248), (204, 208, 214), (110, 114, 126), (214, 168, 52), (255, 234, 150), (255, 230, 150), belt=(130, 30, 40)), 0xE8E6DE, 15),
    Void("void_walker", "Void Walker", pal((28, 20, 52), (14, 10, 28), (4, 2, 10), (90, 50, 170), (210, 180, 255), (186, 120, 255), visor=(2, 0, 6)), 0x2A1446, 16, glow_pulse=True),
    Sakura("sakura", "Sakura", pal((252, 212, 228), (236, 164, 196), (150, 70, 104), (214, 170, 60), (255, 236, 160), (255, 220, 236), visor=(60, 20, 30), belt=(150, 30, 52), lace=(170, 28, 54)), 0xF2A7C3, 17),
    Circuit("circuit", "Circuit", pal((86, 94, 106), (48, 54, 62), (20, 24, 30), (70, 78, 90), (150, 160, 170), (64, 228, 255), visor=(8, 22, 30)), 0x2C8FA8, 18),
]


# ---------------------------------------------------------------------------------------------- packing and output

def box_faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def pack_extras(design):
    """Places every 3D piece's box on the texture, paints it, and returns the JSON parts and the image."""
    img = Image.new("RGBA", (EXTRAS_SIZE * S, EXTRAS_SIZE * S), CLEAR)
    parts = []
    x = y = row_h = 0
    for piece in design.extras():
        w, h, d = (max(0.25, v) for v in piece["box"][3:6])
        need_w, need_h = int(math.ceil(2 * (w + d))) + 1, int(math.ceil(h + d)) + 1
        if x + need_w > EXTRAS_SIZE:
            x, y, row_h = 0, y + row_h, 0
        if y + need_h > EXTRAS_SIZE:
            raise SystemExit(design.id + ": 3D pieces don't fit on the texture")
        for face_name, (fx, fy, fw, fh) in box_faces(x, y, w, h, d).items():
            f = Face(design, (fx, fy, fw, fh), "extra", face_name)
            design.paint_extra(f, piece.get("paint", "main"))
            img.alpha_composite(f.img, (int(round(fx * S)), int(round(fy * S))))
        glow = piece.get("glow", piece.get("paint") == "glow")
        part = {"name": piece["name"], "bone": piece["bone"], "tex": [x, y], "box": [round(v, 3) for v in piece["box"]],
                "pivot": [round(v, 3) for v in piece.get("pivot", [0, 0, 0])], "rot": [round(v, 3) for v in piece.get("rot", [0, 0, 0])], "glow": bool(glow),
                "spin": piece.get("spin", 0), "bob": piece.get("bob", 0), "phase": round(piece.get("phase", 0), 3), "sway": piece.get("sway", 0),
                "orbit": [round(v, 3) for v in piece.get("orbit", [0, 0])]}
        if "slot" in piece:
            part["slot"] = piece["slot"]
        if piece.get("face_orbit"):
            part["face_orbit"] = True
        parts.append(part)
        if piece.get("mirror"):
            # Mirror across x: the left arm and leg are the right ones flipped in their own space, as is the other half of
            # a piece on the head or body
            mx, my, mz, mw, mh, md = part["box"]
            px, py, pz = part["pivot"]
            rx, ry, rz = part["rot"]
            mirror_bone = {"right_arm": "left_arm", "right_leg": "left_leg"}.get(piece["bone"], piece["bone"])
            # Ring plates turning around an axis keep their place in the ring; the rest swing the other way
            phase = part["phase"] if piece.get("face_orbit") else round(part["phase"] + 1.7, 3)
            parts.append(dict(part, name=part["name"] + "_m", bone=mirror_bone, box=[round(-(mx + mw), 3), my, mz, mw, mh, md], pivot=[round(-px, 3), py, pz],
                              rot=[rx, round(-ry, 3), round(-rz, 3)], mirror=True, phase=phase))
        x += need_w
        row_h = max(row_h, need_h)
    return parts, img


def template():
    """Every face in its own color, so you can see which pixels go where (64x32, like vanilla)."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    legs = Image.new("RGBA", (64, 32), CLEAR)
    colors = {"top": (250, 220, 70), "bottom": (160, 130, 40), "right": (230, 120, 60), "front": (220, 60, 60), "left": (70, 160, 230), "back": (120, 80, 200)}
    for faces, part_shade in ((HEAD, 1.0), (BODY, 0.85), (ARM, 0.7), (LEG, 0.55)):
        for face, (x0, y0, w, h) in faces.items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    c = shade(rgba(colors[face]), part_shade * (0.6 if edge else 1.0))
                    if faces is LEG:
                        if face == "top":
                            continue
                        if face == "bottom" or y >= 26:
                            img.putpixel((x, y), c)
                        if face != "bottom" and y < 29:
                            legs.putpixel((x, y), c)
                        continue
                    if faces is HEAD and face == "bottom":
                        continue
                    img.putpixel((x, y), c)
                    if faces is BODY and y < 26 and face not in ("top", "bottom"):
                        legs.putpixel((x, y), c)
    for x in range(10, 14):
        for y in range(11, 14):
            img.putpixel((x, y), CLEAR)
    return img, legs


def main():
    for kind in ("humanoid", "humanoid_leggings", "extras"):
        os.makedirs(os.path.join(ROOT, "textures", "entity", "equipment", kind), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "equipment"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "armor_extras"), exist_ok=True)
    listing = []

    for design in DESIGNS:
        main_layer, legs_layer, glow_main, glow_legs = design.paint()
        tex = os.path.join(ROOT, "textures", "entity", "equipment")
        main_layer.save(os.path.join(tex, "humanoid", design.id + ".png"))
        legs_layer.save(os.path.join(tex, "humanoid_leggings", design.id + ".png"))
        for path in (os.path.join(tex, "humanoid", design.id + "_glow.png"), os.path.join(tex, "humanoid_leggings", design.id + "_glow.png")):
            if os.path.exists(path):
                os.remove(path)
        if design.has_glow:
            glow_main.save(os.path.join(tex, "humanoid", design.id + "_glow.png"))
            glow_legs.save(os.path.join(tex, "humanoid_leggings", design.id + "_glow.png"))
        parts, extras_img = pack_extras(design)
        extras_img.save(os.path.join(tex, "extras", design.id + ".png"))
        with open(os.path.join(ROOT, "armor_extras", design.id + ".json"), "w") as f:
            json.dump({"texture_size": [EXTRAS_SIZE, EXTRAS_SIZE], "parts": parts}, f, indent=1)
            f.write("\n")
        with open(os.path.join(ROOT, "equipment", design.id + ".json"), "w") as f:
            json.dump({"layers": {"humanoid": [{"texture": "vanity:" + design.id}], "humanoid_leggings": [{"texture": "vanity:" + design.id}]}}, f, indent=2)
            f.write("\n")
        listing.append({"id": design.id, "name": design.name, "color": design.icon, "glow": design.has_glow, "pulse": design.glow_pulse, "parts": len(parts)})

    examples = os.path.join(ROOT, "armor_folder")
    os.makedirs(examples, exist_ok=True)
    main_t, legs_t = template()
    main_t.save(os.path.join(examples, "template.png"))
    legs_t.save(os.path.join(examples, "template_leggings.png"))
    frost_a, frost_b, frost_ga, _ = DESIGNS[0].paint()
    frost_a.save(os.path.join(examples, "example.png"))
    frost_b.save(os.path.join(examples, "example_leggings.png"))
    frost_ga.save(os.path.join(examples, "example_glow.png"))
    with open(os.path.join(ROOT, "custom_armors.json"), "w") as f:
        json.dump(listing, f, indent=2)
        f.write("\n")
    for entry in listing:
        print(entry)


if __name__ == "__main__":
    main()
