#!/usr/bin/env bash
# 按 gradle.properties 发布：构建 APK → push → 打 tag → 上传 GitHub Release。
# 手机端：设置 → 关于 → 检查更新，即可拉取本 Release 的 APK。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME="$(rg -n '^pocketglance.versionName=' gradle.properties | cut -d= -f2)"
VERSION_CODE="$(rg -n '^pocketglance.versionCode=' gradle.properties | cut -d= -f2)"
TAG="$VERSION_NAME"
APK_NAME="PocketGlance-${TAG}.apk"
OUT_DIR="$ROOT/dist"
APK_OUT="$OUT_DIR/$APK_NAME"

echo "== git status =="
git status -sb
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "还有未提交改动。请先 commit，再跑本脚本。" >&2
  exit 1
fi

echo "== build APK (versionName=$VERSION_NAME versionCode=$VERSION_CODE) =="
./gradlew :app:assembleDebug
SRC_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$SRC_APK" ]]; then
  echo "找不到 debug APK" >&2
  exit 1
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
  git tag -a "$TAG" -m "PocketGlance $TAG (versionCode $VERSION_CODE)"
fi
git push origin "refs/tags/$TAG"

echo "== GitHub Release $TAG =="
# 改动说明：优先 RELEASE_NOTES 环境变量 / release-notes.md；否则用最近一条提交里「：」后的摘要。
NOTES_FILE="$ROOT/release-notes.md"
if [[ -n "${RELEASE_NOTES:-}" ]]; then
  CHANGELOG="$RELEASE_NOTES"
elif [[ -f "$NOTES_FILE" ]]; then
  CHANGELOG="$(cat "$NOTES_FILE")"
else
  SUBJECT="$(git log -1 --pretty=%s)"
  # 「发布 x.y.z：改动摘要」→ 取冒号后；否则整句当摘要
  if [[ "$SUBJECT" == *：* ]]; then
    SUMMARY="${SUBJECT#*：}"
  elif [[ "$SUBJECT" == *:* ]]; then
    SUMMARY="${SUBJECT#*:}"
  else
    SUMMARY="$SUBJECT"
  fi
  SUMMARY="$(echo "$SUMMARY" | sed 's/[。．.]*$//')"
  CHANGELOG="- ${SUMMARY}"
fi

BODY="$(cat <<EOF
PocketGlance ${TAG}（versionCode ${VERSION_CODE}）

## 安装
- 附件：\`${APK_NAME}\`
- 设置 → 关于 → 检查更新（需允许安装未知应用）

## 本版改动
${CHANGELOG}

## 源码
本页 Source code (zip / tar.gz)，对应 tag \`${TAG}\`。
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
echo "完成：https://github.com/YPYT1/PocketGlance/releases/tag/$TAG"
echo "其他设备：设置 → 关于 → 检查更新"
