import sys
from PIL import Image
src, res, preview = sys.argv[1], sys.argv[2], sys.argv[3]
im = Image.open(src).convert('RGB')
W, H = im.size
px = im.load()
bg = (35, 18, 12)
T, N = 150.0, 14.0
out = Image.new('RGBA', (W, H)); op = out.load()
mono = Image.new('RGBA', (W, H)); mp = mono.load()
for y in range(H):
    for x in range(W):
        r, g, b = px[x, y]
        d = abs(r-bg[0]) + abs(g-bg[1]) + abs(b-bg[2])
        a = max(0.0, min(1.0, (d - N) / (T - N)))
        if a <= 0:
            op[x, y] = (0, 0, 0, 0); mp[x, y] = (0, 0, 0, 0); continue
        c = tuple(max(0, min(255, round(bg[i] + ((r, g, b)[i] - bg[i]) / a))) for i in range(3))
        op[x, y] = c + (round(a*255),)
        mp[x, y] = (255, 255, 255, round(a*255))
# Sembol merkezi ve en uzak noktası
cx, cy = 626, 627
rmax = 0
for y in range(0, H, 2):
    for x in range(0, W, 2):
        if op[x, y][3] > 40:
            rmax = max(rmax, ((x-cx)**2 + (y-cy)**2) ** .5)
print('rmax', rmax)
R_DP = 31.0  # 108dp tuvalde; güvenli daire yarıçapı 33dp
def canvas(img, size):
    s = size / 108.0 * R_DP / rmax
    sm = img.resize((round(W*s), round(H*s)), Image.LANCZOS)
    c = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c.alpha_composite(sm, (round(size/2 - cx*s), round(size/2 - cy*s)))
    return c
import os
for name, size in [('mdpi',108),('hdpi',162),('xhdpi',216),('xxhdpi',324),('xxxhdpi',432)]:
    d = f'{res}/mipmap-{name}'; os.makedirs(d, exist_ok=True)
    canvas(out, size).save(f'{d}/ic_launcher_foreground.png', optimize=True)
    canvas(mono, size).save(f'{d}/ic_launcher_monochrome.png', optimize=True)
# Önizleme: daire, squircle ve kare maskeler
from PIL import ImageDraw
big = canvas(out, 432)
sheet = Image.new('RGB', (432*3+80, 472), (240, 240, 240))
for i, shape in enumerate(['circle', 'rounded', 'square']):
    tile = Image.new('RGBA', (432, 432), bg + (255,)); tile.alpha_composite(big)
    # görünür alan 72/108
    m = 72/108*432; o = (432-m)/2
    tile = tile.crop((round(o), round(o), round(o+m), round(o+m))).resize((432, 432), Image.LANCZOS)
    mask = Image.new('L', (432, 432), 0); md = ImageDraw.Draw(mask)
    if shape == 'circle': md.ellipse((0, 0, 431, 431), fill=255)
    elif shape == 'rounded': md.rounded_rectangle((0, 0, 431, 431), 110, fill=255)
    else: md.rectangle((0, 0, 431, 431), fill=255)
    sheet.paste(tile.convert('RGB'), (20 + i*(432+20), 20), mask)
sheet.save(preview)
