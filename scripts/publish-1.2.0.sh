#!/usr/bin/env bash
# 发布 PocketGlance 1.2.0：确认版本 → 构建 APK → push → 打 tag → 上传 GitHub Release（含 APK；源码由 tag 自动附带）。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME="$(rg -n '^pocketglance.versionName=' gradle.properties | cut -d= -f2)"
VERSION_CODE="$(rg -n '^pocketglance.versionCode=' gradle.properties | cut -d= -f2)"
TAG="$VERSION_NAME"
APK_NAME="PocketGlance-${TAG}.apk"
OUT_DIR="$ROOT/dist"
APK_OUT="$OUT_DIR/$APK_NAME"

if [[ "$VERSION_NAME" != "1.2.0" ]]; then
  echo "期望 versionName=1.2.0，当前是 $VERSION_NAME — 请先改 gradle.properties" >&2
  exit 1
fi

echo "== git status =="
git status -sb
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "还有未提交改动。请先 commit，再跑本脚本。" >&2
  exit 1
fi

echo "== build release APK (versionName=$VERSION_NAME versionCode=$VERSION_CODE) =="
./gradlew :app:assembleRelease
SRC_APK="$ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
if [[ ! -f "$SRC_APK" ]]; then
  # 部分环境会用 debug 密钥签成 app-release.apk
  SRC_APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
fi
if [[ ! -f "$SRC_APK" ]]; then
  echo "找不到 release APK，回退 assembleDebug…" >&2
  ./gradlew :app:assembleDebug
  SRC_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
fi
mkdir -p "$OUT_DIR"
cp -f "$SRC_APK" "$APK_OUT"
ls -lh "$APK_OUT"

echo "== push main =="
git push -u origin HEAD

echo "== tag $TAG =="
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "本地已有 tag $TAG，跳过创建（如需重打请先 git tag -d $TAG && git push origin :refs/tags/$TAG）"
else
  git tag -a "$TAG" -m "PocketGlance $TAG"
fi
git push origin "refs/tags/$TAG"

echo "== GitHub Release $TAG =="
# 源码 zip/tar 由 GitHub 在 tag 上自动提供；这里只额外挂 APK。
BODY="$(cat <<EOF
PocketGlance ${TAG}（versionCode ${VERSION_CODE}）。

- 内置 87 款 LUT（主题合集分类 / 收藏）
- Pocket 模式 HUD 文案
- 相册返回强制退出机身回放
- SoftAP 卡顿：ACK 仍健康时优先重发 live enable，避免过早拆 UDP

安装包：附件 \`${APK_NAME}\`  
源代码：本 Release 页面的 Source code (zip / tar.gz)
EOF
)"

if gh release view "$TAG" >/dev/null 2>&1; then
  echo "Release $TAG 已存在，更新说明并上传 APK…"
  gh release edit "$TAG" --title "PocketGlance $TAG" --notes "$BODY"
  gh release upload "$TAG" "$APK_OUT" --clobber
else
  gh release create "$TAG" "$APK_OUT" \
    --title "PocketGlance $TAG" \
    --notes "$BODY"
fi

echo
echo "完成："
gh release view "$TAG" --web
echo "URL: $(gh release view "$TAG" --json url -q .url)"
