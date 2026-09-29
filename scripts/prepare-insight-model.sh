#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
destination=insight_model/src/main/assets/usage-insight.gguf
sha=9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031
url=https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf
verify() {
    if command -v sha256sum >/dev/null; then
        [[ "$(sha256sum "$1" | cut -d ' ' -f 1)" == "$sha" ]]
    else
        [[ "$(shasum -a 256 "$1" | cut -d ' ' -f 1)" == "$sha" ]]
    fi
}
if [[ -f "$destination" ]] && verify "$destination"; then
    echo 'Pinned insight model is already prepared.'
    exit 0
fi
mkdir -p insight_model/src/main/assets
curl --fail --location --retry 3 --output "$destination.part" "$url"
verify "$destination.part" || { echo 'Model checksum mismatch' >&2; exit 1; }
mv "$destination.part" "$destination"
echo 'Prepared the fixed model for install-time packaging (639,446,688 bytes).'
