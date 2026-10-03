"""
Builds the app's UI font from Google Sans Flex (SIL Open Font License 1.1).

The upstream file is a 4 MB variable font with six axes. The TV app targets 1GB boxes, so this
script derives three small static instances (Regular 400, Medium 500, Bold 700 at the default
width and optical size), keeps only Latin text, and renames the family to "AlterSub Sans": Google's
trademark notes ask that Modified Versions not present themselves as Google Sans.

Usage (needs `pip install fonttools`):
    python tools/build_app_font.py path/to/GoogleSansFlex[GRAD,ROND,opsz,slnt,wdth,wght].ttf

Upstream: https://github.com/google/fonts/tree/main/ofl/googlesansflex
"""
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
FAMILY = "AlterSub Sans"
WEIGHTS = {400: "Regular", 500: "Medium", 700: "Bold"}

# Google Fonts' "latin" range: ASCII, Latin-1, common punctuation, euro, trademark, minus
LATIN = (
    "U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,"
    "U+2000-206F,U+2074,U+20AC,U+2122,U+2191,U+2193,U+2212,U+2215,U+FEFF,U+FFFD"
)


def rename(font: TTFont, style: str) -> None:
    names = {
        1: FAMILY if style in ("Regular", "Bold") else f"{FAMILY} {style}",
        2: style if style in ("Regular", "Bold") else "Regular",
        3: f"{FAMILY.replace(' ', '')}-{style}",
        4: f"{FAMILY} {style}",
        6: f"{FAMILY.replace(' ', '')}-{style}",
        10: "Static Latin subset of Google Sans Flex (SIL OFL 1.1), built for AlterSub.",
        16: FAMILY,
        17: style,
    }
    table = font["name"]
    for name_id, value in names.items():
        table.setName(value, name_id, 3, 1, 0x409)
        table.setName(value, name_id, 1, 0, 0)
    # Variable-font naming records no longer apply to a static instance
    table.names = [n for n in table.names if n.nameID < 256 or n.nameID in names]


def build(source: Path) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for weight, style in WEIGHTS.items():
        font = TTFont(source)
        static = instancer.instantiateVariableFont(
            font,
            {"wght": weight, "wdth": 100, "opsz": 18, "GRAD": 0, "ROND": 0, "slnt": 0},
            static=True,
        )

        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        options.hinting = False
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=subset.parse_unicodes(LATIN))
        subsetter.subset(static)

        rename(static, style)
        static["OS/2"].usWeightClass = weight
        out = OUT_DIR / f"app_sans_{style.lower()}.ttf"
        static.save(out)
        print(f"{out.relative_to(ROOT)}: {out.stat().st_size:,} bytes")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    build(Path(sys.argv[1]))
