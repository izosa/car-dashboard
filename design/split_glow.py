"""Splits a background into the three layers of the animated home screen: a dimmed base, a
faint hexagon texture over its black areas (revealed from the centre at start), and the cyan
glow (lights up, then breathes).

usage: python split_glow.py <source.png> <out_base.jpg> <out_texture.png> <out_glow.png>
       [width height]
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

HEX_SIZE = 11
HEX_STRENGTH = 23  # added brightness on a texture line at the centre, out of 255


def hex_texture(width, height):
    img = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(img)
    w = math.sqrt(3) * HEX_SIZE
    for row in range(-1, int(height / (1.5 * HEX_SIZE)) + 2):
        for col in range(-1, int(width / w) + 2):
            cx = col * w + (w / 2 if row % 2 else 0)
            cy = row * 1.5 * HEX_SIZE
            points = [(cx + HEX_SIZE * math.cos(math.radians(60 * k + 30)),
                       cy + HEX_SIZE * math.sin(math.radians(60 * k + 30))) for k in range(6)]
            draw.polygon(points, outline=255)
    return np.asarray(img).astype(np.float32) / 255


def vignette(width, height):
    # Strongest around the logo and clock, fading out towards the arcs and the corners.
    y, x = np.mgrid[0:height, 0:width]
    r = np.hypot((x - width / 2) / (width / 2), (y - height * 0.42) / (height / 2))
    return np.clip(1.15 - r, 0, 1).astype(np.float32)


src, out_base, out_texture, out_glow = sys.argv[1:5]
size = tuple(map(int, sys.argv[5:7])) if len(sys.argv) > 6 else (1066, 600)

img = np.asarray(Image.open(src).convert("RGB").resize(size, Image.LANCZOS)).astype(np.float32)
r, b = img[..., 0], img[..., 2]
# Cyan/blue light only: grey and silver parts have blue roughly equal to red.
mask = np.clip((b - r) / 140.0, 0, 1)[..., None]

base = img * (1 - 0.9 * mask)
Image.fromarray(base.clip(0, 255).astype(np.uint8)).save(out_base, quality=92, optimize=True)

# Over a near-black base, a cool white at this alpha adds about HEX_STRENGTH of brightness.
dark = np.clip(1 - img.mean(axis=2) / 255 * 4, 0, 1)
alpha = hex_texture(*size) * vignette(*size) * dark * HEX_STRENGTH * 1.1
texture = np.zeros(size[::-1] + (4,), np.float32)
texture[..., :3] = np.array([0.85, 0.95, 1.1]) / 1.1 * 255
texture[..., 3] = alpha
Image.fromarray(texture.clip(0, 255).astype(np.uint8), "RGBA").save(out_texture, optimize=True)

glow = np.dstack([img, mask[..., 0] * 255]).clip(0, 255).astype(np.uint8)
Image.fromarray(glow, "RGBA").save(out_glow, optimize=True)
