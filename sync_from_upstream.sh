#!/usr/bin/env bash
#
# sync_from_upstream.sh
# kotlin-common-library의 master 브랜치에서 TBEG 소스를 추출하여 패키지명을 변환합니다.
# upstream이 어떤 브랜치에 체크아웃되어 있든 master 브랜치의 파일만 가져옵니다.
#
# 사용법:
#   ./sync_from_upstream.sh [upstream_path]
#
# upstream_path 미지정 시 기본값: ../kotlin/kotlin-common-library
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPSTREAM="${1:-$SCRIPT_DIR/../kotlin/kotlin-common-library}"
BRANCH="origin/master"
LAST_SYNC_FILE="$SCRIPT_DIR/.last_sync_commit"

# upstream 내 상대 경로
TBEG_REL="modules/report/template-based-excel-generator"
CORE_VP_REL="modules/core/common-core/src/main/kotlin/com/hunet/common/lib/VariableProcessor.kt"

# 대상 경로
TARGET="$SCRIPT_DIR/modules/tbeg"

# -------------------------------------------------------------------
# 검증
# -------------------------------------------------------------------
if [[ ! -d "$UPSTREAM/.git" ]]; then
    echo "오류: upstream이 git 저장소가 아닙니다: $UPSTREAM"
    exit 1
fi

# remote master 최신 정보 가져오기
echo "upstream remote에서 최신 master 정보를 가져오는 중..."
if ! git -C "$UPSTREAM" fetch origin master --quiet 2>/dev/null; then
    echo "경고: remote fetch 실패 — 로컬 origin/master 캐시를 사용합니다"
fi

# origin/master 브랜치 존재 확인
if ! git -C "$UPSTREAM" rev-parse --verify "$BRANCH" &>/dev/null; then
    echo "오류: upstream에 $BRANCH 브랜치가 없습니다"
    exit 1
fi

# -------------------------------------------------------------------
# 변경 감지 (커밋 해시 비교)
# -------------------------------------------------------------------
CURRENT_HASH=$(git -C "$UPSTREAM" log "$BRANCH" -1 --format=%H -- "$TBEG_REL" "$CORE_VP_REL")

if [[ -f "$LAST_SYNC_FILE" ]]; then
    LAST_HASH=$(cat "$LAST_SYNC_FILE")
    if [[ "$CURRENT_HASH" == "$LAST_HASH" ]]; then
        echo "=== upstream master에 변경 사항 없음 (해시: ${CURRENT_HASH:0:8}) ==="
        exit 0
    fi
    echo "변경 감지: ${LAST_HASH:0:8} → ${CURRENT_HASH:0:8}"
    echo ""
    echo "변경된 파일 목록:"
    git -C "$UPSTREAM" diff --name-only "$LAST_HASH".."$CURRENT_HASH" -- "$TBEG_REL" "$CORE_VP_REL" | while read -r f; do
        echo "  $f"
    done
    echo ""
else
    echo "첫 동기화 (해시: ${CURRENT_HASH:0:8})"
fi

echo "=== TBEG 소스 동기화 시작 (upstream $BRANCH 브랜치) ==="
echo "  upstream: $UPSTREAM"
echo "  대상: $TARGET"

# -------------------------------------------------------------------
# 임시 디렉토리
# -------------------------------------------------------------------
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

UPSTREAM_DIR="$TMPDIR/upstream"
mkdir -p "$UPSTREAM_DIR"

# -------------------------------------------------------------------
# 1) master 브랜치에서 TBEG 파일 추출
# -------------------------------------------------------------------
echo "[1/8] master 브랜치에서 TBEG 파일 추출..."
git -C "$UPSTREAM" archive "$BRANCH" -- "$TBEG_REL" | tar -x -C "$UPSTREAM_DIR"
TBEG_SRC="$UPSTREAM_DIR/$TBEG_REL"

# -------------------------------------------------------------------
# 2) master 브랜치에서 VariableProcessor 추출
# -------------------------------------------------------------------
echo "[2/8] master 브랜치에서 VariableProcessor 추출..."
git -C "$UPSTREAM" archive "$BRANCH" -- "$CORE_VP_REL" | tar -x -C "$UPSTREAM_DIR"
CORE_SRC="$UPSTREAM_DIR/modules/core/common-core"

# -------------------------------------------------------------------
# 3) TBEG src/ 및 문서 복사
# -------------------------------------------------------------------
echo "[3/8] TBEG src/ 및 문서 복사..."
cp -R "$TBEG_SRC/src" "$TMPDIR/src"

if [[ -d "$TBEG_SRC/manual" ]]; then
    cp -R "$TBEG_SRC/manual" "$TMPDIR/manual"
fi
if [[ -f "$TBEG_SRC/README.md" ]]; then
    cp "$TBEG_SRC/README.md" "$TMPDIR/README.md"
fi
if [[ -f "$TBEG_SRC/DEVELOPMENT.md" ]]; then
    cp "$TBEG_SRC/DEVELOPMENT.md" "$TMPDIR/DEVELOPMENT.md"
fi

# -------------------------------------------------------------------
# 4) common-core 유틸리티를 internal 패키지로 복사
# -------------------------------------------------------------------
echo "[4/8] common-core 유틸리티 → internal 패키지로 복사..."
INTERNAL_DEST="$TMPDIR/src/main/kotlin/com/hunet/common/tbeg/internal"
mkdir -p "$INTERNAL_DEST"
cp "$CORE_SRC/src/main/kotlin/com/hunet/common/lib/VariableProcessor.kt" "$INTERNAL_DEST/"

# -------------------------------------------------------------------
# 5) CommonLogger, ImageUtils를 internal 패키지에 생성
# -------------------------------------------------------------------
echo "[5/8] CommonLogger, ImageUtils → internal 패키지에 생성..."

cat > "$INTERNAL_DEST/CommonLogger.kt" << 'COMMONLOGGER_EOF'
package io.github.jogakdal.tbeg.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.full.companionObject

internal fun <R : Any> R.commonLogger(): Lazy<Logger> =
    lazy { LoggerFactory.getLogger(unwrapCompanionClass(this.javaClass).name) }

private fun <T : Any> unwrapCompanionClass(ofClass: Class<T>): Class<*> =
    ofClass.enclosingClass?.takeIf {
        ofClass.enclosingClass.kotlin.companionObject?.java == ofClass
    } ?: ofClass
COMMONLOGGER_EOF

cat > "$INTERNAL_DEST/ImageUtils.kt" << 'IMAGEUTILS_EOF'
package io.github.jogakdal.tbeg.internal

internal fun ByteArray.detectImageType(): String = when {
    size < 4 -> "PNG"
    isPng() -> "PNG"
    isJpeg() -> "JPEG"
    isGif() -> "GIF"
    isBmp() -> "BMP"
    else -> "PNG"
}

private fun ByteArray.isPng(): Boolean =
    size >= 4 &&
    this[0] == 0x89.toByte() && this[1] == 0x50.toByte() &&
    this[2] == 0x4E.toByte() && this[3] == 0x47.toByte()

private fun ByteArray.isJpeg(): Boolean =
    size >= 3 &&
    this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private fun ByteArray.isGif(): Boolean =
    size >= 4 &&
    this[0] == 0x47.toByte() && this[1] == 0x49.toByte() &&
    this[2] == 0x46.toByte() && this[3] == 0x38.toByte()

private fun ByteArray.isBmp(): Boolean =
    size >= 2 &&
    this[0] == 0x42.toByte() && this[1] == 0x4D.toByte()
IMAGEUTILS_EOF

# -------------------------------------------------------------------
# 6) .kt, .java 파일 패키지명 치환
# -------------------------------------------------------------------
echo "[6/8] Kotlin/Java 파일 패키지명 치환..."

find "$TMPDIR/src" -type f \( -name "*.kt" -o -name "*.java" \) | while read -r f; do
    # 패키지명 치환 (순서 중요: 더 구체적인 것부터)
    sed -i '' \
        -e 's/com\.hunet\.common\.tbeg/io.github.jogakdal.tbeg/g' \
        -e 's/com\.hunet\.common\.lib/io.github.jogakdal.tbeg.internal/g' \
        -e 's/import com\.hunet\.common\.logging\.commonLogger/import io.github.jogakdal.tbeg.internal.commonLogger/g' \
        -e 's/import com\.hunet\.common\.util\.detectImageType/import io.github.jogakdal.tbeg.internal.detectImageType/g' \
        -e 's/import com\.hunet\.common\.[a-zA-Z.]*$//' \
        "$f"
done

# VariableProcessor 특별 처리: @Component 제거, internal 가시성 추가
VP_FILE="$INTERNAL_DEST/VariableProcessor.kt"
if [[ -f "$VP_FILE" ]]; then
    sed -i '' \
        -e '/^import org\.springframework\.stereotype\.Component$/d' \
        -e 's/^@Component$//' \
        -e 's/^class VariableProcessor/internal class VariableProcessor/' \
        -e 's/^interface VariableResolverRegistry/internal interface VariableResolverRegistry/' \
        "$VP_FILE"
fi

# -------------------------------------------------------------------
# 7) .md 파일 치환 (패키지명 + Maven 좌표 + Spring prefix)
# -------------------------------------------------------------------
echo "[7/8] 문서 파일 치환..."

find "$TMPDIR" -maxdepth 1 -name "*.md" -type f | while read -r f; do
    sed -i '' \
        -e 's/com\.hunet\.common\.tbeg/io.github.jogakdal.tbeg/g' \
        -e 's/com\.hunet\.common\.lib/io.github.jogakdal.tbeg.internal/g' \
        -e 's/com\.hunet\.common:tbeg/io.github.jogakdal:tbeg/g' \
        -e 's/hunet\.tbeg\b/tbeg/g' \
        "$f"
done

if [[ -d "$TMPDIR/manual" ]]; then
    find "$TMPDIR/manual" -name "*.md" -type f | while read -r f; do
        sed -i '' \
            -e 's/com\.hunet\.common\.tbeg/io.github.jogakdal.tbeg/g' \
            -e 's/com\.hunet\.common\.lib/io.github.jogakdal.tbeg.internal/g' \
            -e 's/com\.hunet\.common:tbeg/io.github.jogakdal:tbeg/g' \
            -e 's/hunet\.tbeg\b/tbeg/g' \
            "$f"
    done
fi

# -------------------------------------------------------------------
# 8) META-INF AutoConfiguration.imports 치환
# -------------------------------------------------------------------
echo "[8/8] META-INF 파일 치환..."

IMPORTS_FILE="$TMPDIR/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if [[ -f "$IMPORTS_FILE" ]]; then
    sed -i '' \
        -e 's/com\.hunet\.common\.tbeg/io.github.jogakdal.tbeg/g' \
        "$IMPORTS_FILE"
fi

# -------------------------------------------------------------------
# 디렉토리 구조 변환
#    com/hunet/common/tbeg → io/github/jogakdal/tbeg
#    com/hunet/common/lib  → io/github/jogakdal/tbeg/internal (이미 위에서 복사)
# -------------------------------------------------------------------
echo "디렉토리 구조 변환..."

for base in \
    "$TMPDIR/src/main/kotlin" \
    "$TMPDIR/src/test/kotlin" \
    "$TMPDIR/src/test/java"; do

    OLD_PKG_DIR="$base/com/hunet/common/tbeg"
    NEW_PKG_DIR="$base/io/github/jogakdal/tbeg"

    if [[ -d "$OLD_PKG_DIR" ]]; then
        mkdir -p "$(dirname "$NEW_PKG_DIR")"
        # internal 서브디렉토리가 이미 있을 수 있으므로 merge
        if [[ -d "$NEW_PKG_DIR" ]]; then
            cp -R "$OLD_PKG_DIR"/* "$NEW_PKG_DIR"/
        else
            cp -R "$OLD_PKG_DIR" "$NEW_PKG_DIR"
        fi
        rm -rf "$base/com"
    fi
done

# -------------------------------------------------------------------
# 대상에 반영
# -------------------------------------------------------------------
echo "=== 결과를 $TARGET 에 반영 ==="

# src/ 동기화 (기존 src 제거 후 교체)
rm -rf "$TARGET/src"
cp -R "$TMPDIR/src" "$TARGET/src"

# manual/ → manual/ko/ 동기화 (다국어 구조: 한국어 원본은 ko/ 하위에 배치)
if [[ -d "$TMPDIR/manual" ]]; then
    rm -rf "$TARGET/manual/ko"
    mkdir -p "$TARGET/manual/ko"
    cp -R "$TMPDIR/manual"/* "$TARGET/manual/ko"/
fi

# README.md → README.ko.md, DEVELOPMENT.md → DEVELOPMENT.ko.md
# (기본 언어가 English이므로 한국어 원본은 .ko.md 접미사)
if [[ -f "$TMPDIR/README.md" ]]; then
    cp "$TMPDIR/README.md" "$TARGET/README.ko.md"
fi
if [[ -f "$TMPDIR/DEVELOPMENT.md" ]]; then
    cp "$TMPDIR/DEVELOPMENT.md" "$TARGET/DEVELOPMENT.ko.md"
fi

# -------------------------------------------------------------------
# 버전 동기화
# upstream의 moduleVersion.tbeg 값을 이 프로젝트의 VERSION_NAME에 반영한다.
# -------------------------------------------------------------------
UPSTREAM_VERSION=$(git -C "$UPSTREAM" show "$BRANCH":gradle.properties | grep '^moduleVersion\.tbeg=' | cut -d'=' -f2)
if [[ -n "$UPSTREAM_VERSION" ]]; then
    LOCAL_PROPS="$SCRIPT_DIR/gradle.properties"
    CURRENT_VERSION=$(grep '^VERSION_NAME=' "$LOCAL_PROPS" | cut -d'=' -f2)
    if [[ "$CURRENT_VERSION" != "$UPSTREAM_VERSION" ]]; then
        sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$UPSTREAM_VERSION/" "$LOCAL_PROPS"
        echo "버전 동기화: $CURRENT_VERSION → $UPSTREAM_VERSION"
    fi
fi

# -------------------------------------------------------------------
# 커밋 해시 저장
# -------------------------------------------------------------------
echo "$CURRENT_HASH" > "$LAST_SYNC_FILE"

echo "=== 동기화 완료 (해시: ${CURRENT_HASH:0:8}) ==="
echo ""
echo "확인 사항:"
echo "  - $TARGET/src/main/kotlin/io/github/jogakdal/tbeg/"
echo "  - $TARGET/src/main/kotlin/io/github/jogakdal/tbeg/internal/VariableProcessor.kt"
echo "  - $TARGET/src/main/resources/META-INF/spring/"
echo "  - $TARGET/manual/ko/ (한국어 원본)"
echo "  - $TARGET/README.ko.md (한국어 README)"
echo "  - $TARGET/DEVELOPMENT.ko.md (한국어 개발자 가이드)"
