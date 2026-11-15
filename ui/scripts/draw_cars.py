"""Draws the nine car pictures of the customer site: a side view per car type, painted and
trimmed per car class, without a make.

    python3 ui/scripts/draw_cars.py

writes ui/src/assets/cars/<type>-<class>.svg. The pictures are committed; run this only to change
them.
"""

from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src" / "assets" / "cars"

# Per type: body outline, glass, pillars (x), wheel centres, wheel-arch radius, ground,
# door lines, handles, extra parts.
TYPES = {
    "sedan": dict(
        body="M60 240 L50 206 Q48 184 72 178 L168 166 C196 150 230 118 262 112 L392 110 "
        "C420 112 452 146 474 160 L566 172 Q594 178 598 202 L600 226 Q600 240 588 240 "
        "L544 240 A54 54 0 0 0 436 240 L204 240 A54 54 0 0 0 96 240 Z",
        glass="M188 164 C212 148 240 126 266 121 L386 119 C408 121 432 142 452 160 Z",
        pillars=[(316, 328)],
        belt=(178, 166, 474, 161),
        shoulder="M74 186 L470 172 L590 186",
        doors=["M328 166 L332 236", "M454 162 Q446 200 444 236", "M206 168 Q210 200 210 236"],
        handles=[(286, 182), (408, 178)],
        wheels=(150, 490, 240),
        arch=54,
        head="M572 184 Q592 186 596 198 L570 196 Z",
        tail="M52 190 L74 186 L74 198 L54 200 Z",
        sill=(204, 436, 228, 240),
    ),
    "suv": dict(
        body="M56 238 L48 196 L52 128 Q56 108 80 106 L392 102 C418 104 446 132 470 150 "
        "L574 162 Q596 166 598 190 L600 224 Q600 238 586 238 L548 238 A58 58 0 0 0 432 238 "
        "L208 238 A58 58 0 0 0 92 238 Z",
        glass="M76 150 L80 124 Q82 116 92 116 L388 112 C408 114 432 136 452 150 Z",
        pillars=[(184, 198), (316, 328)],
        belt=(70, 152, 470, 150),
        shoulder="M60 168 L470 160 L590 176",
        doors=["M328 152 L332 234", "M456 152 Q448 196 444 234", "M200 152 Q206 196 206 234"],
        handles=[(290, 168), (410, 166)],
        wheels=(150, 490, 238),
        arch=58,
        head="M574 170 Q594 172 597 186 L570 184 Z",
        tail="M50 140 L62 138 L62 172 L50 174 Z",
        sill=(208, 432, 222, 238),
        rails=(110, 380, 96),
        cladding=True,
    ),
    "van": dict(
        body="M50 238 L44 196 L46 100 Q48 76 76 74 L400 72 C432 74 470 118 500 140 "
        "L572 156 Q596 162 598 188 L600 224 Q600 238 586 238 L548 238 A56 56 0 0 0 436 238 "
        "L206 238 A56 56 0 0 0 94 238 Z",
        glass="M68 142 L70 94 Q72 86 82 86 L394 84 C420 86 452 118 476 142 Z",
        pillars=[(160, 172), (284, 296), (384, 396)],
        belt=(62, 144, 492, 142),
        shoulder="M54 160 L496 154 L590 170",
        doors=["M396 144 L398 234", "M484 144 Q474 190 470 234", "M284 144 L284 234"],
        handles=[(300, 160), (448, 160)],
        wheels=(150, 492, 238),
        arch=56,
        head="M576 166 Q594 168 597 182 L572 180 Z",
        tail="M46 108 L58 106 L58 160 L46 162 Z",
        sill=(206, 436, 222, 238),
        rail=(206, 380, 150),
    ),
}

# Per class: paint (light, base, dark), wheel style, rim size, trim colour, lamp glow.
CLASSES = {
    "economy": dict(paint=("#ffb37a", "#f47a2a", "#b4501a"), rim="steel", tyre=42, rim_r=24,
                    trim="#2b3036", glow=False),
    "comfort": dict(paint=("#8ec2ef", "#3b7dc4", "#1f4f86"), rim="alloy", tyre=43, rim_r=28,
                    trim="#2b3036", glow=False),
    "premium": dict(paint=("#5d6675", "#262b33", "#0d0f13"), rim="sport", tyre=44, rim_r=32,
                    trim="#d9dee5", glow=True),
}


def wheel(cx, cy, cls, uid):
    c = CLASSES[cls]
    t, r = c["tyre"], c["rim_r"]
    parts = [
        f'<circle cx="{cx}" cy="{cy}" r="{t}" fill="#1b1d21"/>',
        f'<circle cx="{cx}" cy="{cy}" r="{t - 3}" fill="none" stroke="#2c3036" stroke-width="2"/>',
        f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="url(#rim-{uid})"/>',
    ]
    if c["rim"] == "steel":
        parts.append(f'<circle cx="{cx}" cy="{cy}" r="{r - 6}" fill="#c9ced6" stroke="#9aa1ab" stroke-width="2"/>')
        for k in range(6):
            parts.append(
                f'<circle cx="{cx}" cy="{cy - r + 11}" r="2.6" fill="#7d8590" '
                f'transform="rotate({k * 60} {cx} {cy})"/>')
    else:
        spokes = 5 if c["rim"] == "alloy" else 10
        width = 7 if c["rim"] == "alloy" else 3.4
        colour = "#e4e8ee" if c["rim"] == "alloy" else "#aab2bd"
        for k in range(spokes):
            parts.append(
                f'<rect x="{cx - width / 2}" y="{cy - r + 3}" width="{width}" height="{r - 6}" '
                f'rx="{width / 2}" fill="{colour}" transform="rotate({k * 360 / spokes} {cx} {cy})"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="6" fill="#59616c"/>')
    parts.append(f'<circle cx="{cx}" cy="{cy}" r="2.5" fill="#d0d5dc"/>')
    return "\n    ".join(parts)


def svg(type_, cls):
    t, c = TYPES[type_], CLASSES[cls]
    uid = f"{type_}-{cls}"
    light, base, dark = c["paint"]
    rear, front, axle = t["wheels"]
    ground = axle + c["tyre"]
    rim_dark = "#2a2e35" if c["rim"] == "sport" else "#8a929d"
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 44 640 264">',
        "  <defs>",
        f'    <linearGradient id="paint-{uid}" x1="0" y1="0" x2="0" y2="1">'
        f'<stop offset="0" stop-color="{light}"/><stop offset="0.45" stop-color="{base}"/>'
        f'<stop offset="1" stop-color="{dark}"/></linearGradient>',
        f'    <linearGradient id="glass-{uid}" x1="0" y1="0" x2="1" y2="1">'
        '<stop offset="0" stop-color="#5b7187"/><stop offset="0.55" stop-color="#26323f"/>'
        '<stop offset="1" stop-color="#161d26"/></linearGradient>',
        f'    <radialGradient id="rim-{uid}"><stop offset="0" stop-color="#3a3f47"/>'
        f'<stop offset="1" stop-color="{rim_dark}"/></radialGradient>',
        f'    <radialGradient id="shadow-{uid}"><stop offset="0" stop-color="#0b1220" stop-opacity="0.35"/>'
        '<stop offset="1" stop-color="#0b1220" stop-opacity="0"/></radialGradient>',
        f'    <clipPath id="glass-clip-{uid}"><path d="{t["glass"]}"/></clipPath>',
        "  </defs>",
        f'  <ellipse cx="324" cy="{ground}" rx="300" ry="14" fill="url(#shadow-{uid})"/>',
    ]
    if "rails" in t:
        x1, x2, y = t["rails"]
        out.append(f'  <rect x="{x1}" y="{y}" width="{x2 - x1}" height="7" rx="3.5" fill="#22262c"/>')
    r = t["arch"] - 1
    for cx in (rear, front):
        out.append(f'  <path d="M{cx - r} {axle + 2} A{r} {r} 0 0 1 {cx + r} {axle + 2} Z" fill="#14171b"/>')
    out.append(f'  <path d="{t["body"]}" fill="url(#paint-{uid})"/>')
    if t.get("cladding"):
        r = t["arch"]
        for cx in (rear, front):
            out.append(f'  <path d="M{cx - r - 2} {axle} A{r + 2} {r + 2} 0 0 1 {cx + r + 2} {axle}" '
                       f'fill="none" stroke="#25292f" stroke-width="9"/>')
    x1, x2, y1, y2 = t["sill"]
    out.append(f'  <rect x="{x1}" y="{y1}" width="{x2 - x1}" height="{y2 - y1}" fill="{dark}" opacity="0.55"/>')
    out.append(f'  <path d="{t["glass"]}" fill="url(#glass-{uid})"/>')
    for p1, p2 in t["pillars"]:
        out.append(f'  <rect x="{p1}" y="60" width="{p2 - p1}" height="110" fill="{dark}" '
                   f'clip-path="url(#glass-clip-{uid})"/>')
    # A reflection across the glass.
    belt = t["belt"][1]
    out.append(f'  <path clip-path="url(#glass-clip-{uid})" '
               f'd="M300 {belt - 50} L330 {belt - 50} L282 {belt} L270 {belt} Z" fill="#ffffff" opacity="0.10"/>')
    trim = c["trim"]
    out.append(f'  <path d="{t["glass"]}" fill="none" stroke="{trim}" stroke-width="{3 if c["glow"] else 2}" '
               'stroke-linejoin="round"/>')
    out.append(f'  <path d="{t["shoulder"]}" fill="none" stroke="#ffffff" stroke-opacity="0.35" stroke-width="2.5"/>')
    for d in t["doors"]:
        out.append(f'  <path d="{d}" fill="none" stroke="{dark}" stroke-opacity="0.8" stroke-width="2"/>')
    if "rail" in t:
        x1, x2, y = t["rail"]
        out.append(f'  <line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" stroke="{dark}" stroke-width="3"/>')
    for hx, hy in t["handles"]:
        colour = "#e9edf2" if c["glow"] else dark
        out.append(f'  <rect x="{hx}" y="{hy}" width="26" height="6" rx="3" fill="{colour}"/>')
    head_colour = "#eaf6ff" if c["glow"] else "#fff4d6"
    out.append(f'  <path d="{t["head"]}" fill="{head_colour}" stroke="#9aa3ad" stroke-width="1.5"/>')
    if c["glow"]:
        out.append(f'  <path d="{t["head"]}" fill="none" stroke="#7fd3ff" stroke-width="2.5" opacity="0.9"/>')
    out.append(f'  <path d="{t["tail"]}" fill="#d22f3c"/>')
    for cx in (rear, front):
        out.append("  <g>\n    " + wheel(cx, axle, cls, uid) + "\n  </g>")
    out.append("</svg>\n")
    return "\n".join(out)


for type_ in TYPES:
    for cls in CLASSES:
        (OUT / f"{type_}-{cls}.svg").write_text(svg(type_, cls))
print(f"{len(TYPES) * len(CLASSES)} pictures in {OUT}")
