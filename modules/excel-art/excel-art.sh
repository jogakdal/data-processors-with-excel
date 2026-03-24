#!/bin/bash
# 이미지를 Excel 셀 배경색으로 변환하는 실행 스크립트
#
# 사용법:
#   ./modules/excel-art/excel-art.sh <이미지 파일> [옵션]
#
# 옵션:
#   -s, --start-cell    시작 셀 (기본: A1, 예: B2, C5)
#   -o, --output        출력 파일 (기본: <이미지명>.xlsx)
#   -c, --colors        최대 색상 수 (기본: 256, 최대: 64000)
#   -p, --pixel-size    셀 크기 pt (기본: 1)
#
# 예:
#   ./modules/excel-art/excel-art.sh logo.png
#   ./modules/excel-art/excel-art.sh logo.png -s B2
#   ./modules/excel-art/excel-art.sh logo.png -s B2 -o logo.xlsx
#   ./modules/excel-art/excel-art.sh logo.png --colors 512 --pixel-size 2

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
FAT_JAR="$SCRIPT_DIR/build/libs/excel-art.jar"

# Fat JAR가 없으면 빌드
if [ ! -f "$FAT_JAR" ]; then
    echo "Fat JAR 빌드 중..."
    cd "$PROJECT_DIR" || exit 1
    ./gradlew :excel-art:fatJar -q
fi

# 인자 파싱
IMAGE_FILE=""
START_CELL="A1"
OUTPUT_FILE=""
MAX_COLORS="256"
PIXEL_SIZE="1"

while [ $# -gt 0 ]; do
    case "$1" in
        -s|--start-cell)
            START_CELL="$2"; shift 2 ;;
        -o|--output)
            OUTPUT_FILE="$2"; shift 2 ;;
        -c|--colors)
            MAX_COLORS="$2"; shift 2 ;;
        -p|--pixel-size)
            PIXEL_SIZE="$2"; shift 2 ;;
        -h|--help)
            echo "사용법: excel-art.sh <이미지 파일> [옵션]"
            echo ""
            echo "옵션:"
            echo "  -s, --start-cell    시작 셀 (기본: A1)"
            echo "  -o, --output        출력 파일 (기본: <이미지명>.xlsx)"
            echo "  -c, --colors        최대 색상 수 (기본: 256, 최대: 64000)"
            echo "  -p, --pixel-size    셀 크기 pt (기본: 1)"
            exit 0 ;;
        -*)
            echo "알 수 없는 옵션: $1" >&2; exit 1 ;;
        *)
            if [ -z "$IMAGE_FILE" ]; then
                IMAGE_FILE="$1"
            else
                echo "알 수 없는 인자: $1" >&2; exit 1
            fi
            shift ;;
    esac
done

if [ -z "$IMAGE_FILE" ]; then
    echo "사용법: excel-art.sh <이미지 파일> [옵션]"
    echo "도움말: excel-art.sh --help"
    exit 1
fi

OUTPUT_FILE="${OUTPUT_FILE:-${IMAGE_FILE%.*}.xlsx}"

java -jar "$FAT_JAR" "$IMAGE_FILE" "$OUTPUT_FILE" "$MAX_COLORS" "$PIXEL_SIZE" "$START_CELL"
