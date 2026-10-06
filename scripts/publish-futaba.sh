#!/usr/bin/env bash
# FutabaViewer に同梱する Maven リポジトリを作る。
#
#   scripts/publish-futaba.sh <出力先ディレクトリ> <版>
#   例) scripts/publish-futaba.sh ../futabaviewer/third_party/telephoto-maven 0.20.0-futaba.1
#
# FutabaViewer が使うターゲット(Android / desktop(JVM) / iosArm64 / iosSimulatorArm64)と、
# それらを束ねる共通メタデータだけを出力先へ写す。js / wasm / iosX64 は写さない
# (Gradle は使わないターゲットの成果物を取りに行かないので、.module に名前が残っていても困らない)。
set -euo pipefail

out="${1:?出力先ディレクトリを指定してください}"
version="${2:?版を指定してください(例: 0.20.0-futaba.1)}"
root="$(cd "$(dirname "$0")/.." && pwd)"
staging="$(mktemp -d)"
trap 'rm -rf "$staging"' EXIT

modules=(
  :annotations
  :zoomable
  :flick
  :zoomable-image:sub-sampling-image
  :zoomable-image:core
  :zoomable-image:coil3
)
tasks=()
for m in "${modules[@]}"; do tasks+=("$m:publishToMavenLocal"); done

(cd "$root" && ./gradlew "${tasks[@]}" \
  -PVERSION_NAME="$version" \
  -PsignAllPublications=false \
  -Dmaven.repo.local="$staging" \
  --console=plain -q)

mkdir -p "$out/me/saket/telephoto"
for dir in "$staging"/me/saket/telephoto/*/; do
  name="$(basename "$dir")"
  case "$name" in
    *-js|*-wasm-js|*-iosx64) continue ;;
  esac
  rm -rf "$out/me/saket/telephoto/$name/$version"
  mkdir -p "$out/me/saket/telephoto/$name"
  cp -R "$dir$version" "$out/me/saket/telephoto/$name/"
  # maven-metadata-local.xml は mavenLocal 専用。ファイルリポジトリとしては要らない。
  find "$out/me/saket/telephoto/$name" -name 'maven-metadata-local.xml' -delete
done

echo "Published telephoto $version ($(git -C "$root" rev-parse --short HEAD)) to $out"
