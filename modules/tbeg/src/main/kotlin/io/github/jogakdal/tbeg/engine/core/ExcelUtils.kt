package io.github.jogakdal.tbeg.engine.core

import io.github.jogakdal.tbeg.internal.detectImageType
import org.apache.poi.poifs.crypt.EncryptionInfo
import org.apache.poi.poifs.crypt.EncryptionMode
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.util.CellAddress
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
 * 예: "B5" -> CellCoord(row=4, col=1)
 *     "$B$5" -> CellCoord(row=4, col=1)  // $ 기호는 무시
 *
 * @param ref 셀 참조 문자열 (예: "A1", "$B$5", "AA100")
 * @return CellCoord(행 인덱스, 열 인덱스) - 0-based
 */
internal fun parseCellRef(ref: String): CellCoord {
    val cleaned = ref.replace("$", "")  // 절대 좌표 기호 제거
    val colPart = cleaned.takeWhile(Char::isLetter).uppercase()
    val rowPart = cleaned.dropWhile(Char::isLetter)
    return CellCoord(row = rowPart.toInt() - 1, col = colPart.toColumnIndex())
}

/**
 * CellCoord를 셀 참조 문자열로 변환한다.
 * 예: CellCoord(row=4, col=1) -> "B5"
 */
internal fun CellCoord.toCellRefString() = toCellRef(row, col)

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
 * CellCoord를 범위 참조 문자열로 변환한다.
 * 예: (CellCoord(0, 0), CellCoord(4, 2)) -> "A1:C5"
 */
internal fun toRangeRef(start: CellCoord, end: CellCoord) =
    "${start.toCellRefString()}:${end.toCellRefString()}"

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

// ========== ZIP 유틸리티 ==========

/**
 * ZipInputStream의 엔트리를 Sequence로 순회한다.
 */
internal fun ZipInputStream.entries(): Sequence<ZipEntry> = generateSequence { nextEntry }

/**
 * ZIP 바이트 배열의 각 엔트리를 변환하여 새 ZIP을 생성한다.
 *
 * @param transform 엔트리 이름과 바이트를 받아 변환된 바이트를 반환한다.
 *   null을 반환하면 해당 엔트리를 제거한다.
 */
internal fun ByteArray.transformZipEntries(
    transform: (entryName: String, bytes: ByteArray) -> ByteArray?
): ByteArray = ByteArrayOutputStream().also { output ->
    ZipInputStream(ByteArrayInputStream(this)).use { zis ->
        ZipOutputStream(output).use { zos ->
            zis.entries().forEach { entry ->
                val transformed = transform(entry.name, zis.readBytes())
                if (transformed != null) {
                    zos.putNextEntry(ZipEntry(entry.name))
                    zos.write(transformed)
                    zos.closeEntry()
                }
            }
        }
    }
}.toByteArray()

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
 *
 * ZIP 엔트리를 스트리밍으로 순회하며 xl/workbook.xml만 수정하고
 * 나머지 엔트리는 그대로 복사한다. OPCPackage를 사용하지 않으므로
 * 대용량 파일에서도 메모리 제한 없이 동작한다.
 */
internal fun ByteArray.removeAbsPath(): ByteArray =
    ByteArrayOutputStream(size).also { out ->
        ZipArchiveOutputStream(out).use { zos ->
            ZipArchiveInputStream(ByteArrayInputStream(this)).use { zis ->
                generateSequence { zis.nextEntry }.forEach { entry ->
                    zos.putArchiveEntry(ZipArchiveEntry(entry.name).apply { time = entry.time })
                    if (entry.name == "xl/workbook.xml") {
                        val xml = zis.readAllBytes().toString(Charsets.UTF_8)
                        zos.write(xml.replace(ALTERNATE_CONTENT_REGEX, "").toByteArray(Charsets.UTF_8))
                    } else {
                        zis.copyTo(zos)
                    }
                    zos.closeArchiveEntry()
                }
            }
        }
    }.toByteArray()

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
