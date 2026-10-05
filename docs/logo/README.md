# AlterSub logo

* `altersub-logo-original.webp`: the original artwork (2000 × 2000), in cyan and cream.
* `altersub-logo.webp`: the logo in the app's yellow (`#FFE500`, `@color/accent`), on the original background. This is the master.
* `altersub-logo-transparent.png`: the yellow logo on a transparent background, cropped to the logo and its glow (880 × 600).

## How the yellow version was made

Every pixel of the original is its background colour (`#0C1723`) plus some amount of the cyan ink (`#2BE1DE`) and some amount
of the cream ink (`#F7F5EB`). The two amounts were found per pixel by least squares, and both inks were replaced by the
yellow at the same combined strength. Anything else in the pixel (the background's texture) was kept. Edges, the glow and
the dimmer parts of the lines therefore keep their original brightness. The transparent cut uses the same strength as its
alpha, over pure yellow.

## Where the app uses it

All crops are taken from the yellow master, in its 2000 px coordinates:

| File | Crop (x, y, width, height) | Size |
|---|---|---|
| `drawable-xhdpi/ic_launcher_banner.webp`, `drawable-xxhdpi/…` | 373, 659, 1214, 683 | 640 × 360, 960 × 540 |
| `mipmap-*/ic_launcher.png` | 520, 540, 920, 920 | 48 to 192 px square |
| `drawable-xhdpi/logo_header.png`, `drawable-xxhdpi/…` (transparent) | 576, 740, 808, 522 | 108 and 162 px tall |
| `raw/web_logo.png` (transparent) | 576, 740, 808, 522 | 120 px tall |
| `raw/web_icon.png` | 520, 540, 920, 920 | 192 px square |

`drawable/ic_notification.xml` is the only drawing: the logo's speech bubble and lines in white, because Android shows
notification icons as one-colour silhouettes.
