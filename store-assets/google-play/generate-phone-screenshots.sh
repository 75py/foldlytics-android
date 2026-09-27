#!/usr/bin/env bash
set -euo pipefail

# Builds the upload-ready phone screenshots and contact sheets from raw-ja/ and
# raw-en/. The layout lives in phone-template/template.html; the slot list and
# crop regions live in render-phone-screenshots.py.
#
# Requirements: ImageMagick 7 (`magick`) and Python Playwright with Chromium.
# Either install Playwright for python3:
#   python3 -m pip install playwright && python3 -m playwright install chromium
# or have `uv` on PATH, which runs the script with its inline dependencies
# (run `uv run --with playwright python -m playwright install chromium` once).

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
renderer="$script_dir/render-phone-screenshots.py"

if ! command -v magick >/dev/null 2>&1; then
    echo "ImageMagick magick was not found on PATH" >&2
    exit 1
fi

if python3 -c 'import playwright' >/dev/null 2>&1; then
    exec python3 "$renderer" "$@"
elif command -v uv >/dev/null 2>&1; then
    exec uv run --script "$renderer" "$@"
else
    echo "Python Playwright is required." >&2
    echo "Install it with: python3 -m pip install playwright && python3 -m playwright install chromium" >&2
    exit 1
fi
