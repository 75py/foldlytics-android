#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
japanese_raw_dir="$script_dir/raw-ja"
english_raw_dir="$script_dir/raw-en"
japanese_output_dir="$script_dir/ja-JP/phone"
english_output_dir="$script_dir/en-US/phone"
japanese_preview_file="$script_dir/previews/ja-JP-phone-contact-sheet.png"
english_preview_file="$script_dir/previews/en-US-phone-contact-sheet.png"
font_path="${FOLDLYTICS_STORE_FONT:-/System/Library/Fonts/ヒラギノ角ゴシック W6.ttc}"
work_dir="$(mktemp -d /private/tmp/foldlytics-store-screenshots.XXXXXX)"

resolve_raw_name() {
    local raw_dir="$1"
    shift
    local raw_name

    for raw_name in "$@"; do
        if [[ -f "$raw_dir/$raw_name" ]]; then
            printf '%s\n' "$raw_name"
            return
        fi
    done

    # The last name is a checked-in legacy fallback until an emulator capture replaces it.
    printf '%s\n' "${!#}"
}

japanese_home_raw="$(resolve_raw_name "$japanese_raw_dir" "01-home-summary.png" "01-summary.png")"
japanese_session_raw="$(resolve_raw_name "$japanese_raw_dir" "02-session-details.png" "02-inner-sessions.png")"
japanese_inner_ratio_raw="$(resolve_raw_name "$japanese_raw_dir" "03-inner-ratio-trend.png" "03-trends.png")"
japanese_open_count_raw="$(resolve_raw_name "$japanese_raw_dir" "04-open-count-trend.png" "04-open-count.png")"
japanese_app_raw="$(resolve_raw_name "$japanese_raw_dir" "05-app-ranking.png" "05-total-app-ranking.png" "05-inner-app-ranking.png")"
japanese_drawer_raw="$(resolve_raw_name "$japanese_raw_dir" "06-drawer.png" "06-on-device.png")"
english_home_raw="$(resolve_raw_name "$english_raw_dir" "01-home-summary.png" "01-summary.png")"
english_session_raw="$(resolve_raw_name "$english_raw_dir" "02-session-details.png" "02-inner-sessions.png")"
english_inner_ratio_raw="$(resolve_raw_name "$english_raw_dir" "03-inner-ratio-trend.png" "03-trends.png")"
english_open_count_raw="$(resolve_raw_name "$english_raw_dir" "04-open-count-trend.png" "04-open-count.png")"
english_app_raw="$(resolve_raw_name "$english_raw_dir" "05-app-ranking.png" "05-total-app-ranking.png" "05-inner-app-ranking.png")"
english_drawer_raw="$(resolve_raw_name "$english_raw_dir" "06-drawer.png" "06-on-device.png")"

cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

if [[ ! -f "$font_path" ]]; then
    echo "Store asset font not found: $font_path" >&2
    exit 1
fi

mkdir -p "$japanese_output_dir" "$english_output_dir" "$script_dir/previews"

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
    "$japanese_home_raw" \
    "01-display-time.png" \
    $'外側と内側、それぞれの\n利用時間が分かる' \
    '#0067A5' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "$japanese_session_raw" \
    "02-inner-sessions.png" \
    $'1回の内側画面利用時間が\n分かる' \
    '#0067A5' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "$japanese_inner_ratio_raw" \
    "03-long-term-trends.png" \
    $'使い方の変化を\n週・月・年単位で確認' \
    '#C44E00' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "$japanese_open_count_raw" \
    "04-open-count.png" \
    $'検出した「開いた」回数を\n期間ごとに振り返る' \
    '#0067A5' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "$japanese_app_raw" \
    "05-app-ranking.png" \
    $'画面ごとによく使う\nアプリを比較' \
    '#C44E00' \
    54

render_screenshot \
    "$japanese_raw_dir" \
    "$japanese_output_dir" \
    "$japanese_drawer_raw" \
    "06-on-device.png" \
    $'利用履歴は\n端末内だけに保存' \
    '#0067A5' \
    54

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_home_raw" \
    "01-display-time.png" \
    $'See your cover and inner\ndisplay time' \
    '#0067A5' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_session_raw" \
    "02-inner-sessions.png" \
    $'See inner-display use\nfor each opening' \
    '#0067A5' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_inner_ratio_raw" \
    "03-long-term-trends.png" \
    $'Follow your usage trends\nover weeks and months' \
    '#C44E00' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_open_count_raw" \
    "04-open-count.png" \
    $'Track detected opens\nover time' \
    '#0067A5' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_app_raw" \
    "05-app-ranking.png" \
    $'See which apps you use\non each display' \
    '#C44E00' \
    50

render_screenshot \
    "$english_raw_dir" \
    "$english_output_dir" \
    "$english_drawer_raw" \
    "06-on-device.png" \
    $'Your usage history\nstays on your device' \
    '#0067A5' \
    50

build_contact_sheet() {
    local output_dir="$1"
    local output_file="$2"

    magick montage \
        -font "$font_path" \
        -tile 6x1 \
        -geometry 216x384+0+0 \
        -strip \
        -depth 8 \
        "$output_dir/01-display-time.png" \
        "$output_dir/02-inner-sessions.png" \
        "$output_dir/03-long-term-trends.png" \
        "$output_dir/04-open-count.png" \
        "$output_dir/05-app-ranking.png" \
        "$output_dir/06-on-device.png" \
        "PNG24:$output_file"
}

build_contact_sheet "$japanese_output_dir" "$japanese_preview_file"
build_contact_sheet "$english_output_dir" "$english_preview_file"
