#!/usr/bin/env bash
set -euo pipefail

# Renders the Google Play preview videos into preview-video/output/.
# Usage: generate-preview-video.sh [ja] [en] [--frames 1.5,5.0] [--no-audio]
#
# Requirements: ffmpeg, ImageMagick 7 (`magick`), and Python with Playwright
# (Chromium), numpy and scipy. With `uv` on PATH the script runs with its
# inline dependencies instead.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
renderer="$script_dir/render-preview-video.py"

if ! command -v magick >/dev/null 2>&1; then
    echo "magick was not found on PATH" >&2
    exit 1
fi

frames_only=false
for arg in "$@"; do
    case "$arg" in
        --frames|--frames=*) frames_only=true ;;
    esac
done
if [[ "$frames_only" == false ]] && ! command -v ffmpeg >/dev/null 2>&1; then
    echo "ffmpeg was not found on PATH" >&2
    exit 1
fi

if python3 -c 'import playwright, numpy, scipy' >/dev/null 2>&1; then
    exec python3 "$renderer" "$@"
elif command -v uv >/dev/null 2>&1; then
    exec uv run --script "$renderer" "$@"
else
    echo "Python Playwright, numpy and scipy are required." >&2
    echo "Install them with: python3 -m pip install playwright numpy scipy && python3 -m playwright install chromium" >&2
    exit 1
fi
