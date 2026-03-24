#!/bin/bash
# excel-art 단독 실행 파일 빌드 스크립트

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
FAT_JAR="$SCRIPT_DIR/build/libs/excel-art.jar"
OUTPUT="$SCRIPT_DIR/build/excel-art"
LAUNCHER_TMP=$(mktemp)

# 1. Fat JAR 빌드
echo "Fat JAR 빌드 중..."
cd "$PROJECT_DIR" || exit 1
./gradlew :excel-art:fatJar -q || exit 1

JAR_SIZE=$(wc -c < "$FAT_JAR" | tr -d ' ')

# 2. 런처 스크립트 생성
cat > "$LAUNCHER_TMP" << LAUNCHEREOF
#!/bin/bash
# excel-art — 이미지를 Excel 셀 배경색으로 변환
# 자기 완결형 실행 파일 (셸 스크립트 + JAR 내장)
JAR_SIZE=${JAR_SIZE}
if ! command -v java &>/dev/null; then echo "오류: java가 필요합니다." >&2; exit 1; fi
I=""; S="A1"; O=""; C="256"; P="1"
while [ \$# -gt 0 ]; do case "\$1" in
  -s|--start-cell) S="\$2"; shift 2;; -o|--output) O="\$2"; shift 2;;
  -c|--colors) C="\$2"; shift 2;; -p|--pixel-size) P="\$2"; shift 2;;
  -h|--help) echo "excel-art — 이미지를 Excel 셀 배경색으로 변환"; echo "";
    echo "사용법: excel-art <이미지 파일> [옵션]"; echo "";
    echo "  -s, --start-cell    시작 셀 (기본: A1)";
    echo "  -o, --output        출력 파일 (기본: <이미지명>.xlsx)";
    echo "  -c, --colors        최대 색상 수 (기본: 256, 최대: 64000)";
    echo "  -p, --pixel-size    셀 크기 pt (기본: 1)"; exit 0;;
  -*) echo "알 수 없는 옵션: \$1" >&2; exit 1;;
  *) if [ -z "\$I" ]; then I="\$1"; else echo "알 수 없는 인자: \$1" >&2; exit 1; fi; shift;;
esac; done
if [ -z "\$I" ]; then echo "사용법: excel-art <이미지 파일> [옵션]"; exit 1; fi
O="\${O:-\${I%.*}.xlsx}"
M="\$(cd "\$(dirname "\$0")" && pwd)/\$(basename "\$0")"
D="\${TMPDIR:-/tmp}/excel-art-cache"; K=\$(cksum "\$M" | cut -d' ' -f1); J="\$D/ea-\${K}.jar"
if [ ! -f "\$J" ]; then mkdir -p "\$D"; tail -c "\$JAR_SIZE" "\$M" > "\$J"; fi
java -jar "\$J" "\$I" "\$O" "\$C" "\$P" "\$S"; exit \$?
LAUNCHEREOF

# 3. 런처 + JAR 결합
cat "$LAUNCHER_TMP" "$FAT_JAR" > "$OUTPUT"
chmod +x "$OUTPUT"
rm -f "$LAUNCHER_TMP"

SIZE=$(ls -lh "$OUTPUT" | awk '{print $5}')
echo ""
echo "✓ 빌드 완료: $OUTPUT ($SIZE)"
echo "  사용법: $OUTPUT <이미지 파일> [옵션]"
echo "  도움말: $OUTPUT --help"
