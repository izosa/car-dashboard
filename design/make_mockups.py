"""Regenerates mockup_closeup.jpg and mockup_dashboard.jpg in `car/photos/`: screenshots/home.png
warped in perspective onto the head unit in the two photos of the car.

The photos are not in the repo (they show the car's interior); they live one level up, in
`car/photos/`. usage: python make_mockups.py   (from anywhere; paths are relative to this file)

The screen corners (top-left, top-right, bottom-right, bottom-left, in photo pixels) were read
off the photos by hand. The Teyes status bar is a system overlay, but Dashboard is fullscreen
and hides it, so by default the photo's own bar is not copied over; set KEEP_STATUS_BAR=1 to
keep it (useful for apps that don't go fullscreen).
"""
import os
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
PHOTOS = HERE.parents[1] / "photos"
SCREENSHOT = HERE.parent / "screenshots" / "home.png"

JOBS = [
    (PHOTOS / "head-unit-closeup.jpg", PHOTOS / "mockup_closeup.jpg",
     [(1205, 1002), (3100, 1000), (3185, 2212), (1262, 2237)]),
    (PHOTOS / "head-unit-dashboard.jpg", PHOTOS / "mockup_dashboard.jpg",
     [(1922, 996), (2642, 1000), (2650, 1447), (1930, 1450)]),
]
KEEP_STATUS_BAR = bool(os.environ.get("KEEP_STATUS_BAR"))


def perspective_coeffs(dst, src):
    # PIL's perspective transform maps output (photo) coords back to input (screenshot) coords.
    a, b = [], []
    for (x, y), (u, v) in zip(dst, src):
        a += [[x, y, 1, 0, 0, 0, -u * x, -u * y], [0, 0, 0, x, y, 1, -v * x, -v * y]]
        b += [u, v]
    return np.linalg.solve(np.array(a, float), np.array(b, float))


def composite(photo_path, out_path, quad):
    photo = Image.open(photo_path).convert("RGB")
    shot = Image.open(SCREENSHOT).convert("RGB")
    w, h = shot.size
    c = perspective_coeffs(quad, [(0, 0), (w, 0), (w, h), (0, h)])
    warped = shot.transform(photo.size, Image.PERSPECTIVE, c, Image.BICUBIC)
    mask = Image.new("L", shot.size, 255).transform(photo.size, Image.PERSPECTIVE, c, Image.BICUBIC)
    mask = mask.filter(ImageFilter.GaussianBlur(1.5))

    glass = np.asarray(photo).astype(np.float32)
    screen = np.asarray(warped).astype(np.float32)
    if KEEP_STATUS_BAR:
        # Copy the photo's bright status bar icons (top 11% of the screen) over the screenshot.
        bar = Image.new("L", (w, h), 0)
        bar.paste(255, (0, 0, w, int(h * 0.11)))
        bar = np.asarray(bar.transform(photo.size, Image.PERSPECTIVE, c, Image.BILINEAR))
        icons = (glass.mean(axis=2, keepdims=True) > 140) * (bar.astype(np.float32)[..., None] / 255)
        screen = screen * (1 - icons) + glass * icons
    m = np.asarray(mask).astype(np.float32)[..., None] / 255
    img = Image.fromarray((glass * (1 - m) + screen * m).astype(np.uint8))
    img.thumbnail((2000, 2000), Image.LANCZOS)
    img.save(out_path, quality=90)
    print("wrote", out_path)


if __name__ == "__main__":
    for photo_path, out_path, quad in JOBS:
        composite(photo_path, out_path, quad)
