#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["playwright>=1.40"]
# ///
"""Render the Google Play phone screenshots from the raw captures.

Each slot crops regions of a raw 1080 x 1920 capture (or places a widget or
share image) into phone-template/template.html, captures the page with
headless Chromium, and flattens the result with ImageMagick into a 24-bit PNG
without alpha. Contact sheets are rebuilt afterwards.

Run through generate-phone-screenshots.sh, which checks the dependencies.
"""

from __future__ import annotations

import os
import pathlib
import shutil
import subprocess
import sys
import tempfile

try:
    from playwright.sync_api import sync_playwright
except ImportError:  # pragma: no cover - reported by the wrapper script
    sys.exit(
        "Python Playwright is required: pip install playwright && python3 -m playwright install chromium"
    )

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
TEMPLATE = SCRIPT_DIR / "phone-template" / "template.html"
RIBBON = SCRIPT_DIR / "generated" / "feature-graphic-background-source.png"
RAW = {"ja": SCRIPT_DIR / "raw-ja", "en": SCRIPT_DIR / "raw-en"}
OUTPUT = {"ja": SCRIPT_DIR / "ja-JP" / "phone", "en": SCRIPT_DIR / "en-US" / "phone"}
PREVIEW = {
    "ja": SCRIPT_DIR / "previews" / "ja-JP-phone-contact-sheet.png",
    "en": SCRIPT_DIR / "previews" / "en-US-phone-contact-sheet.png",
}

# Cropped panels: 1008 px source regions (4 px wider than the 1000 px app cards
# on each side), 44 px of app-background padding, and a 12 px white frame,
# giving a 1000 px wide panel.
REGION_WIDTH = 1008
PADDING = 44
SCALE = (1000 - 2 * 12 - 2 * PADDING) / REGION_WIDTH
APP_BACKGROUND = "#F9F9FF"
PANEL_TOP = 580
SHARE_TOP = 680


def crop(src: str, regions, *, cap=False, scale=SCALE, top=PANEL_TOP, **extra):
    return {
        "type": "crop",
        "src": src,
        "srcWidth": 1080,
        "regions": regions,
        "scale": scale,
        "top": top,
        "radius": 44,
        "padding": PADDING,
        "background": APP_BACKGROUND,
        "cap": cap,
        **extra,
    }


def image(src: str, width: int, top: int, left: int | None = None):
    layer = {"type": "image", "src": src, "width": width, "top": top}
    if left is not None:
        layer["left"] = left
    return layer


def share_image(src: str, top: int):
    # The share image prints the capture device's manufacturer and model at the
    # top right. Cover it so no device or manufacturer name reaches the listing.
    return {
        "type": "crop",
        "src": src,
        "srcWidth": 1200,
        "regions": [[0, 0, 1200, 675]],
        "scale": 0.8,
        "top": top,
        "radius": 36,
        "padding": 0,
        "background": "#F4F7FB",
        "masks": [[720, 40, 440, 60, "#F4F7FB"]],
    }


# Source coordinates follow the current raw captures (1080 x 1920, 390 dpi).
SLOTS = {
    "ja": [
        ("01-display-time.png", ["外側と内側、", "どっちが多い？"], "折りたたみスマホの使い方を記録",
         [crop("01-summary.png", [[36, 296, 1008, 250], [36, 752, 1008, 846]], cap=True)]),
        ("02-inner-apps.png", ["内側で使う", "アプリは？"], "アプリごとに外側・内側の時間を比較",
         [crop("05-app-ranking.png", [[36, 410, 1008, 460], [36, 1064, 1008, 593]])]),
        ("03-per-opening.png", ["開いたら、", "何分使う？"], "長く使った回は、アプリの内訳まで",
         [crop("02-inner-sessions.png", [[36, 280, 1008, 110], [36, 540, 1008, 925]], cap=True)]),
        ("04-detected-opens.png", ["1日に何回", "開いてる？"], "検出した「開いた」回数の推移",
         [crop("04-open-count.png", [[36, 280, 1008, 920]])]),
        ("05-widget.png", ["ホーム画面で", "さっと確認"], "ウィジェットで内側の割合をチェック",
         [image("07-widget-wide.png", 956, 640), image("07-widget-small.png", 478, 1190)]),
        ("06-share-image.png", ["結果を", "画像でシェア"], "共有は自分で操作したときだけ",
         [share_image("08-share-image.png", SHARE_TOP)]),
    ],
    "en": [
        ("01-display-time.png", ["Cover or", "inner display?"], "See how you really use your foldable",
         [crop("01-summary.png", [[36, 296, 1008, 250], [36, 740, 1008, 840]], cap=True)]),
        ("02-inner-apps.png", ["Which apps get", "the inner display?"], "Compare cover and inner time by app",
         [crop("05-app-ranking.png", [[36, 405, 1008, 455], [36, 1083, 1008, 585]])]),
        ("03-per-opening.png", ["How long do you", "stay unfolded?"], "App breakdowns for your longest sessions",
         [crop("02-inner-sessions.png", [[36, 280, 1008, 100], [36, 548, 1008, 895]], cap=True)]),
        ("04-detected-opens.png", ["How often do", "you unfold?"], "Detected opens over time",
         [crop("04-open-count.png", [[36, 280, 1008, 912]])]),
        ("05-widget.png", ["Check it from", "your home screen"], "See your inner display share in a widget",
         [image("07-widget-wide.png", 956, 600), image("07-widget-small.png", 478, 1250)]),
        ("06-share-image.png", ["Share your", "summary image"], "Only when you choose to share",
         [share_image("08-share-image.png", SHARE_TOP)]),
    ],
}

# Slots whose raw inputs are not captured yet are skipped with a notice instead
# of failing, so the other screenshots can still be regenerated.
OPTIONAL_SLOTS = {("en", "06-share-image.png")}


def resolve_layers(locale: str, layers):
    resolved = []
    for layer in layers:
        path = RAW[locale] / layer["src"]
        if not path.is_file():
            sys.exit(f"Raw capture not found: {path}")
        resolved.append({**layer, "src": path.as_uri()})
    return resolved


def has_inputs(locale: str, slot) -> bool:
    name, _headline, _sub, layers = slot
    missing = [layer["src"] for layer in layers if not (RAW[locale] / layer["src"]).is_file()]
    if missing and (locale, name) in OPTIONAL_SLOTS:
        print(f"Skipping {locale} {name}: missing raw {', '.join(missing)}", file=sys.stderr)
        return False
    return True


def run(*args: str) -> None:
    subprocess.run(args, check=True)


def main() -> None:
    magick = shutil.which("magick")
    if magick is None:
        sys.exit("ImageMagick magick was not found on PATH")
    tmp_root = os.environ.get("TMPDIR", "/tmp")
    with tempfile.TemporaryDirectory(prefix="foldlytics-store-screenshots.", dir=tmp_root) as work, \
            sync_playwright() as playwright:
        work_dir = pathlib.Path(work)
        browser = playwright.chromium.launch()
        page = browser.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
        for locale, slots in SLOTS.items():
            output_dir = OUTPUT[locale]
            output_dir.mkdir(parents=True, exist_ok=True)
            slots = [slot for slot in slots if has_inputs(locale, slot)]
            names = [name for name, *_ in slots]
            for stale in output_dir.glob("*.png"):
                if stale.name not in names:
                    stale.unlink()
            for name, headline, sub, layers in slots:
                page.goto(TEMPLATE.as_uri())
                page.evaluate(
                    "slot => render(slot)",
                    {
                        "locale": locale,
                        "headline": headline,
                        "sub": sub,
                        "ribbon": RIBBON.as_uri(),
                        "layers": resolve_layers(locale, layers),
                    },
                )
                captured = work_dir / f"{locale}-{name}"
                page.screenshot(path=str(captured))
                run(magick, str(captured), "-background", APP_BACKGROUND, "-alpha", "remove",
                    "-alpha", "off", "-strip", f"PNG24:{output_dir / name}")
                print(f"{output_dir / name}")
            PREVIEW[locale].parent.mkdir(parents=True, exist_ok=True)
            run(magick, "montage", "-tile", f"{len(names)}x1", "-geometry", "216x384+0+0",
                "-strip", "-depth", "8", *[str(output_dir / n) for n in names],
                f"PNG24:{PREVIEW[locale]}")
            print(f"{PREVIEW[locale]}")
        browser.close()


if __name__ == "__main__":
    main()
