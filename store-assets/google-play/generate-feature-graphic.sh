#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source_image="$script_dir/generated/feature-graphic-background-source.png"
japanese_output_image="$script_dir/ja-JP/feature-graphic.png"
english_output_image="$script_dir/en-US/feature-graphic.png"
font_path="${FOLDLYTICS_STORE_FONT:-/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc}"

if [[ ! -f "$source_image" ]]; then
    echo "Generated feature graphic background not found: $source_image" >&2
    exit 1
fi
if [[ ! -f "$font_path" ]]; then
    echo "Store asset font not found: $font_path" >&2
    exit 1
fi

mkdir -p "$(dirname "$japanese_output_image")" "$(dirname "$english_output_image")"

render_feature_graphic() {
    local output_image="$1"
    local tagline="$2"
    local tagline_point_size="$3"

    magick "$source_image" \
        -resize 1024x500! \
        -fill '#C44E00' \
        -draw "roundrectangle 148,128 157,360 5,5" \
        -font "$font_path" \
        -fill '#10213C' \
        -gravity NorthWest \
        -pointsize 46 \
        -annotate +178+126 'Foldlytics' \
        -pointsize "$tagline_point_size" \
        -interline-spacing 8 \
        -annotate +178+208 "$tagline" \
        -background '#D7E3FF' \
        -alpha remove \
        -alpha off \
        -strip \
        "PNG24:$output_image"
}

render_feature_graphic \
    "$japanese_output_image" \
    $'折りたたみスマホ、\nどのくらい開いて\n使っていますか？' \
    28

render_feature_graphic \
    "$english_output_image" \
    $'How much do you use\nthe inner display?' \
    27
