#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
japanese_raw_dir="$script_dir/raw-ja"
english_raw_dir="$script_dir/raw-en"
japanese_output_dir="$script_dir/ja-JP/phone"
english_output_dir="$script_dir/en-US/phone"
font_path="${FOLDLYTICS_STORE_FONT:-/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc}"
work_dir="$(mktemp -d /private/tmp/foldlytics-store-screenshots.XXXXXX)"

cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

if [[ ! -f "$font_path" ]]; then
    echo "Store asset font not found: $font_path" >&2
    exit 1
fi

mkdir -p "$japanese_output_dir" "$english_output_dir"

render_screenshot() {
    local raw_dir="$1"
    local output_dir="$2"
    local raw_name="$3"
    local output_name="$4"
    local headline="$5"
    local accent_color="$6"
    local point_size="$7"
    local scaled="$work_dir/${output_name%.png}-scaled.png"
    local mask="$work_dir/${output_name%.png}-mask.png"
    local rounded="$work_dir/${output_name%.png}-rounded.png"
    local base="$work_dir/${output_name%.png}-base.png"

    magick "$raw_dir/$raw_name" \
        -resize 924x1643! \
        "$scaled"

    magick -size 924x1643 xc:none \
        -fill white \
        -draw "roundrectangle 0,0 923,1642 38,38" \
        "$mask"

    magick "$scaled" "$mask" \
        -alpha off \
        -compose CopyOpacity \
        -composite \
        "$rounded"

    magick -size 1080x1920 "gradient:#E7EEFF-#F8F8FF" \
        -fill '#0067A526' \
        -draw "circle 1035,70 1185,70" \
        -fill '#C44E001C' \
        -draw "circle 20,1895 -145,1895" \
        -fill '#001B3F24' \
        -draw "roundrectangle 84,261 1008,1904 40,40" \
        -fill "$accent_color" \
        -draw "roundrectangle 48,49 58,178 5,5" \
        -font "$font_path" \
        -pointsize "$point_size" \
        -interline-spacing 8 \
        -fill '#131722' \
        -gravity NorthWest \
        -annotate +86+42 "$headline" \
        "$base"

    magick "$base" "$rounded" \
        -geometry +78+249 \
        -compose Over \
        -composite \
        -background '#F8F8FF' \
        -alpha remove \
        -alpha off \
        -strip \
        "PNG24:$output_dir/$output_name"
}

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "01-summary.png" \
    "01-display-time.png" \
    $'外側と内側、それぞれの\n利用時間が分かる' \
    '#0067A5' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "02-trends.png" \
    "02-long-term-trends.png" \
    $'使い方の変化を\n週・月・年単位で確認' \
    '#C44E00' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "03-open-count.png" \
    "03-open-count.png" \
    $'検出した開閉回数を\n期間ごとに振り返る' \
    '#0067A5' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "04-app-ranking.png" \
    "04-app-ranking.png" \
    $'画面ごとによく使う\nアプリを比較' \
    '#C44E00' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "05-on-device.png" \
    "05-on-device.png" \
    $'利用履歴は\n端末内だけに保存' \
    '#0067A5' \
    54

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "01-summary.png" \
    "01-display-time.png" \
    $'See your cover and inner\ndisplay time' \
    '#0067A5' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "02-trends.png" \
    "02-long-term-trends.png" \
    $'Follow your usage trends\nover weeks and months' \
    '#C44E00' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "03-open-count.png" \
    "03-open-count.png" \
    $'Track detected opens\nover time' \
    '#0067A5' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "04-app-ranking.png" \
    "04-app-ranking.png" \
    $'See which apps you use\non each display' \
    '#C44E00' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "05-on-device.png" \
    "05-on-device.png" \
    $'Your usage history\nstays on your device' \
    '#0067A5' \
    50
