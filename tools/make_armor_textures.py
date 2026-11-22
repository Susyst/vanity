"""Paints Vanity's built-in custom armor designs.

For each design this writes, under src/main/resources/assets/vanity/:
  textures/entity/equipment/humanoid/<id>.png            helmet, chestplate, boots (64x32 armor layout)
  textures/entity/equipment/humanoid_leggings/<id>.png   leggings
  textures/entity/equipment/humanoid/<id>_glow.png       parts that glow in the dark (and the leggings one)
  textures/entity/equipment/extras/<id>.png              texture for the 3D add-on pieces
  armor_extras/<id>.json                                 the 3D add-on pieces (read by CustomArmors.java)
  equipment/<id>.json                                    armor model layers
plus the template and example copied into config/vanity/armors/examples/.

Run: python3 tools/make_armor_textures.py
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "vanity")
CLEAR = (0, 0, 0, 0)

# Face rectangles (x, y, w, h) on the 64x32 armor layout
HEAD = {"top": (8, 0, 8, 8), "bottom": (16, 0, 8, 8), "right": (0, 8, 8, 8), "front": (8, 8, 8, 8), "left": (16, 8, 8, 8), "back": (24, 8, 8, 8)}
BODY = {"top": (20, 16, 8, 4), "bottom": (28, 16, 8, 4), "right": (16, 20, 4, 12), "front": (20, 20, 8, 12), "left": (28, 20, 4, 12), "back": (32, 20, 8, 12)}
ARM = {"top": (44, 16, 4, 4), "bottom": (48, 16, 4, 4), "right": (40, 20, 4, 12), "front": (44, 20, 4, 12), "left": (48, 20, 4, 12), "back": (52, 20, 4, 12)}
LEG = {"top": (4, 16, 4, 4), "bottom": (8, 16, 4, 4), "right": (0, 20, 4, 12), "front": (4, 20, 4, 12), "left": (8, 20, 4, 12), "back": (12, 20, 4, 12)}
SIDES = ("right", "front", "left", "back")


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(c, a=255):
    return (c[0], c[1], c[2], a)


def shade(c, f):
    return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)


def mix(a, b, t):
    return (clamp(a[0] + (b[0] - a[0]) * t), clamp(a[1] + (b[1] - a[1]) * t), clamp(a[2] + (b[2] - a[2]) * t), 255)


def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


class Face:
    """One face of a box on the texture, drawn in its own coordinates (fx right, fy down)."""

    def __init__(self, design, img, glow, rect, part, name, rows=None):
        self.d, self.img, self.glow = design, img, glow
        self.x0, self.y0, self.w, self.h = rect
        self.part, self.name = part, name
        # rows limits which face rows are painted (boots use the bottom of the leg, leggings the top)
        self.r0, self.r1 = rows if rows else (0, self.h)

    def inside(self, fx, fy):
        return 0 <= fx < self.w and self.r0 <= fy < self.r1

    def px(self, fx, fy, c):
        if self.inside(fx, fy):
            self.img.putpixel((self.x0 + fx, self.y0 + fy), rgb(c) if len(c) == 3 else c)

    def get(self, fx, fy):
        return self.img.getpixel((self.x0 + fx, self.y0 + fy))

    def lit(self, fx, fy, c, strength=1.0):
        """A pixel that glows: drawn on the texture and on the glow mask."""
        if self.inside(fx, fy):
            self.px(fx, fy, c)
            if self.glow is not None:
                self.glow.putpixel((self.x0 + fx, self.y0 + fy), rgb(c, clamp(255 * strength)))

    def hline(self, fy, c, a=0, b=None):
        for fx in range(a, self.w if b is None else b):
            self.px(fx, fy, c)

    def vline(self, fx, c, a=None, b=None):
        for fy in range(self.r0 if a is None else a, self.r1 if b is None else b):
            self.px(fx, fy, c)

    def tint(self, fx, fy, f):
        if self.inside(fx, fy):
            self.px(fx, fy, shade(self.get(fx, fy), f))


class Design:
    """Base armor: plated pieces lit from the top left. Subclasses add their own material and details."""
    visor = "open"
    texture_noise = 0.05

    def __init__(self, ident, name, pal, icon, seed, glow_pulse=False):
        self.id, self.name, self.p, self.icon = ident, name, pal, icon
        self.rng = random.Random(seed)
        self.glow_pulse = glow_pulse
        self.has_glow = False

    # ---- material ----

    def material(self, face, fx, fy):
        """Base color of a pixel: a soft top-to-bottom gradient plus a little noise."""
        h = max(1, face.r1 - face.r0 - 1)
        t = (fy - face.r0) / h
        c = mix(self.p["light"], self.p["mid"], min(1.0, t * 1.4))
        return shade(c, 1.0 + self.rng.uniform(-self.texture_noise, self.texture_noise))

    def fill(self, face):
        for fy in range(face.r0, face.r1):
            for fx in range(face.w):
                face.px(fx, fy, self.material(face, fx, fy))

    def rim(self, face, color=None, sides=(True, True, True, True)):
        c = color or self.p["rim"]
        top, right, bottom, left = sides
        for fx in range(face.w):
            if top:
                face.px(fx, face.r0, c)
            if bottom:
                face.px(fx, face.r1 - 1, shade(c, 0.85))
        for fy in range(face.r0, face.r1):
            if left:
                face.px(0, fy, c)
            if right:
                face.px(face.w - 1, fy, shade(c, 0.85))

    def bevel(self, face, x0, y0, x1, y1):
        """Light top/left edge and dark bottom/right edge inside a plate (x1, y1 inclusive)."""
        for fx in range(x0, x1 + 1):
            face.tint(fx, y0, 1.18)
            face.tint(fx, y1, 0.78)
        for fy in range(y0 + 1, y1):
            face.tint(x0, fy, 1.12)
            face.tint(x1, fy, 0.82)

    def seam(self, face, fy):
        """A plate overlap: dark line with a light line just below."""
        face.hline(fy, self.p["dark"])
        for fx in range(face.w):
            face.tint(fx, fy + 1, 1.15)

    def rivet(self, face, fx, fy):
        face.px(fx, fy, self.p["accent_light"])
        face.px(fx + 1, fy + 1, shade(self.p["dark"], 1.0)) if face.inside(fx + 1, fy + 1) else None

    # ---- pieces ----

    def helmet(self, faces):
        for name, face in faces.items():
            if name == "bottom":
                continue
            self.fill(face)
            self.rim(face)
            if name == "top":
                face.vline(3, shade(self.p["light"], 1.15), 1, 7)
                face.vline(4, shade(self.p["mid"], 0.95), 1, 7)
            elif name == "front":
                self.bevel(face, 1, 1, 6, 6)
                face.hline(2, shade(self.p["light"], 1.12), 1, 7)
                self.helmet_visor(face)
            elif name in ("right", "left"):
                self.bevel(face, 1, 1, 6, 6)
                face.hline(6, self.p["dark"], 1, 7)
                self.rivet(face, 3, 3)
            elif name == "back":
                self.seam(face, 4)
                self.seam(face, 6)
        self.helmet_details(faces)

    def helmet_visor(self, face):
        if self.visor == "open":
            for fx in range(2, 6):
                for fy in range(3, 6):
                    face.px(fx, fy, CLEAR)
            for fy in range(3, 7):
                face.px(1, fy, shade(self.p["mid"], 0.85))
                face.px(6, fy, shade(self.p["mid"], 0.85))
            face.hline(6, self.p["rim"], 2, 6)
        elif self.visor == "slit":
            face.hline(3, self.p["visor"], 1, 7)
            face.hline(4, shade(self.p["visor"], 0.7), 1, 7)
            face.hline(5, shade(self.p["mid"], 0.8), 2, 6)
            face.vline(3, shade(self.p["dark"], 1.0), 5, 7)
            face.vline(4, shade(self.p["dark"], 1.0), 5, 7)
            self.has_glow = True
            for fx in (2, 5):
                face.lit(fx, 3, self.p["glow"])
        elif self.visor == "tvisor":
            face.hline(3, self.p["visor"], 1, 7)
            face.vline(3, self.p["visor"], 3, 7)
            face.vline(4, self.p["visor"], 3, 7)

    def helmet_details(self, faces):
        pass

    def chestplate(self, body, arm):
        for name, face in body.items():
            self.fill(face)
            self.rim(face)
            if name == "front":
                # Two chest plates over banded abdomen plates
                face.vline(3, shade(self.p["dark"], 1.0), 1, 6)
                face.vline(4, shade(self.p["light"], 1.1), 1, 6)
                self.bevel(face, 1, 1, 3, 5)
                self.bevel(face, 4, 1, 6, 5)
                for fy in (6, 8):
                    self.seam(face, fy)
                face.hline(10, self.p["dark"], 1, 7)
            elif name == "back":
                face.vline(3, shade(self.p["light"], 1.12), 1, 11)
                face.vline(4, shade(self.p["dark"], 1.0), 1, 11)
                for fy in (4, 7):
                    self.seam(face, fy)
            elif name in ("right", "left"):
                for fy in (4, 7):
                    self.seam(face, fy)
            elif name == "top":
                face.hline(1, shade(self.p["light"], 1.15), 1, 7)
            elif name == "bottom":
                for fy in range(face.r0, face.r1):
                    face.hline(fy, shade(self.p["dark"], 1.1))
        for name, face in arm.items():
            self.fill(face)
            if name == "top":
                self.rim(face, self.p["accent"])
                continue
            if name == "bottom":
                for fy in range(face.r0, face.r1):
                    face.hline(fy, self.p["dark"])
                continue
            # Pauldron (rows 0-3), under-arm (4-6), bracer (7-11)
            face.hline(0, self.p["accent"])
            self.bevel(face, 0, 1, face.w - 1, 3)
            face.hline(3, shade(self.p["accent"], 0.9))
            for fy in range(4, 7):
                for fx in range(face.w):
                    face.px(fx, fy, shade(self.p["mid"], 0.72 + 0.06 * ((fx + fy) % 2)))
            self.bevel(face, 0, 7, face.w - 1, 11)
            face.hline(7, shade(self.p["light"], 1.1))
            face.hline(11, self.p["rim"])
            if name == "right":
                self.rivet(face, 1, 1)
        self.chest_details(body, arm)

    def chest_details(self, body, arm):
        pass

    def boots(self, leg):
        for name, face in leg.items():
            if name == "top":
                continue
            if name == "bottom":
                for fy in range(4):
                    face.hline(fy, self.p["dark"])
                continue
            self.fill(face)
            face.hline(face.r0, self.p["accent"])
            face.hline(face.r0 + 1, shade(self.p["light"], 1.12))
            face.hline(face.r1 - 1, self.p["dark"])
            face.hline(face.r1 - 2, shade(self.p["mid"], 0.8))
            if name == "front":
                for fx in range(face.w):
                    face.tint(fx, face.r1 - 3, 1.2)
        self.boot_details(leg)

    def boot_details(self, leg):
        pass

    def leggings(self, body, leg):
        for name, face in body.items():
            if name in ("top", "bottom"):
                continue
            self.fill(face)
            # Belt
            face.hline(0, self.p["belt"])
            face.hline(1, shade(self.p["belt"], 0.8))
            if name == "front":
                face.px(3, 0, self.p["accent_light"])
                face.px(4, 0, self.p["accent_light"])
                face.px(3, 1, self.p["accent"])
                face.px(4, 1, self.p["accent"])
                # Two hip plates
                face.vline(3, self.p["dark"], 2, 6)
                face.vline(4, self.p["dark"], 2, 6)
                self.bevel(face, 0, 2, 2, 5)
                self.bevel(face, 5, 2, 7, 5)
            else:
                self.bevel(face, 0, 2, face.w - 1, 5)
        for name, face in leg.items():
            if name in ("top", "bottom"):
                continue
            self.fill(face)
            # Thigh plate, knee guard, shin
            self.bevel(face, 0, 0, face.w - 1, 4)
            face.hline(5, self.p["accent"])
            face.hline(6, shade(self.p["accent"], 0.75))
            if name == "front":
                face.px(1, 5, self.p["accent_light"])
                face.px(2, 5, self.p["accent_light"])
            self.bevel(face, 0, 7, face.w - 1, 8)
        self.legging_details(body, leg)

    def legging_details(self, body, leg):
        pass

    # ---- 3D add-ons ----

    def extras(self):
        """3D pieces: dicts with bone, box [x, y, z, w, h, d], pivot, rot (degrees), paint, and optional glow, mirror,
        spin (degrees per second around the pivot's Y), bob (pixels)."""
        return []

    def paint_extra(self, face, paint, name):
        """Paints one face of a 3D piece. paint: 'main', 'accent', 'dark', 'glow', 'plume', 'leaf', 'crystal'..."""
        p = self.p
        base = {"main": p["mid"], "accent": p["accent"], "dark": p["dark"], "glow": p["glow"], "light": p["light"]}.get(paint, p["mid"])
        for fy in range(face.h):
            for fx in range(face.w):
                t = fy / max(1, face.h - 1)
                c = mix(shade(base, 1.15), shade(base, 0.85), t)
                if paint != "glow":
                    c = shade(c, 1.0 + self.rng.uniform(-0.05, 0.05))
                face.px(fx, fy, c)
        if paint not in ("glow",) and face.w >= 3 and face.h >= 3:
            for fx in range(face.w):
                face.px(fx, 0, shade(base, 1.25))
                face.px(fx, face.h - 1, shade(base, 0.7))

    # ---- output ----

    def paint(self):
        a = Image.new("RGBA", (64, 32), CLEAR)
        b = Image.new("RGBA", (64, 32), CLEAR)
        ga = Image.new("RGBA", (64, 32), CLEAR)
        gb = Image.new("RGBA", (64, 32), CLEAR)
        self.helmet({n: Face(self, a, ga, r, "head", n) for n, r in HEAD.items()})
        self.chestplate({n: Face(self, a, ga, r, "body", n) for n, r in BODY.items()},
                        {n: Face(self, a, ga, r, "arm", n) for n, r in ARM.items()})
        self.boots({n: Face(self, a, ga, r, "boots", n, None if n in ("top", "bottom") else (6, 12)) for n, r in LEG.items()})
        self.leggings({n: Face(self, b, gb, r, "belt", n, None if n in ("top", "bottom") else (0, 6)) for n, r in BODY.items()},
                      {n: Face(self, b, gb, r, "pants", n, None if n in ("top", "bottom") else (0, 9)) for n, r in LEG.items()})
        return a, b, ga, gb


def box_faces(u, v, w, h, d):
    """Texture rectangles of a box with Minecraft's box UV layout at (u, v)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def pack_extras(design):
    """Places every 3D piece's box on a 64x64 texture, paints it, and returns the JSON parts and the image."""
    img = Image.new("RGBA", (64, 64), CLEAR)
    parts = []
    x = y = row_h = 0
    for i, piece in enumerate(design.extras()):
        w, h, d = (int(math.ceil(v)) for v in piece["box"][3:6])
        need_w, need_h = 2 * (w + d), h + d
        if x + need_w > 64:
            x, y, row_h = 0, y + row_h, 0
        if y + need_h > 64:
            raise SystemExit(design.id + ": 3D pieces don't fit on the 64x64 texture")
        for face_name, rect in box_faces(x, y, w, h, d).items():
            if rect[2] > 0 and rect[3] > 0:
                design.paint_extra(Face(design, img, None, rect, "extra", face_name), piece.get("paint", "main"), face_name)
        part = {"name": piece.get("name", "p%d" % i), "bone": piece["bone"], "tex": [x, y], "box": piece["box"],
                "pivot": piece.get("pivot", [0, 0, 0]), "rot": piece.get("rot", [0, 0, 0]), "glow": piece.get("paint") == "glow" or piece.get("glow", False),
                "spin": piece.get("spin", 0), "bob": piece.get("bob", 0), "phase": piece.get("phase", 0)}
        parts.append(part)
        if piece.get("mirror"):
            mx, my, mz, mw, mh, md = piece["box"]
            px, py, pz = part["pivot"]
            rx, ry, rz = part["rot"]
            mirror_bone = {"right_arm": "left_arm", "right_leg": "left_leg"}.get(piece["bone"], piece["bone"])
            if mirror_bone == piece["bone"]:
                px = -px
            parts.append(dict(part, name=part["name"] + "_m", bone=mirror_bone, box=[-(mx + mw), my, mz, mw, mh, md], pivot=[px, py, pz],
                              rot=[rx, -ry, -rz], mirror=True, phase=part["phase"] + 1.7))
        x += need_w
        row_h = max(row_h, need_h)
    return parts, img


# ---------------------------------------------------------------------------------------------- designs

class Frost(Design):
    texture_noise = 0.03

    def material(self, face, fx, fy):
        c = super().material(face, fx, fy)
        # Faceted ice: diagonal bright streaks
        if (fx + 2 * fy) % 6 == 0:
            c = mix(c, (236, 250, 255), 0.4)
        return c

    def chest_details(self, body, arm):
        f = body["front"]
        self.has_glow = True
        for fx, fy in ((3, 2), (4, 2), (3, 3), (4, 3)):
            f.lit(fx, fy, (190, 245, 255) if (fx + fy) % 2 else (120, 220, 255))
        for name, face in list(body.items()) + list(arm.items()):
            for _ in range(face.w * (face.r1 - face.r0) // 16):
                fx, fy = self.rng.randrange(face.w), self.rng.randrange(face.r0, face.r1)
                face.lit(fx, fy, (240, 252, 255), 0.55)

    def helmet_details(self, faces):
        self.has_glow = True
        for fx in (2, 3, 4, 5):
            faces["front"].lit(fx, 1, (200, 246, 255), 0.8)

    def extras(self):
        crystal = "crystal"
        return [
            {"name": "crown_mid", "bone": "head", "box": [-1, -5, -1, 2, 5, 2], "pivot": [0, -9, -1], "rot": [-8, 45, 0], "paint": crystal, "glow": True},
            {"name": "crown_side", "bone": "head", "box": [-1, -3, -1, 2, 3, 2], "pivot": [-3, -9, -1], "rot": [0, 45, 18], "paint": crystal, "glow": True, "mirror": True},
            {"name": "shoulder_big", "bone": "right_arm", "box": [-1, -5, -1, 2, 5, 2], "pivot": [-2, -3, 0], "rot": [8, 45, 22], "paint": crystal, "glow": True, "mirror": True},
            {"name": "shoulder_small", "bone": "right_arm", "box": [-0.5, -3, -0.5, 1, 3, 1], "pivot": [-3, -3, 2], "rot": [30, 0, 30], "paint": crystal, "glow": True, "mirror": True},
        ]

    def paint_extra(self, face, paint, name):
        if paint == "crystal":
            for fy in range(face.h):
                for fx in range(face.w):
                    t = fy / max(1, face.h - 1)
                    c = mix((236, 252, 255), (96, 190, 240), t)
                    if fx == 0:
                        c = mix(c, (255, 255, 255), 0.5)
                    face.px(fx, fy, c)
            return
        super().paint_extra(face, paint, name)


class Obsidian(Design):
    visor = "slit"

    def material(self, face, fx, fy):
        c = super().material(face, fx, fy)
        # Glassy obsidian: rare purple sheen
        if self.rng.random() < 0.08:
            c = mix(c, (70, 40, 110), 0.6)
        return c

    def chest_details(self, body, arm):
        self.has_glow = True
        f = body["front"]
        # Glowing amethyst core and seams
        for fx, fy, s in ((3, 2, 1.0), (4, 2, 1.0), (3, 3, 1.0), (4, 3, 1.0), (3, 1, 0.6), (4, 4, 0.6)):
            f.lit(fx, fy, self.p["glow"] if s == 1.0 else shade(self.p["glow"], 0.8), s)
        for name in ("front", "back"):
            for fx in range(1, 7):
                body[name].lit(fx, 6, shade(self.p["glow"], 0.75), 0.5)
        for face in arm.values():
            if face.name not in ("top", "bottom"):
                face.hline(3, self.p["accent"])
                for fx in range(face.w):
                    face.lit(fx, 3, shade(self.p["glow"], 0.8), 0.55)

    def helmet_details(self, faces):
        self.has_glow = True
        faces["front"].lit(3, 1, self.p["glow"])
        faces["front"].lit(4, 1, self.p["glow"])

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name not in ("top", "bottom"):
                for fx in range(face.w):
                    face.lit(fx, 5, shade(self.p["glow"], 0.8), 0.5)

    def extras(self):
        return [
            {"name": "horn_base", "bone": "head", "box": [-4, -1, -1, 4, 2, 2], "pivot": [-4.5, -7.5, 0], "rot": [0, 0, 28], "paint": "dark", "mirror": True},
            {"name": "horn_tip", "bone": "head", "box": [-1, -4, -1, 2, 4, 2], "pivot": [-8, -9.5, 0], "rot": [0, 0, -12], "paint": "dark", "mirror": True},
            {"name": "horn_point", "bone": "head", "box": [-0.5, -2, -0.5, 1, 2, 1], "pivot": [-8.3, -13, 0], "rot": [0, 0, -20], "paint": "glow", "mirror": True},
            {"name": "pauldron", "bone": "right_arm", "box": [-4.5, -3.5, -3.5, 6, 3, 7], "pivot": [0, 0, 0], "rot": [0, 0, -10], "paint": "main", "mirror": True},
            {"name": "pauldron_spike", "bone": "right_arm", "box": [-1, -3, -1, 2, 3, 2], "pivot": [-3, -3.5, 0], "rot": [0, 45, 20], "paint": "dark", "mirror": True},
        ]


class Ember(Design):
    def material(self, face, fx, fy):
        c = super().material(face, fx, fy)
        return shade(c, 0.92 + 0.12 * ((fx * 7 + fy * 3) % 5 == 0))

    def cracks(self, face, n):
        self.has_glow = True
        for _ in range(n):
            fx, fy = self.rng.randrange(1, max(2, face.w - 1)), self.rng.randrange(face.r0 + 1, max(face.r0 + 2, face.r1 - 1))
            for step in range(self.rng.randint(3, 6)):
                if not face.inside(fx, fy):
                    break
                hot = step % 3 == 0
                face.lit(fx, fy, (255, 226, 120) if hot else (255, 120, 30), 1.0 if hot else 0.85)
                fx += self.rng.choice((-1, 0, 1))
                fy += self.rng.choice((0, 1, 1))

    def helmet_details(self, faces):
        for name in ("front", "right", "left", "back", "top"):
            self.cracks(faces[name], 1 if name == "front" else 2)

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name != "bottom":
                self.cracks(face, max(1, face.w * face.h // 30))

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.cracks(face, 1)

    def legging_details(self, body, leg):
        for face in list(body.values()) + list(leg.values()):
            if face.name in SIDES:
                self.cracks(face, 1)

    def extras(self):
        return [
            {"name": "flame_front", "bone": "head", "box": [-1, -4, -1, 2, 4, 2], "pivot": [-2.5, -8.5, -2], "rot": [30, 0, 22], "paint": "dark", "mirror": True},
            {"name": "flame_front_tip", "bone": "head", "box": [-0.5, -2, -0.5, 1, 2, 1], "pivot": [-4.2, -11.3, 0.1], "rot": [30, 0, 22], "paint": "glow", "mirror": True},
            {"name": "flame_back", "bone": "head", "box": [-1, -3, -1, 2, 3, 2], "pivot": [-2, -8.5, 2], "rot": [50, 0, 18], "paint": "dark", "mirror": True},
            {"name": "flame_back_tip", "bone": "head", "box": [-0.5, -2, -0.5, 1, 2, 1], "pivot": [-3, -10.4, 4.2], "rot": [50, 0, 18], "paint": "glow", "mirror": True},
            {"name": "spike_a", "bone": "right_arm", "box": [-0.5, -4, -0.5, 1, 4, 1], "pivot": [-3, -3, -1.5], "rot": [0, 0, 25], "paint": "dark", "mirror": True},
            {"name": "spike_b", "bone": "right_arm", "box": [-0.5, -5, -0.5, 1, 5, 1], "pivot": [-2.5, -3, 0.5], "rot": [0, 0, 18], "paint": "dark", "mirror": True},
            {"name": "spike_tip", "bone": "right_arm", "box": [-0.5, -1, -0.5, 1, 1, 1], "pivot": [-4.1, -7.4, 0.5], "rot": [0, 0, 18], "paint": "glow", "mirror": True},
        ]


class Verdant(Design):
    def material(self, face, fx, fy):
        c = super().material(face, fx, fy)
        # Moss: clumps of lighter green
        if self.rng.random() < 0.18:
            c = mix(c, (120, 180, 70), 0.5)
        return c

    def vines(self, face):
        phase = self.rng.random() * 6
        for fy in range(face.r0, face.r1):
            fx = int(round((face.w - 1) / 2 + (face.w / 2 - 1) * math.sin(fy * 0.9 + phase)))
            face.px(fx, fy, self.p["accent"])
            if self.rng.random() < 0.3:
                face.px(fx + 1, fy, (164, 226, 96))

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name in SIDES and face.h >= 8:
                self.vines(face)
        self.has_glow = True
        for fx, fy in ((2, 3), (5, 4), (3, 9)):
            body["front"].lit(fx, fy, (180, 255, 140), 0.6)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.vines(face)

    def extras(self):
        return [
            {"name": "leaf_front", "bone": "head", "box": [-1.5, -4, -0.5, 3, 4, 1], "pivot": [-2, -8.6, -4], "rot": [-20, 0, 25], "paint": "leaf", "mirror": True},
            {"name": "leaf_side", "bone": "head", "box": [-0.5, -4, -1.5, 1, 4, 3], "pivot": [-4.6, -8.6, 0.5], "rot": [0, 0, 40], "paint": "leaf", "mirror": True},
            {"name": "leaf_back", "bone": "head", "box": [-1.5, -3, -0.5, 3, 3, 1], "pivot": [0, -8.6, 4.4], "rot": [30, 0, 0], "paint": "leaf"},
            {"name": "sprout", "bone": "head", "box": [-0.5, -3, -0.5, 1, 3, 1], "pivot": [0.5, -9, -0.5], "rot": [8, 0, -10], "paint": "accent"},
            {"name": "sprout_leaf", "bone": "head", "box": [-1, -1, -0.5, 2, 1, 1], "pivot": [0.9, -12, -0.4], "rot": [0, 0, -10], "paint": "leaf"},
            {"name": "shoulder_leaf", "bone": "right_arm", "box": [-0.5, -3, -1.5, 1, 3, 3], "pivot": [-3.6, -3, 0], "rot": [0, 0, 35], "paint": "leaf", "mirror": True},
            {"name": "shoulder_leaf_b", "bone": "right_arm", "box": [-1.5, -3, -0.5, 3, 3, 1], "pivot": [-2, -3, -2.6], "rot": [-30, 0, 20], "paint": "leaf", "mirror": True},
            {"name": "mushroom_stem", "bone": "right_arm", "box": [-0.5, -2, -0.5, 1, 2, 1], "pivot": [-1, -3, 1.5], "rot": [0, 0, 0], "paint": "light"},
            {"name": "mushroom_cap", "bone": "right_arm", "box": [-1.5, -1, -1.5, 3, 1, 3], "pivot": [-1, -5, 1.5], "rot": [0, 0, 0], "paint": "glow"},
        ]

    def paint_extra(self, face, paint, name):
        if paint == "leaf":
            for fy in range(face.h):
                for fx in range(face.w):
                    edge = fx in (0, face.w - 1) or fy in (0, face.h - 1)
                    c = (74, 140, 52) if edge else (108, 182, 70)
                    if (fx + fy) % 3 == 0 and not edge:
                        c = (140, 206, 92)
                    face.px(fx, fy, c)
            for fx, fy in ((0, 0), (face.w - 1, 0)):
                face.px(fx, fy, CLEAR)
            return
        super().paint_extra(face, paint, name)


class Royal(Design):
    texture_noise = 0.025

    def material(self, face, fx, fy):
        c = super().material(face, fx, fy)
        # Polished: a bright diagonal reflection
        if (fx - fy) % 7 == 0:
            c = mix(c, (255, 255, 255), 0.35)
        return c

    def chest_details(self, body, arm):
        f = body["front"]
        gold, deep = self.p["accent"], shade(self.p["accent"], 0.7)
        # Gold emblem: a crown over a shield
        for fx, fy in ((2, 2), (3, 1), (4, 1), (5, 2), (3, 2), (4, 2)):
            f.px(fx, fy, gold)
        for fx, fy in ((3, 3), (4, 3), (3, 4), (4, 4)):
            f.px(fx, fy, (40, 70, 160))
        f.px(3, 3, (90, 130, 220))
        for fy in range(1, 11):
            for face in (body["right"], body["left"]):
                face.px(1, fy, shade(gold, 0.9))
        for face in arm.values():
            if face.name in SIDES:
                face.hline(0, gold)
                face.hline(3, deep)
                face.hline(7, gold)

    def helmet_details(self, faces):
        gold = self.p["accent"]
        for name in ("top", "front", "back"):
            face = faces[name]
            face.vline(3, gold, 0, 8 if name != "front" else 3)
            face.vline(4, shade(gold, 1.1), 0, 8 if name != "front" else 3)
        faces["front"].hline(6, gold, 1, 7)

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                face.hline(face.r0, self.p["accent"])

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                face.hline(5, self.p["accent"])

    def extras(self):
        return [
            {"name": "plume", "bone": "head", "box": [-1, -3, -4, 2, 3, 8], "pivot": [0, -9, 0.5], "rot": [0, 0, 0], "paint": "plume"},
            {"name": "plume_tail", "bone": "head", "box": [-1, -2, 0, 2, 2, 5], "pivot": [0, -10.5, 4], "rot": [-30, 0, 0], "paint": "plume"},
            {"name": "pauldron", "bone": "right_arm", "box": [-4.5, -3.5, -3.5, 6, 3, 7], "pivot": [0, 0, 0], "rot": [0, 0, -14], "paint": "main", "mirror": True},
            {"name": "pauldron_rim", "bone": "right_arm", "box": [-4.6, -1, -3.6, 6, 1, 7], "pivot": [0, 0, 0], "rot": [0, 0, -14], "paint": "accent", "mirror": True},
        ]

    def paint_extra(self, face, paint, name):
        if paint == "plume":
            for fy in range(face.h):
                for fx in range(face.w):
                    c = (196, 30, 46) if (fx + fy) % 3 else (150, 18, 34)
                    if fy == 0:
                        c = (232, 70, 80)
                    face.px(fx, fy, c)
            return
        super().paint_extra(face, paint, name)


class Void(Design):
    visor = "slit"

    def material(self, face, fx, fy):
        t = (fy - face.r0) / max(1, face.r1 - face.r0 - 1)
        c = mix(self.p["light"], (40, 18, 74), t * 0.9)
        return shade(c, 1.0 + self.rng.uniform(-0.06, 0.06))

    def stars(self, face):
        self.has_glow = True
        for _ in range(max(1, face.w * (face.r1 - face.r0) // 10)):
            fx, fy = self.rng.randrange(face.w), self.rng.randrange(face.r0, face.r1)
            c = self.rng.choice([(255, 255, 255), (220, 196, 255), (170, 130, 255)])
            face.lit(fx, fy, c, self.rng.choice([0.6, 0.85, 1.0]))

    def helmet_details(self, faces):
        for name in ("top", "right", "left", "back"):
            self.stars(faces[name])

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name != "bottom":
                self.stars(face)
        for name in ("right", "front", "left", "back"):
            face = body[name]
            for fy in range(face.r0, face.r1):
                face.lit(0, fy, shade(self.p["glow"], 0.7), 0.45)

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.stars(face)

    def legging_details(self, body, leg):
        for face in list(body.values()) + list(leg.values()):
            if face.name in SIDES:
                self.stars(face)

    def extras(self):
        halo = {"bone": "head", "pivot": [0, -12.5, 0], "rot": [22, 0, 0], "paint": "glow", "bob": 0.6}
        return [
            dict(halo, name="halo_front", box=[-5, 0, -5, 10, 1, 1]),
            dict(halo, name="halo_back", box=[-5, 0, 4, 10, 1, 1]),
            dict(halo, name="halo_right", box=[-5, 0, -4, 1, 1, 8]),
            dict(halo, name="halo_left", box=[4, 0, -4, 1, 1, 8]),
            {"name": "orb", "bone": "body", "box": [-1, -1, -1, 2, 2, 2], "pivot": [-8, -3, 0], "rot": [45, 45, 0], "paint": "glow", "bob": 1.2, "spin": 90, "mirror": True},
        ]


class Sakura(Design):
    texture_noise = 0.03

    def lacing(self, face, rows):
        lace = (150, 30, 52)
        for fy in rows:
            for fx in range(face.w):
                face.px(fx, fy, lace if fx % 2 == 0 else shade(lace, 1.3))

    def blossoms(self, face, n):
        for _ in range(n):
            fx, fy = self.rng.randrange(1, max(2, face.w - 1)), self.rng.randrange(face.r0 + 1, max(face.r0 + 2, face.r1 - 1))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                face.px(fx + dx, fy + dy, (255, 240, 246))
            face.px(fx, fy, (255, 200, 90))

    def chest_details(self, body, arm):
        for name, face in body.items():
            if name in SIDES:
                self.lacing(face, (4, 7, 10))
        for face in arm.values():
            if face.name in SIDES:
                self.lacing(face, (8, 10))
        self.blossoms(body["front"], 2)
        self.blossoms(body["back"], 2)
        f = body["front"]
        for i in range(8):
            f.px(1 + i * 5 // 8, 10 - i, (92, 56, 48))

    def helmet_details(self, faces):
        self.lacing(faces["back"], (3, 5))
        self.blossoms(faces["right"], 1)
        self.blossoms(faces["left"], 1)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.lacing(face, (2, 4))

    def extras(self):
        return [
            {"name": "crest", "bone": "head", "box": [-0.5, -5, -0.5, 1, 5, 1], "pivot": [-0.7, -7.5, -5], "rot": [0, 0, 28], "paint": "accent", "mirror": True},
            {"name": "crest_disc", "bone": "head", "box": [-1, -1, -0.5, 2, 2, 1], "pivot": [0, -7.5, -5.2], "rot": [0, 0, 45], "paint": "accent"},
            {"name": "sode", "bone": "right_arm", "box": [-1, -1, -3.5, 1, 6, 7], "pivot": [-4, -2, 0], "rot": [0, 0, 12], "paint": "main", "mirror": True},
            {"name": "blossom", "bone": "head", "box": [-1, -1, -1, 2, 2, 2], "pivot": [-5, -6, 2], "rot": [0, 45, 0], "paint": "blossom"},
        ]

    def paint_extra(self, face, paint, name):
        if paint == "blossom":
            for fy in range(face.h):
                for fx in range(face.w):
                    face.px(fx, fy, (255, 236, 244) if (fx + fy) % 2 else (255, 196, 220))
            return
        super().paint_extra(face, paint, name)
        if paint == "main" and face.h >= 6:
            for fy in (1, 3, 5):
                for fx in range(face.w):
                    face.px(fx, fy, (150, 30, 52) if fx % 2 == 0 else (196, 50, 76))


class Circuit(Design):
    visor = "slit"

    def traces(self, face, n):
        self.has_glow = True
        for _ in range(n):
            fx, fy = self.rng.randrange(1, max(2, face.w - 1)), self.rng.randrange(face.r0 + 1, max(face.r0 + 2, face.r1 - 1))
            horizontal = self.rng.random() < 0.5
            for _ in range(self.rng.randint(2, 6)):
                if not face.inside(fx, fy):
                    break
                face.lit(fx, fy, self.p["glow"], 0.9)
                fx, fy = (fx + 1, fy) if horizontal else (fx, fy + 1)
                if self.rng.random() < 0.25:
                    horizontal = not horizontal
            face.lit(fx, fy, (210, 252, 255), 1.0)

    def helmet_details(self, faces):
        for name in ("right", "left", "back", "top"):
            self.traces(faces[name], 2)

    def chest_details(self, body, arm):
        for face in list(body.values()) + list(arm.values()):
            if face.name != "bottom":
                self.traces(face, max(1, face.w * face.h // 24))
        f = body["front"]
        for fx, fy in ((3, 3), (4, 3), (3, 4), (4, 4)):
            f.lit(fx, fy, (210, 252, 255))

    def boot_details(self, leg):
        for face in leg.values():
            if face.name in SIDES:
                face.lit(1, face.r0 + 2, self.p["glow"], 0.9)
                face.lit(2, face.r0 + 2, self.p["glow"], 0.9)

    def legging_details(self, body, leg):
        for face in leg.values():
            if face.name in SIDES:
                self.traces(face, 1)

    def extras(self):
        return [
            {"name": "antenna", "bone": "head", "box": [-0.5, -6, -0.5, 1, 6, 1], "pivot": [-4.5, -7, 1.5], "rot": [-6, 0, -8], "paint": "dark"},
            {"name": "antenna_tip", "bone": "head", "box": [-1, -1, -1, 2, 2, 2], "pivot": [-5.4, -13.2, 0.9], "rot": [0, 0, 0], "paint": "glow", "bob": 0.3},
            {"name": "ear_module", "bone": "head", "box": [-1, -2, -1.5, 1, 3, 3], "pivot": [-5, -3.5, 0], "rot": [0, 0, 0], "paint": "main", "mirror": True},
            {"name": "core", "bone": "body", "box": [-2.5, 0, 0, 5, 6, 2], "pivot": [0, 2, 3], "rot": [0, 0, 0], "paint": "main"},
            {"name": "core_cells", "bone": "body", "box": [-1.5, 0, 0, 3, 4, 1], "pivot": [0, 3, 4.6], "rot": [0, 0, 0], "paint": "glow"},
            {"name": "shoulder_plate", "bone": "right_arm", "box": [-1, -1, -2.5, 1, 4, 5], "pivot": [-4, -2.5, 0], "rot": [0, 0, 8], "paint": "main", "mirror": True},
            {"name": "shoulder_strip", "bone": "right_arm", "box": [-0.5, 0, -1.5, 1, 3, 3], "pivot": [-4.6, -2, 0], "rot": [0, 0, 8], "paint": "glow", "mirror": True},
        ]


def pal(light, mid, dark, rim, accent, accent_light, glow, visor=None, belt=None):
    return {"light": light, "mid": mid, "dark": dark, "rim": rim, "accent": accent, "accent_light": accent_light,
            "glow": glow, "visor": visor or dark, "belt": belt or rim}


DESIGNS = [
    Frost("frostforged", "Frostforged", pal((176, 226, 250), (104, 178, 224), (34, 78, 134), (52, 104, 162), (220, 244, 255), (255, 255, 255), (150, 230, 255)), 0x7AC8EB, 11),
    Obsidian("obsidian_knight", "Obsidian Knight", pal((52, 38, 66), (28, 20, 38), (8, 5, 14), (16, 10, 24), (112, 64, 176), (190, 150, 255), (190, 110, 255), visor=(6, 3, 10)), 0x3A2A52, 12, glow_pulse=True),
    Ember("ember", "Ember", pal((72, 60, 56), (44, 36, 34), (18, 12, 10), (26, 18, 16), (200, 84, 24), (255, 186, 90), (255, 140, 40)), 0xC0501A, 13, glow_pulse=True),
    Verdant("verdant", "Verdant", pal((96, 156, 76), (60, 118, 54), (44, 70, 30), (92, 62, 34), (96, 170, 64), (190, 230, 120), (170, 255, 140), belt=(110, 74, 40)), 0x3E7A3A, 14),
    Royal("royal_guard", "Royal Guard", pal((248, 248, 244), (206, 208, 212), (120, 124, 134), (176, 136, 44), (228, 182, 60), (255, 232, 150), (255, 230, 150), belt=(130, 30, 40)), 0xE8E6DE, 15),
    Void("void_walker", "Void Walker", pal((24, 18, 44), (14, 10, 26), (4, 2, 10), (70, 36, 128), (124, 70, 220), (210, 180, 255), (176, 110, 255), visor=(2, 0, 6)), 0x2A1446, 16, glow_pulse=True),
    Sakura("sakura", "Sakura", pal((252, 206, 224), (236, 160, 192), (150, 70, 104), (190, 92, 130), (226, 182, 60), (255, 236, 160), (255, 220, 236), belt=(150, 30, 52)), 0xF2A7C3, 17),
    Circuit("circuit", "Circuit", pal((78, 86, 96), (46, 52, 60), (20, 24, 30), (30, 34, 42), (90, 100, 112), (150, 160, 170), (64, 228, 255), visor=(8, 18, 24)), 0x2C8FA8, 18),
]


def template():
    """Every face in its own color, so you can see which pixels go where."""
    img = Image.new("RGBA", (64, 32), CLEAR)
    legs = Image.new("RGBA", (64, 32), CLEAR)
    colors = {"top": (250, 220, 70), "bottom": (160, 130, 40), "right": (230, 120, 60), "front": (220, 60, 60), "left": (70, 160, 230), "back": (120, 80, 200)}
    for faces, part_shade in ((HEAD, 1.0), (BODY, 0.85), (ARM, 0.7), (LEG, 0.55)):
        for face, (x0, y0, w, h) in faces.items():
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    edge = x in (x0, x0 + w - 1) or y in (y0, y0 + h - 1)
                    c = shade(colors[face], part_shade * (0.6 if edge else 1.0))
                    if faces is LEG:
                        if face in ("top",):
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
            json.dump({"texture_size": [64, 64], "parts": parts}, f, indent=1)
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
    frost_a, frost_b, frost_ga, frost_gb = DESIGNS[0].paint()
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
