package io.github.jogakdal.tbeg.engine.core

import io.github.jogakdal.tbeg.internal.detectImageType
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellAddress
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackagingURIHelper
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Excel 관련 유틸리티 함수 모음.
 */

// ========== Excel 열 변환 유틸리티 ==========

/**
 * Excel 열 문자를 0-based 숫자로 변환한다.
 * 예: "A" -> 0, "B" -> 1, "Z" -> 25, "AA" -> 26
 */
internal fun String.toColumnIndex() =
    uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

/** 일반 함수 래퍼 (FormulaAdjuster에서 사용) */
@JvmName("columnNameToIndex")
internal fun toColumnIndex(colName: String) = colName.toColumnIndex()

/**
 * 0-based 숫자를 Excel 열 문자로 변환한다.
 * 예: 0 -> "A", 1 -> "B", 25 -> "Z", 26 -> "AA"
 */
internal fun Int.toColumnLetter(): String = buildString {
    generateSequence(this@toColumnLetter + 1) { n -> ((n - 1) / 26).takeIf { it > 0 } }
        .map { n -> 'A' + ((n - 1) % 26) }
        .toList()
        .asReversed()  // 복사 없이 역순 뷰
        .forEach(::append)
}

/** 일반 함수 래퍼 (FormulaAdjuster에서 사용) */
@JvmName("indexToColumnName")
internal fun toColumnLetter(index: Int) = index.toColumnLetter()

/**
 * 셀 참조 문자열을 파싱한다. (절대 좌표 $도 지원)
 * 예: "B5" -> (row=4, col=1)
 *     "$B$5" -> (row=4, col=1)  // $ 기호는 무시
 *
 * @param ref 셀 참조 문자열 (예: "A1", "$B$5", "AA100")
 * @return Pair(행 인덱스, 열 인덱스) - 0-based
 */
internal fun parseCellRef(ref: String): Pair<Int, Int> {
    val cleaned = ref.replace("$", "")  // 절대 좌표 기호 제거
    val colPart = cleaned.takeWhile(Char::isLetter).uppercase()
    val rowPart = cleaned.dropWhile(Char::isLetter)
    val col = colPart.toColumnIndex()
    val row = rowPart.toInt() - 1
    return row to col
}

/**
 * 0-based 행/열 인덱스를 셀 참조 문자열로 변환한다.
 * 예: (row=4, col=1) -> "B5"
 */
internal fun toCellRef(row: Int, col: Int) = "${col.toColumnLetter()}${row + 1}"

/**
 * 0-based 행/열 인덱스를 범위 참조 문자열로 변환한다.
 * 예: (startRow=0, startCol=0, endRow=4, endCol=2) -> "A1:C5"
 */
internal fun toRangeRef(startRow: Int, startCol: Int, endRow: Int, endCol: Int) =
    "${toCellRef(startRow, startCol)}:${toCellRef(endRow, endCol)}"

/**
 * 범위 문자열에서 시트 참조를 분리한다.
 * 예: "'Sheet1'!A1:C3" -> ("Sheet1", "A1:C3")
 *     "A1:C3" -> (null, "A1:C3")
 *     "'시트 이름'!$A$1:$C$3" -> ("시트 이름", "$A$1:$C$3")
 *     "빈범위!A1:B2" -> ("빈범위", "A1:B2")
 *
 * @param range 범위 문자열 (시트 참조 포함 가능)
 * @return Pair(시트 이름 또는 null, 시트 참조 제거된 범위)
 */
internal fun extractSheetReference(range: String): Pair<String?, String> {
    // 작은따옴표로 감싼 시트명!범위 형식
    val quotedPattern = Regex("""^'([^']+)'!(.+)$""")
    quotedPattern.find(range)?.let {
        return it.groupValues[1] to it.groupValues[2]
    }

    // 따옴표 없는 시트명!범위 형식 (한글 포함 다양한 문자 지원)
    val unquotedPattern = Regex("""^([^'!]+)!(.+)$""")
    unquotedPattern.find(range)?.let {
        return it.groupValues[1] to it.groupValues[2]
    }

    return null to range
}

// ========== XML 유틸리티 ==========

/**
 * XML 특수 문자를 이스케이프한다.
 */
internal fun String.escapeXml(): String = this
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

// ========== POI 확장 함수 ==========

/**
 * XSSFWorkbook을 ByteArray로 변환한다.
 * absPath 제거: 템플릿의 원본 경로가 저장되어 있으면 Excel이 파일 열 때
 * 경로 불일치로 인해 "수정됨" 상태가 될 수 있음
 */
internal fun XSSFWorkbook.toByteArray(): ByteArray =
    ByteArrayOutputStream().also { write(it) }.toByteArray().removeAbsPath()

// AlternateContent 요소를 제거하는 정규식 (absPath 포함)
private val ALTERNATE_CONTENT_REGEX = Regex(
    """<mc:AlternateContent>.*?</mc:AlternateContent>""",
    RegexOption.DOT_MATCHES_ALL
)

/**
 * xlsx 바이트 배열에서 workbook.xml의 absPath (원본 파일 경로) 요소를 제거한다.
 * Excel이 파일을 열 때 경로 불일치로 인해 "수정됨" 상태가 되는 것을 방지한다.
 */
internal fun ByteArray.removeAbsPath(): ByteArray {
    return OPCPackage.open(ByteArrayInputStream(this)).use { pkg ->
        val workbookPartName = PackagingURIHelper.createPartName("/xl/workbook.xml")
        pkg.getPart(workbookPartName)?.let { part ->
            val originalXml = part.inputStream.bufferedReader().readText()
            val modifiedXml = originalXml.replace(ALTERNATE_CONTENT_REGEX, "")
            if (originalXml != modifiedXml) {
                part.outputStream.use { it.write(modifiedXml.toByteArray(Charsets.UTF_8)) }
            }
        }
        ByteArrayOutputStream().also { pkg.save(it) }.toByteArray()
    }
}

/**
 * 지정된 위치를 포함하는 병합 영역을 찾습니다.
 */
internal fun Sheet.findMergedRegion(rowIndex: Int, columnIndex: Int): CellRangeAddress? =
    (0 until numMergedRegions)
        .map { getMergedRegion(it) }
        .firstOrNull { it.isInRange(rowIndex, columnIndex) }

/**
 * POI에서 사용하는 이미지 타입 문자열을 반환한다.
 * GIF는 PNG로, BMP는 DIB로 변환된다.
 */
internal fun ByteArray.detectImageTypeForPoi(): String =
    when (val type = detectImageType()) {
        "GIF" -> "PNG"   // POI에서 GIF는 PNG로 처리
        "BMP" -> "DIB"   // POI에서 BMP는 DIB로 처리
        else -> type
    }

// ========== Excel 암호화 유틸리티 ==========

/**
 * Excel 파일 데이터를 암호화한다.
 *
 * @param password 파일을 열 때 필요한 암호
 * @return 암호화된 Excel 파일 데이터
 */
internal fun ByteArray.encryptExcel(password: String): ByteArray =
    POIFSFileSystem().use { fs ->
        EncryptionInfo(EncryptionMode.agile).let { info ->
            info.encryptor.apply {
                confirmPassword(password)
                getDataStream(fs).use { encryptedStream ->
                    encryptedStream.write(this@encryptExcel)
                }
            }
        }
        ByteArrayOutputStream().also { fs.writeFilesystem(it) }.toByteArray()
    }

/**
 * Excel 파일 데이터를 암호화하여 OutputStream에 씁니다.
 *
 * @param password 파일을 열 때 필요한 암호
 * @param output 암호화된 데이터를 쓸 OutputStream
 */
internal fun ByteArray.encryptExcelTo(password: String, output: OutputStream) {
    POIFSFileSystem().use { fs ->
        EncryptionInfo(EncryptionMode.agile).let { info ->
            info.encryptor.apply {
                confirmPassword(password)
                getDataStream(fs).use { encryptedStream ->
                    encryptedStream.write(this@encryptExcelTo)
                }
            }
        }
        fs.writeFilesystem(output)
    }
}

// ========== 워크북 초기 뷰 설정 ==========

/**
 * 워크북이 열릴 때 지정된 시트의 지정된 셀에 포커스가 있도록 설정한다.
 *
 * Excel 파일이 처음 열릴 때:
 * - sheetIndex 시트가 활성화된다
 * - 모든 시트에서 cellAddress 셀이 선택된다
 * - 스크롤 위치가 cellAddress로 설정된다
 */
internal fun Workbook.setInitialView(sheetIndex: Int = 0, cellAddress: String = "A1") {
    if (numberOfSheets == 0) return

    setActiveSheet(sheetIndex)

    val cellAddr = CellAddress(cellAddress)

    for (i in 0 until numberOfSheets) {
        getSheetAt(i).apply {
            isSelected = (i == sheetIndex)
            activeCell = cellAddr
            showInPane(cellAddr.row, cellAddr.column)
        }
    }
}
