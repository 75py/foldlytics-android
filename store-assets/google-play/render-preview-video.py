#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["playwright>=1.40"]
# ///
"""Render the 30-second Google Play preview video for each locale.

The video reuses the phone screenshot slots from render-phone-screenshots.py
(headlines, subtitles, crop regions and raw captures), so the video and the
upload-ready screenshots stay in sync. preview-video/template.html holds the
layout and the animation timeline; this script drives it frame by frame in
headless Chromium and encodes the frames with ffmpeg.

Run through generate-preview-video.sh, which checks the dependencies.
"""

from __future__ import annotations

import argparse
import importlib.util
import pathlib
import shutil
import subprocess
import sys

from playwright.sync_api import sync_playwright

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
TEMPLATE = SCRIPT_DIR / "preview-video" / "template.html"
OUTPUT_DIR = SCRIPT_DIR / "preview-video" / "output"
ICON = SCRIPT_DIR.parent / "app-icon-512.png"
FPS = 30

_spec = importlib.util.spec_from_file_location(
    "render_phone_screenshots", SCRIPT_DIR / "render-phone-screenshots.py"
)
screenshots = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(screenshots)

TEXT = {
    "ja": {
        "hook": ["折りたたみスマホ、", "どのくらい開いて", "使ってる？"],
        "innerLabel": "内側",
        "coverLabel": "外側",
        "endTag": ["折りたたみスマホの使い方を、", "記録して振り返る"],
        "chips": ["利用履歴は端末内で処理", "広告なし"],
    },
    "en": {
        "hook": ["How much do you", "actually unfold", "your phone?"],
        "innerLabel": "Inner",
        "coverLabel": "Cover",
        "endTag": ["See how you really use", "your foldable phone"],
        "chips": ["History stays on your device", "No ads"],
    },
}

# The summary donut in raw-*/01-summary.png, measured on the 2026-09-27
# captures: centre, stroke mid-radius and stroke width in raw pixels. The video
# redraws it over the capture, so recheck these after the summary card moves.
DONUT = {
    "ja": {"cx": 540, "cy": 1136.5, "r": 171.5, "w": 56},
    "en": {"cx": 540, "cy": 1124.5, "r": 171.5, "w": 56},
}
# Inner share of the representative 90-day summary: 259 h 1 min inner and
# 145 h 26 min cover (64%). The redraw stops at this fraction.
DONUT_INNER = (259 * 60 + 1) / ((259 * 60 + 1) + (145 * 60 + 26))


def video_data(locale: str, magick: str) -> dict:
    slots = []
    for name, headline, sub, layers in screenshots.SLOTS[locale]:
        screenshots.validate_inputs(locale, (name, headline, sub, layers), magick)
        layers = screenshots.resolve_layers(locale, layers)
        if name.startswith("01-"):
            layers[0] = {**layers[0], "donut": DONUT[locale]}
        slots.append({"headline": headline, "sub": sub, "layers": layers})
    return {
        "locale": locale,
        "slots": slots,
        "ribbon": screenshots.RIBBON.as_uri(),
        "icon": ICON.as_uri(),
        "donutInner": DONUT_INNER,
        **TEXT[locale],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("locales", nargs="*", metavar="locale", help=f"one of {', '.join(TEXT)} (default: all)")
    parser.add_argument(
        "--frames",
        help="comma-separated times in seconds; write PNG stills instead of a video",
    )
    args = parser.parse_args()
    unknown = [locale for locale in args.locales if locale not in TEXT]
    if unknown:
        parser.error(f"unknown locale: {', '.join(unknown)}")
    locales = args.locales or list(TEXT)

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None and not args.frames:
        sys.exit("ffmpeg was not found on PATH")
    magick = shutil.which("magick")
    if magick is None:
        sys.exit("ImageMagick magick was not found on PATH (used to validate the raw captures)")
    for path in (TEMPLATE, ICON, screenshots.RIBBON):
        if not path.is_file():
            sys.exit(f"Rendering input not found: {path}")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        page = browser.new_page(viewport={"width": 1080, "height": 1920}, device_scale_factor=1)
        for locale in locales:
            page.goto(TEMPLATE.as_uri())
            duration = page.evaluate("data => setup(data)", video_data(locale, magick))
            if args.frames:
                for seconds in (float(value) for value in args.frames.split(",")):
                    page.evaluate(f"render({seconds})")
                    still = OUTPUT_DIR / f"{locale}-{seconds:05.2f}s.png"
                    page.screenshot(path=str(still))
                    print(still)
                continue
            output = OUTPUT_DIR / f"foldlytics-preview-{locale}.mp4"
            partial = output.with_suffix(".partial.mp4")
            encoder = subprocess.Popen(
                [ffmpeg, "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(FPS),
                 "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "16",
                 "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(partial)],
                stdin=subprocess.PIPE,
            )
            try:
                for frame in range(round(duration * FPS)):
                    page.evaluate(f"render({frame / FPS})")
                    encoder.stdin.write(page.screenshot(type="png"))
            finally:
                encoder.stdin.close()
                encoder.wait()
            if encoder.returncode != 0:
                sys.exit(f"ffmpeg failed for {locale}")
            partial.replace(output)
            print(output)
        browser.close()


if __name__ == "__main__":
    main()
