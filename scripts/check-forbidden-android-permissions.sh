#!/usr/bin/env bash
set -euo pipefail

readonly FORBIDDEN_PERMISSIONS=(
    "android.permission.INTERNET"
    "android.permission.ACCESS_NETWORK_STATE"
    "android.permission.FOREGROUND_SERVICE"
)

find_apkanalyzer() {
    local resolved_path
    if resolved_path="$(command -v apkanalyzer 2>/dev/null)"; then
        printf '%s\n' "$resolved_path"
        return 0
    fi

    local sdk_root
    local candidate
    for sdk_root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [[ -n "$sdk_root" ]] || continue

        candidate="$sdk_root/cmdline-tools/latest/bin/apkanalyzer"
        if [[ -x "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return 0
        fi

        for candidate in "$sdk_root"/cmdline-tools/*/bin/apkanalyzer; do
            if [[ -x "$candidate" ]]; then
                printf '%s\n' "$candidate"
                return 0
            fi
        done
    done

    return 1
}

if [[ $# -ne 1 ]]; then
    printf 'Usage: %s <apk-path>\n' "${0##*/}" >&2
    exit 2
fi

readonly apk_path="$1"
if [[ ! -f "$apk_path" ]]; then
    printf 'APK not found: %s\n' "$apk_path" >&2
    exit 2
fi

if ! apkanalyzer_path="$(find_apkanalyzer)"; then
    printf 'apkanalyzer was not found. Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with Command-line Tools installed.\n' >&2
    exit 2
fi
readonly apkanalyzer_path

if ! declared_permissions="$("$apkanalyzer_path" manifest permissions "$apk_path")"; then
    printf 'Failed to read permissions from APK: %s\n' "$apk_path" >&2
    exit 2
fi
readonly declared_permissions="${declared_permissions//$'\r'/}"

forbidden_found=()
for permission in "${FORBIDDEN_PERMISSIONS[@]}"; do
    if grep -Fqx "$permission" <<< "$declared_permissions"; then
        forbidden_found+=("$permission")
    fi
done

if (( ${#forbidden_found[@]} > 0 )); then
    printf 'Forbidden Android permissions found in %s:\n' "$apk_path" >&2
    printf '  %s\n' "${forbidden_found[@]}" >&2
    exit 1
fi

printf 'No forbidden Android permissions found in %s.\n' "$apk_path"
