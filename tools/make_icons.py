from PIL import Image, ImageDraw
import math, os

S = 1024
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Rounded square background with a very subtle vertical gradient.
bg = Image.new("RGBA", (S, S), (0, 0, 0, 0))
bd = ImageDraw.Draw(bg)
for y in range(S):
    t = y / (S - 1)
    r = int(10 + 4 * t)
    g = int(14 + 8 * t)
    b = int(20 + 12 * t)
    bd.line([(0, y), (S, y)], fill=(r, g, b, 255))
mask = Image.new("L", (S, S), 0)
md = ImageDraw.Draw(mask)
md.rounded_rectangle([0, 0, S - 1, S - 1], radius=250, fill=255)
img.paste(bg, (0, 0), mask)

# Soft teal glow ring behind the mark.
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
for i in range(24, 0, -1):
    alpha = int(3 + 2.2 * (24 - i))
    r = 330 + i * 5
    gd.ellipse([S/2-r, S/2-r, S/2+r, S/2+r], outline=(45, 212, 191, alpha), width=8)
img = Image.alpha_composite(img, glow)

d = ImageDraw.Draw(img)

TEAL = (94, 234, 212, 255)
TEAL_DARK = (20, 184, 166, 255)
WHITE = (244, 250, 249, 255)

# Waveform bars (sound / speech).
bars = [(-300, 104, 54), (-190, 168, 54), (-80, 250, 54), (30, 196, 54), (140, 134, 54), (250, 78, 54)]
for cx, h, w in bars:
    x0 = S/2 + cx - w/2
    y0 = S/2 - h/2 + 34
    d.rounded_rectangle([x0, y0, x0+w, y0+h], radius=w/2, fill=TEAL)

# Notebook page in front, lower right: white sheet with teal lines.
page = [430, 492, 760, 800]
shadow = [page[0]+14, page[1]+18, page[2]+14, page[3]+18]
d.rounded_rectangle(shadow, radius=54, fill=(0, 0, 0, 90))
d.rounded_rectangle(page, radius=54, fill=WHITE)
# folded corner
fold = [(642, 492), (760, 610), (760, 492)]
d.polygon(fold, fill=(204, 251, 241, 255))
d.line([(642, 492), (642, 610), (760, 610)], fill=(15, 118, 110, 255), width=12, joint="curve")
for i, yy in enumerate([650, 700, 750]):
    xx2 = 690 if i == 2 else 710
    d.rounded_rectangle([500, yy, xx2, yy+22], radius=11, fill=(20, 184, 166, 255))

# Small recording dot, upper-right.
d.ellipse([718, 220, 822, 324], fill=(255, 92, 99, 255))
d.ellipse([744, 246, 796, 298], fill=(255, 255, 255, 255))

img = img.resize((512, 512), Image.LANCZOS)
os.makedirs("res", exist_ok=True)
for name, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    out = img.resize((size, size), Image.LANCZOS)
    out.save(f"res/mipmap-{name}/ic_launcher.png")

# Foreground for notification / launcher round (white glyph, transparent).
fg = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
fd = ImageDraw.Draw(fg)
for cx, h, w in [(-118, 78, 42), (-40, 128, 42), (38, 188, 42), (116, 100, 42)]:
    x0 = 256 + cx - w/2
    y0 = 256 - h/2 - 16
    fd.rounded_rectangle([x0, y0, x0+w, y0+h], radius=w/2, fill=(255, 255, 255, 255))
fd.rounded_rectangle([178, 342, 334, 372], radius=15, fill=(255, 255, 255, 255))
fg.save("res/drawable/ic_stat_tingji.png")
print("icons written")
