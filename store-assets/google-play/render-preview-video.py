#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = ["playwright>=1.40", "numpy>=1.24", "scipy>=1.10"]
# ///
"""Render the 30-second Google Play preview video for each locale.

The video reuses the phone screenshot slots from render-phone-screenshots.py
(headlines, subtitles, crop regions and raw captures), so the video and the
upload-ready screenshots stay in sync. preview-video/template.html holds the
layout and animation; this script drives it frame by frame in headless
Chromium, encodes the frames with ffmpeg and adds the synthesized soundtrack
from preview-video/soundtrack.py. TIMELINE below is the single source of timing
for both the animation and the soundtrack.

Run through generate-preview-video.sh, which checks the dependencies.
"""

from __future__ import annotations

import argparse
import importlib.util
import pathlib
import shutil
import subprocess
import sys
import tempfile

from playwright.sync_api import sync_playwright

SCRIPT_DIR = pathlib.Path(__file__).resolve().parent
TEMPLATE = SCRIPT_DIR / "preview-video" / "template.html"
OUTPUT_DIR = SCRIPT_DIR / "preview-video" / "output"
ICON = SCRIPT_DIR.parent / "app-icon-512.png"
FPS = 30



def _load(name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


screenshots = _load("render_phone_screenshots", SCRIPT_DIR / "render-phone-screenshots.py")
soundtrack = _load("preview_soundtrack", SCRIPT_DIR / "preview-video" / "soundtrack.py")

# Seconds. Scenes follow the screenshot slots; each scene exits during the last
# exitLength seconds of its own slot, before the next scene enters.
SCENE0 = 3.8
SCENE_LENGTH = 3.7
TIMELINE = {
    "duration": 30.0,
    "foldOpen": [0.9, 2.3],
    "ringFill": [2.1, 3.2],
    "hookEnd": SCENE0,
    "scene0": SCENE0,
    "sceneLength": SCENE_LENGTH,
    "exitLength": 0.35,
    # Filled in per locale from the number of slots (end card after the last scene).
    "endStart": None,
}
LOUDNESS = "loudnorm=I=-16:TP=-1.5:LRA=11"

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


def timeline_for(scene_count: int) -> dict:
    end_start = SCENE0 + SCENE_LENGTH * scene_count + 0.05
    # The last end-card chips finish entering 1.7 seconds after endStart.
    if end_start + 1.7 > TIMELINE["duration"]:
        sys.exit("Too many screenshot slots for the 30-second preview; shorten the scenes")
    return {**TIMELINE, "endStart": end_start}


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
        "timeline": timeline_for(len(slots)),
        **TEXT[locale],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("locales", nargs="*", metavar="locale", help=f"one of {', '.join(TEXT)} (default: all)")
    parser.add_argument(
        "--frames",
        help="comma-separated times in seconds; write PNG stills instead of a video",
    )
    parser.add_argument("--no-audio", action="store_true", help="encode the video without the soundtrack")
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
            data = video_data(locale, magick)
            duration = page.evaluate("data => setup(data)", data)
            if args.frames:
                for seconds in (float(value) for value in args.frames.split(",")):
                    page.evaluate(f"render({seconds})")
                    still = OUTPUT_DIR / f"{locale}-{seconds:05.2f}s.png"
                    page.screenshot(path=str(still))
                    print(still)
                continue
            output = OUTPUT_DIR / f"foldlytics-preview-{locale}.mp4"
            partial = output.with_suffix(".video.mp4")
            try:
                encoder = subprocess.Popen(
                    [ffmpeg, "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(FPS),
                     "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "16",
                     "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(partial)],
                    stdin=subprocess.PIPE,
                )
                broken_pipe = False
                try:
                    for frame in range(round(duration * FPS)):
                        page.evaluate(f"render({frame / FPS})")
                        encoder.stdin.write(page.screenshot(type="png"))
                except BrokenPipeError:
                    broken_pipe = True
                finally:
                    try:
                        encoder.stdin.close()
                    except BrokenPipeError:
                        broken_pipe = True
                    encoder.wait()
                if broken_pipe or encoder.returncode != 0:
                    sys.exit(f"ffmpeg failed for {locale} (exit {encoder.returncode})")
                if args.no_audio:
                    partial.replace(output)
                else:
                    add_soundtrack(ffmpeg, partial, output, data["timeline"], len(data["slots"]))
                print(output)
            finally:
                partial.unlink(missing_ok=True)
        browser.close()


def add_soundtrack(ffmpeg: str, video: pathlib.Path, output: pathlib.Path, timeline: dict,
                   scene_count: int) -> None:
    with tempfile.TemporaryDirectory(prefix="foldlytics-preview-audio.") as work:
        wav = pathlib.Path(work) / "soundtrack.wav"
        soundtrack.write_soundtrack(wav, timeline, scene_count)
        staged = pathlib.Path(work) / output.name
        subprocess.run(
            [ffmpeg, "-y", "-loglevel", "error", "-i", str(video), "-i", str(wav),
             "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-af", LOUDNESS, "-ar", "44100",
             "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(staged)],
            check=True,
        )
        shutil.move(staged, output)


if __name__ == "__main__":
    main()
