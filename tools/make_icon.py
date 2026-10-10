import struct, zlib, sys, math

N = 32          # pixel-art grid
SCALE = 4       # 32 * 4 = 128px, the usual mod icon size

BG      = (24, 18, 20)
BG2     = (34, 24, 27)
BORDER  = (92, 22, 26)
OUTLINE = (40, 6, 8)
RED     = (205, 32, 38)
RED_DK  = (150, 18, 24)
RED_HI  = (255, 110, 110)
WHITE   = (255, 235, 235)
CRACK   = (28, 4, 6)
GOLD    = (255, 178, 30)
GOLD_DK = (176, 104, 8)
FACE    = (255, 240, 200)
HAND    = (60, 30, 6)

px = [[BG for _ in range(N)] for _ in range(N)]

# Background: subtle radial vignette + 1px dark-red frame
for y in range(N):
    for x in range(N):
        d = math.hypot(x - 15.5, y - 15.5) / 22
        px[y][x] = BG2 if d < 0.55 else BG
        if x in (0, N - 1) or y in (0, N - 1):
            px[y][x] = BORDER

# Heart: classic implicit curve, sampled on the grid
def in_heart(x, y):
    u = (x - 15.5) / 11.0
    v = -(y - 14.0) / 11.0
    return (u * u + v * v - 0.62) ** 3 - u * u * v ** 3 * 1.15 <= 0

heart = [[in_heart(x, y) for x in range(N)] for y in range(N)]
for y in range(N):
    for x in range(N):
        if heart[y][x]:
            px[y][x] = RED
            # darker lower-right half for depth
            if (x - 15.5) + (y - 14) * 0.9 > 6:
                px[y][x] = RED_DK
for y in range(N):
    for x in range(N):
        if not heart[y][x] and any(
            0 <= x + dx < N and 0 <= y + dy < N and heart[y + dy][x + dx]
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[y][x] = OUTLINE

# Highlight on the upper-left lobe
for (x, y) in [(9, 9), (10, 9), (9, 10), (8, 10), (8, 11), (9, 11), (11, 8), (12, 8)]:
    if heart[y][x]:
        px[y][x] = RED_HI
for (x, y) in [(9, 9), (10, 9)]:
    px[y][x] = WHITE

# Jagged crack down the middle: one death breaks it for everyone
crack = [(16, 9), (15, 10), (15, 11), (16, 12), (17, 13), (16, 14), (15, 15), (15, 16),
         (16, 17), (17, 18), (16, 19), (16, 20), (15, 21)]
for (x, y) in crack:
    if heart[y][x]:
        px[y][x] = CRACK

# Gold stopwatch in the bottom-right corner (the run timer)
cx, cy, r = 24.0, 24.0, 5.2
for y in range(N):
    for x in range(N):
        d = math.hypot(x - cx, y - cy)
        if d <= r + 0.5:
            px[y][x] = OUTLINE
        if d <= r - 0.5:
            px[y][x] = GOLD
        if d <= r - 1.6:
            px[y][x] = FACE
# crown + button on top
for (x, y) in [(23, 17), (24, 17), (25, 17), (24, 18)]:
    px[y][x] = GOLD_DK
for (x, y) in [(22, 16), (23, 16), (24, 16), (25, 16), (26, 16)]:
    px[y][x] = OUTLINE
# hands pointing up and right
for (x, y) in [(24, 21), (24, 22), (24, 23), (24, 24), (25, 24), (26, 24)]:
    px[y][x] = HAND
px[24][24] = GOLD_DK

# --- write PNG (RGBA, no external libraries) ---
size = N * SCALE
rows = bytearray()
for y in range(size):
    rows.append(0)
    for x in range(size):
        r_, g_, b_ = px[y // SCALE][x // SCALE]
        rows += bytes((r_, g_, b_, 255))

def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)) \
    + chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b"")
open(sys.argv[1], "wb").write(png)
print("wrote", sys.argv[1], size, "x", size)
