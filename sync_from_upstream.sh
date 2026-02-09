#!/usr/bin/env bash
#
# sync_from_upstream.sh
# kotlin-common-library에서 TBEG 소스를 당겨와 패키지명을 변환합니다.
#
# 사용법:
#   ./sync_from_upstream.sh [upstream_path]
#
# upstream_path 미지정 시 기본값: ../kotlin/kotlin-common-library
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPSTREAM="${1:-$SCRIPT_DIR/../kotlin/kotlin-common-library}"

# 원본 경로
TBEG_SRC="$UPSTREAM/modules/report/template-based-excel-generator"
CORE_SRC="$UPSTREAM/modules/core/common-core"

# 대상 경로
TARGET="$SCRIPT_DIR/modules/tbeg"

# -------------------------------------------------------------------
# 검증
# -------------------------------------------------------------------
if [[ ! -d "$TBEG_SRC" ]]; then
    echo "오류: 원본 TBEG 경로를 찾을 수 없습니다: $TBEG_SRC"
    exit 1
fi
if [[ ! -f "$CORE_SRC/src/main/kotlin/com/hunet/common/lib/VariableProcessor.kt" ]]; then
    echo "오류: VariableProcessor.kt를 찾을 수 없습니다: $CORE_SRC"
    exit 1
fi

echo "=== TBEG 소스 동기화 시작 ==="
echo "  원본: $TBEG_SRC"
echo "  대상: $TARGET"

# -------------------------------------------------------------------
# 임시 디렉토리
# -------------------------------------------------------------------
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

# -------------------------------------------------------------------
# 1) TBEG src/ 복사
# -------------------------------------------------------------------
echo "[1/7] TBEG src/ 복사..."
cp -R "$TBEG_SRC/src" "$TMPDIR/src"

# -------------------------------------------------------------------
# 2) manual/, README.md, DEVELOPMENT.md 복사
# -------------------------------------------------------------------
echo "[2/7] 문서 복사..."
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
# 3) common-core 유틸리티를 internal 패키지로 복사
# -------------------------------------------------------------------
echo "[3/8] common-core 유틸리티 → internal 패키지로 복사..."
INTERNAL_DEST="$TMPDIR/src/main/kotlin/com/hunet/common/tbeg/internal"
mkdir -p "$INTERNAL_DEST"
cp "$CORE_SRC/src/main/kotlin/com/hunet/common/lib/VariableProcessor.kt" "$INTERNAL_DEST/"

# -------------------------------------------------------------------
# 4) CommonLogger, ImageUtils를 internal 패키지에 생성
# -------------------------------------------------------------------
echo "[4/8] CommonLogger, ImageUtils → internal 패키지에 생성..."

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
# 5) .kt, .java 파일 패키지명 치환
# -------------------------------------------------------------------
echo "[5/8] Kotlin/Java 파일 패키지명 치환..."

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
# 5) .md 파일 치환 (패키지명 + Maven 좌표 + Spring prefix)
# -------------------------------------------------------------------
echo "[6/8] 문서 파일 치환..."

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
# 6) META-INF AutoConfiguration.imports 치환
# -------------------------------------------------------------------
echo "[7/8] META-INF 파일 치환..."

IMPORTS_FILE="$TMPDIR/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
if [[ -f "$IMPORTS_FILE" ]]; then
    sed -i '' \
        -e 's/com\.hunet\.common\.tbeg/io.github.jogakdal.tbeg/g' \
        "$IMPORTS_FILE"
fi

# -------------------------------------------------------------------
# 7) 디렉토리 구조 변환
#    com/hunet/common/tbeg → io/github/jogakdal/tbeg
#    com/hunet/common/lib  → io/github/jogakdal/tbeg/internal (이미 위에서 복사)
# -------------------------------------------------------------------
echo "[8/8] 디렉토리 구조 변환..."

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

# manual/ 동기화
if [[ -d "$TMPDIR/manual" ]]; then
    rm -rf "$TARGET/manual"
    cp -R "$TMPDIR/manual" "$TARGET/manual"
fi

# README.md, DEVELOPMENT.md
if [[ -f "$TMPDIR/README.md" ]]; then
    cp "$TMPDIR/README.md" "$TARGET/README.md"
fi
if [[ -f "$TMPDIR/DEVELOPMENT.md" ]]; then
    cp "$TMPDIR/DEVELOPMENT.md" "$TARGET/DEVELOPMENT.md"
fi

echo "=== 동기화 완료 ==="
echo ""
echo "확인 사항:"
echo "  - $TARGET/src/main/kotlin/io/github/jogakdal/tbeg/"
echo "  - $TARGET/src/main/kotlin/io/github/jogakdal/tbeg/internal/VariableProcessor.kt"
echo "  - $TARGET/src/main/resources/META-INF/spring/"
