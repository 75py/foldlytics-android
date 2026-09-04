#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$script_dir/../.." && pwd)"
raw_ja_dir="$script_dir/raw-ja"
raw_en_dir="$script_dir/raw-en"
expected_avd="${FOLDLYTICS_STORE_AVD:-Foldlytics_Pixel_9_Pro_Fold_API_36}"
expected_sdk="36"
remote_root="/sdcard/Download/Foldlytics"
remote_ja_dir="$remote_root/store-screenshots"
remote_en_dir="$remote_root/store-screenshots-en"
work_dir="$(mktemp -d /private/tmp/foldlytics-store-capture.XXXXXX)"
candidate_serial=""
validated_device_serial=""
cleanup_armed=false

adb_bin="$(command -v adb || true)"
magick_bin="$(command -v magick || true)"

cleanup() {
    if [[ "$cleanup_armed" == true && -n "$validated_device_serial" ]]; then
        "$adb_bin" -s "$validated_device_serial" shell rm -rf "$remote_ja_dir" "$remote_en_dir" \
            >/dev/null 2>&1 || true
    fi
    rm -rf "$work_dir"
}
trap cleanup EXIT

if [[ -z "$adb_bin" ]]; then
    echo "adb was not found on PATH" >&2
    exit 1
fi
if [[ -z "$magick_bin" ]]; then
    echo "ImageMagick magick was not found on PATH" >&2
    exit 1
fi

connected_devices=()
while IFS= read -r serial; do
    [[ -n "$serial" ]] && connected_devices+=("$serial")
done < <("$adb_bin" devices | awk '$2 == "device" { print $1 }')

if [[ "${#connected_devices[@]}" -ne 1 ]]; then
    echo "Expected exactly one connected device; refusing to run on a physical or unknown device." >&2
    "$adb_bin" devices >&2
    exit 1
fi

candidate_serial="${connected_devices[0]}"
if [[ "$candidate_serial" != emulator-* ]]; then
    echo "Connected device is not an emulator: $candidate_serial" >&2
    exit 1
fi

device_getprop() {
    "$adb_bin" -s "$candidate_serial" shell getprop "$1" | tr -d '\r'
}

if [[ "$(device_getprop ro.kernel.qemu)" != "1" ]]; then
    echo "Connected emulator did not report ro.kernel.qemu=1" >&2
    exit 1
fi
connected_avd="$(device_getprop ro.boot.qemu.avd_name)"
if [[ "$connected_avd" != "$expected_avd" ]]; then
    echo "Connected AVD is $connected_avd; expected $expected_avd." >&2
    echo "Set FOLDLYTICS_STORE_AVD to use a differently named API 36 foldable AVD." >&2
    exit 1
fi
if [[ "$(device_getprop ro.build.version.sdk)" != "$expected_sdk" ]]; then
    echo "Connected device is not Android API $expected_sdk" >&2
    exit 1
fi
if [[ "$(device_getprop sys.boot_completed)" != "1" ]]; then
    echo "Connected emulator has not completed boot" >&2
    exit 1
fi

validated_device_serial="$candidate_serial"
cleanup_armed=true

"$adb_bin" -s "$validated_device_serial" shell cmd device_state state 2 >/dev/null
"$adb_bin" -s "$validated_device_serial" shell wm size 1080x1920 >/dev/null
"$adb_bin" -s "$validated_device_serial" shell rm -rf "$remote_ja_dir" "$remote_en_dir"

capture_names=(
    01-home-summary
    02-session-details
    03-inner-ratio-trend
    04-open-count-trend
    05-total-app-ranking
    06-drawer
)
preferred_raw_names=(
    01-home-summary
    02-session-details
    03-inner-ratio-trend
    04-open-count-trend
    05-total-app-ranking
    06-drawer
)
raw_names=(
    01-summary
    02-inner-sessions
    03-trends
    04-open-count
    05-app-ranking
    06-on-device
)

mkdir -p "$work_dir/raw-ja" "$work_dir/raw-en"

echo "Running StoreScreenshotCaptureTest on $expected_avd ($validated_device_serial), OPENED, 1080x1920"
(
    cd "$repo_dir"
    ANDROID_SERIAL="$validated_device_serial" ./gradlew --no-daemon :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=com.nagopy.android.foldlytics.ui.StoreScreenshotCaptureTest
)

pull_and_validate() {
    local remote_dir="$1"
    local staging_dir="$2"
    local index
    local capture_name
    local raw_name
    local remote_file
    local staging_file
    local format
    local width
    local height

    for index in "${!capture_names[@]}"; do
        capture_name="${capture_names[$index]}"
        raw_name="${raw_names[$index]}"
        remote_file="$remote_dir/$capture_name.png"
        staging_file="$staging_dir/$raw_name.png"
        "$adb_bin" -s "$validated_device_serial" pull "$remote_file" "$staging_file" >/dev/null
        format="$($magick_bin identify -format '%m' "$staging_file")"
        width="$($magick_bin identify -format '%w' "$staging_file")"
        height="$($magick_bin identify -format '%h' "$staging_file")"
        if [[ "$format" != "PNG" || "$width" != "1080" || "$height" != "1920" ]]; then
            echo "Invalid screenshot metadata for $remote_file: $format ${width}x${height}" >&2
            exit 1
        fi
        echo "$remote_file -> $raw_name.png ($format ${width}x${height})"
    done
}

pull_and_validate "$remote_ja_dir" "$work_dir/raw-ja"
pull_and_validate "$remote_en_dir" "$work_dir/raw-en"

remove_preferred_raw_aliases() {
    local raw_dir="$1"
    local preferred_name

    for preferred_name in "${preferred_raw_names[@]}"; do
        rm -f -- "$raw_dir/$preferred_name.png"
    done
}

remove_preferred_raw_aliases "$raw_ja_dir"
remove_preferred_raw_aliases "$raw_en_dir"

remove_app_ranking_raw_aliases() {
    local raw_dir="$1"

    rm -f -- \
        "$raw_dir/05-inner-app-ranking.png" \
        "$raw_dir/05-total-app-ranking.png"
}

remove_app_ranking_raw_aliases "$raw_ja_dir"
remove_app_ranking_raw_aliases "$raw_en_dir"

for index in "${!raw_names[@]}"; do
    raw_name="${raw_names[$index]}"
    mv "$work_dir/raw-ja/$raw_name.png" "$raw_ja_dir/$raw_name.png"
    mv "$work_dir/raw-en/$raw_name.png" "$raw_en_dir/$raw_name.png"
done

"$script_dir/generate-phone-screenshots.sh"
