#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="$root_dir/assets/demo-catalog"

if [[ -n "${DEMO_SOURCE_DIR:-}" ]]; then
  source_dir="$DEMO_SOURCE_DIR"
else
  common_git_dir="$(git -C "$root_dir" rev-parse --git-common-dir)"
  if [[ "$common_git_dir" != /* ]]; then
    common_git_dir="$root_dir/$common_git_dir"
  fi
  source_dir="$(cd "$(dirname "$common_git_dir")" && pwd)/Pic"
fi

catalog_source="$source_dir/530cd765-4340-4b78-a3f2-95bb3a4fb80d.png"
guide_source="$source_dir/32b3f736-c48a-446d-a800-230ee2d91579.png"

command -v sips >/dev/null 2>&1 || {
  printf 'ERROR: sips is required to crop demo assets\n' >&2
  exit 1
}
[[ -f "$catalog_source" ]] || {
  printf 'ERROR: catalog source image not found: %s\n' "$catalog_source" >&2
  exit 1
}
[[ -f "$guide_source" ]] || {
  printf 'ERROR: AI guide source image not found: %s\n' "$guide_source" >&2
  exit 1
}

mkdir -p "$output_dir"

# sips treats an all-zero crop offset as "center" on macOS. A one-pixel
# horizontal offset selects the intended top-left cell deterministically.
sips --cropToHeightWidth 380 515 --cropOffset 0 1 \
  "$catalog_source" --out "$output_dir/labradorite-8mm.png" >/dev/null
sips --cropToHeightWidth 380 515 --cropOffset 0 530 \
  "$catalog_source" --out "$output_dir/garnet-8mm.png" >/dev/null
sips --cropToHeightWidth 380 515 --cropOffset 395 0 \
  "$catalog_source" --out "$output_dir/fluorite-8mm.png" >/dev/null
sips -Z 512 "$guide_source" --out "$output_dir/ai-water-guide.png" >/dev/null

printf 'Generated demo catalog assets in %s\n' "$output_dir"
