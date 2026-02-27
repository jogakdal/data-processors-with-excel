package io.github.jogakdal.tbeg.engine.rendering

import io.github.jogakdal.tbeg.engine.core.findMergedRegion
import io.github.jogakdal.tbeg.engine.core.parseCellRef
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFCell
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType

/**
 * XSSF/SXSSF 렌더링 전략의 공통 기능을 제공하는 추상 베이스 클래스.
 *
 * 템플릿 메서드 패턴을 사용하여:
 * - render(): 알고리즘의 골격 정의
 * - 추상 메서드: 서브클래스에서 구현할 확장 포인트
 * - 공통 유틸리티: 셀 값 설정, 위치 조정 등
 */
internal abstract class AbstractRenderingStrategy : RenderingStrategy {
    /**
     * 템플릿을 렌더링한다. (템플릿 메서드)
     *
     * 알고리즘 골격:
     * 1. 워크북 초기화
     * 2. 템플릿 분석
     * 3. 시트별 처리
     * 4. 이미지 삽입
     * 5. 워크북 마무리
     */
    override fun render(
        templateBytes: ByteArray,
        data: Map<String, Any>,
        context: RenderingContext
    ): ByteArray {
        return withWorkbook(templateBytes) { workbook, xssfWorkbook ->
            val blueprint = context.analyzer.analyzeFromWorkbook(xssfWorkbook)
            val imageLocations = mutableListOf<ImageLocation>()

            // 렌더링 전 초기화 (서브클래스 확장 포인트)
            beforeProcessSheets(workbook, blueprint, data, context)

            // 시트별 처리
            blueprint.sheets.forEachIndexed { index, sheetSpec ->
                val sheet = workbook.getSheetAt(index)
                processSheet(sheet, index, sheetSpec, data, imageLocations, context)
            }

            // 이미지 삽입
            insertImages(workbook, imageLocations, data, context)

            // 렌더링 후 마무리 (서브클래스 확장 포인트)
            afterProcessSheets(workbook, context)

            // 워크북을 바이트 배열로 변환
            finalizeWorkbook(workbook)
        }
    }

    // ========== 추상 메서드 (서브클래스 구현 필수) ==========

    /**
     * 워크북을 생성하고 작업을 수행한 후 정리한다.
     * 리소스 관리를 서브클래스에 위임한다.
     */
    protected abstract fun <T> withWorkbook(
        templateBytes: ByteArray,
        block: (workbook: Workbook, xssfWorkbook: XSSFWorkbook) -> T
    ): T

    /**
     * 개별 시트를 처리한다.
     * XSSF는 shiftRows, SXSSF는 스트리밍 방식으로 구현된다.
     */
    protected abstract fun processSheet(
        sheet: Sheet,
        sheetIndex: Int,
        blueprint: SheetSpec,
        data: Map<String, Any>,
        imageLocations: MutableList<ImageLocation>,
        context: RenderingContext
    )

    /**
     * 워크북을 바이트 배열로 변환한다.
     */
    protected abstract fun finalizeWorkbook(workbook: Workbook): ByteArray

    // ========== 훅 메서드 (서브클래스 선택적 오버라이드) ==========

    /**
     * 시트 처리 전 호출된다. (훅 메서드)
     * SXSSF에서 스타일 추출, 시트 내용 클리어 등에 사용된다.
     */
    protected open fun beforeProcessSheets(
        workbook: Workbook,
        blueprint: WorkbookSpec,
        data: Map<String, Any>,
        context: RenderingContext
    ) { }

    /**
     * 시트 처리 후 호출된다. (훅 메서드)
     * 수식 재계산, calcChain 정리, 초기 뷰 설정 등에 사용된다.
     */
    protected open fun afterProcessSheets(
        workbook: Workbook,
        context: RenderingContext
    ) { }

    // ========== 공통 CellContent 처리 ==========

    /**
     * 셀 내용을 처리한다.
     * 모든 CellContent 타입에 대한 공통 처리 로직이다.
     *
     * @return 추가 처리가 필요한 경우 false (서브클래스에서 처리)
     */
    protected fun processCellContent(
        cell: Cell,
        content: CellContent,
        data: Map<String, Any>,
        sheetIndex: Int,
        imageLocations: MutableList<ImageLocation>,
        context: RenderingContext,
        rowOffset: Int = 0,
        colOffset: Int = 0,
        repeatItemIndex: Int = 0
    ): Boolean {
        when (content) {
            is CellContent.Empty -> {
            }

            is CellContent.StaticString -> {
                val evaluated = context.evaluateText(content.value, data)
                cell.setCellValue(evaluated)
            }

            is CellContent.StaticNumber -> {
                cell.setCellValue(content.value)
            }

            is CellContent.StaticBoolean -> {
                cell.setCellValue(content.value)
            }

            is CellContent.Variable -> {
                val evaluated = context.evaluateText(content.originalText, data)
                setCellValue(cell, evaluated)
            }

            is CellContent.ItemField -> {
                val item = data[content.itemVariable]
                val value = context.resolveFieldPath(item, content.fieldPath)
                setCellValue(cell, value)
            }

            is CellContent.Formula -> {
                // 서브클래스에서 추가 처리 필요 (수식 조정 등)
                return false
            }

            is CellContent.FormulaWithVariables -> {
                val substitutedFormula = context.evaluateText(content.formula, data)
                cell.cellFormula = substitutedFormula
            }

            is CellContent.ImageMarker -> {
                processImageMarker(
                    cell, content, sheetIndex, imageLocations,
                    rowOffset, colOffset, repeatItemIndex
                )
            }

            is CellContent.RepeatMarker -> {
                cell.setBlank()
            }

            is CellContent.SizeMarker -> {
                processSizeMarker(cell, content, data, context)
            }
        }
        sanitizeCellXml(cell)
        return true
    }

    /**
     * 컬렉션 크기 마커를 처리한다.
     * ${size(collection)} 또는 =TBEG_SIZE(collection) 패턴을 컬렉션 크기로 치환한다.
     */
    protected fun processSizeMarker(
        cell: Cell,
        content: CellContent.SizeMarker,
        data: Map<String, Any>,
        context: RenderingContext
    ) {
        // 1. 스트리밍 모드: context.collectionSizes에서 가져오기
        // 2. 비스트리밍 모드: data에서 컬렉션 가져와서 크기 계산
        val size = context.collectionSizes[content.collectionName]
            ?: (data[content.collectionName] as? Collection<*>)?.size
            ?: 0

        val originalText = content.originalText

        // ${size(collection)} 패턴 치환
        val dollarSizePattern = Regex(
            """\$\{size\s*\(\s*["'`]?${Regex.escape(content.collectionName)}["'`]?\s*\)}""",
            RegexOption.IGNORE_CASE
        )
        var result = dollarSizePattern.replace(originalText, size.toString())

        // =TBEG_SIZE(collection) 패턴 치환 (수식 또는 문자열로 저장된 경우)
        val tbegSizePattern = Regex(
            """=?TBEG_SIZE\s*\(\s*["'`]?${Regex.escape(content.collectionName)}["'`]?\s*\)""",
            RegexOption.IGNORE_CASE
        )
        result = tbegSizePattern.replace(result, size.toString())

        // 결과가 순수 숫자인 경우 숫자로 저장 (NumberFormatProcessor 적용을 위해)
        val numericValue = result.toLongOrNull() ?: result.toDoubleOrNull()
        if (numericValue != null) {
            setCellValue(cell, numericValue)
        } else {
            setCellValue(cell, result)
        }
    }

    /**
     * 이미지 마커를 처리한다.
     */
    protected fun processImageMarker(
        cell: Cell,
        content: CellContent.ImageMarker,
        sheetIndex: Int,
        imageLocations: MutableList<ImageLocation>,
        rowOffset: Int,
        colOffset: Int,
        repeatItemIndex: Int
    ) {
        // position이 지정된 이미지는 첫 번째 반복에서만 처리 (중복 방지)
        if (content.position != null && repeatItemIndex > 0) {
            cell.setBlank()
            return
        }

        // position 조정 (반복 영역 내 오프셋 적용)
        val adjustedPosition = if (content.position != null && (rowOffset != 0 || colOffset != 0)) {
            adjustPosition(content.position, rowOffset, colOffset)
        } else {
            content.position
        }

        imageLocations.add(
            ImageLocation(
                sheetIndex = sheetIndex,
                imageName = content.imageName,
                position = adjustedPosition,
                markerRowIndex = cell.rowIndex,
                markerColIndex = cell.columnIndex,
                sizeSpec = content.sizeSpec,
                hAlign = cell.cellStyle?.alignment ?: HorizontalAlignment.GENERAL,
                vAlign = cell.cellStyle?.verticalAlignment ?: VerticalAlignment.TOP
            )
        )
        cell.setBlank()
    }

    // ========== 이미지 삽입 ==========

    /**
     * 수집된 이미지 위치에 이미지를 삽입한다.
     */
    protected fun insertImages(
        workbook: Workbook,
        imageLocations: List<ImageLocation>,
        data: Map<String, Any>,
        context: RenderingContext
    ) {
        for (location in imageLocations) {
            val imageBytes = data["image.${location.imageName}"] as? ByteArray
                ?: data[location.imageName] as? ByteArray
                ?: continue

            val sheet = workbook.getSheetAt(location.sheetIndex)
            val markerMergedRegion = sheet.findMergedRegion(location.markerRowIndex, location.markerColIndex)

            context.imageInserter.insertImageWithPosition(
                workbook, sheet, imageBytes,
                location.position,
                location.markerRowIndex, location.markerColIndex,
                location.sizeSpec,
                markerMergedRegion,
                location.hAlign, location.vAlign
            )
        }
    }

    // ========== calcChain 정리 ==========

    /**
     * calcChain을 비웁니다.
     * 반복 확장 후 calcChain이 불일치 상태가 될 수 있어 제거가 필요하다.
     */
    protected fun clearCalcChain(workbook: XSSFWorkbook) {
        workbook.calculationChain?.let { chain ->
            val ctCalcChain = chain.ctCalcChain
            while (ctCalcChain.sizeOfCArray() > 0) {
                ctCalcChain.removeC(0)
            }
        }
    }

    // ========== 숫자 형식 스타일 유틸리티 ==========

    companion object {
        // Excel 내장 숫자 형식 인덱스
        protected const val NUMBER_FORMAT_INTEGER: Short = 3  // #,##0
        protected const val NUMBER_FORMAT_DECIMAL: Short = 4  // #,##0.00
    }

    /**
     * 원본 스타일을 복제하고 Excel 내장 숫자 형식을 적용한 스타일을 반환한다.
     * 정수는 인덱스 3 (#,##0), 소수는 인덱스 4 (#,##0.00)를 사용한다.
     *
     * @param cache 스타일 캐시 맵 (키: "원본스타일인덱스_int" 또는 "원본스타일인덱스_dec")
     * @param workbook 워크북
     * @param originalStyle 원본 스타일
     * @param isInteger 정수 여부
     */
    protected fun getOrCreateNumberStyle(
        cache: MutableMap<String, XSSFCellStyle>,
        workbook: XSSFWorkbook,
        originalStyle: XSSFCellStyle,
        isInteger: Boolean
    ): XSSFCellStyle {
        val cacheKey = "${originalStyle.index}_${if (isInteger) "int" else "dec"}"
        return cache.getOrPut(cacheKey) {
            workbook.createCellStyle().apply {
                cloneStyleFrom(originalStyle)
                dataFormat = if (isInteger) NUMBER_FORMAT_INTEGER else NUMBER_FORMAT_DECIMAL
            }
        }
    }

    // ========== 셀 XML 정리 ==========

    /**
     * XSSFCell의 CTCell XML에서 OOXML 위반 요소를 제거한다.
     *
     * POI가 셀 타입 변경 시 이전 XML 요소를 정리하지 않아
     * `<v>`와 `<is>`가 동시에 존재하는 문제를 수정한다.
     * - inlineStr: `<is>`만 유효하므로 `<v>` 제거
     * - 다른 타입(n, s, b 등): `<v>`만 유효하므로 잔존 `<is>` 제거
     *
     * SXSSF 셀(SXSSFCell)은 XSSFCell이 아니므로 자동 스킵된다.
     */
    protected fun sanitizeCellXml(cell: Cell) {
        val ctCell = (cell as? XSSFCell)?.ctCell ?: return
        if (!ctCell.isSetIs) return

        if (ctCell.t == STCellType.INLINE_STR) {
            if (ctCell.isSetV) ctCell.unsetV()
        } else {
            ctCell.unsetIs()
        }
    }

    // ========== 셀 값 설정 유틸리티 ==========

    /**
     * 셀에 값을 설정한다.
     * 값의 타입에 따라 적절한 메서드를 호출한다.
     */
    protected fun setCellValue(cell: Cell, value: Any?) {
        when (value) {
            null -> cell.setBlank()
            is String -> cell.setCellValue(value)
            is Number -> cell.setCellValue(value.toDouble())
            is Boolean -> cell.setCellValue(value)
            is java.time.LocalDate -> cell.setCellValue(value)
            is java.time.LocalDateTime -> cell.setCellValue(value)
            is java.util.Date -> cell.setCellValue(value)
            else -> cell.setCellValue(value.toString())
        }
    }

    // ========== 위치 조정 유틸리티 ==========

    /**
     * 위치 문자열에 오프셋을 적용한다.
     *
     * @param position 단일 셀(B5) 또는 범위(B5:D10)
     * @param rowOffset 행 오프셋
     * @param colOffset 열 오프셋
     * @return 조정된 위치 문자열
     */
    protected fun adjustPosition(position: String, rowOffset: Int, colOffset: Int): String {
        return if (position.contains(":")) {
            // 범위: B5:D10 -> 각 셀에 오프셋 적용
            val (start, end) = position.split(":")
            "${adjustCellRef(start, rowOffset, colOffset)}:${adjustCellRef(end, rowOffset, colOffset)}"
        } else {
            // 단일 셀: B5 -> 오프셋 적용
            adjustCellRef(position, rowOffset, colOffset)
        }
    }

    /**
     * 셀 참조에 오프셋을 적용한다.
     * 예: B5 + (2, 1) -> C7
     */
    protected fun adjustCellRef(ref: String, rowOffset: Int, colOffset: Int): String {
        val (row, col) = parseCellRef(ref)
        val newRow = row + rowOffset
        val newCol = col + colOffset
        return toColumnName(newCol) + (newRow + 1)
    }

    /**
     * 열 인덱스를 열 이름으로 변환한다.
     * 예: 0 -> A, 25 -> Z, 26 -> AA
     */
    protected fun toColumnName(colIndex: Int): String {
        val sb = StringBuilder()
        var n = colIndex + 1
        while (n > 0) {
            n--
            sb.insert(0, ('A' + (n % 26)))
            n /= 26
        }
        return sb.toString()
    }
}

/**
 * 이미지 위치 정보
 *
 * @param sheetIndex 시트 인덱스
 * @param imageName 이미지 이름 (데이터 맵의 키)
 * @param position 위치 문자열 (단일 셀 B5, 범위 B5:D10, 또는 null)
 * @param markerRowIndex 마커 셀 행 (position이 null일 때 사용)
 * @param markerColIndex 마커 셀 열 (position이 null일 때 사용)
 * @param sizeSpec 이미지 크기 명세
 * @param hAlign 마커 셀의 수평 정렬 (position이 null일 때 적용)
 * @param vAlign 마커 셀의 수직 정렬 (position이 null일 때 적용)
 */
internal data class ImageLocation(
    val sheetIndex: Int,
    val imageName: String,
    val position: String?,
    val markerRowIndex: Int,
    val markerColIndex: Int,
    val sizeSpec: ImageSizeSpec = ImageSizeSpec.FIT_TO_CELL,
    val hAlign: HorizontalAlignment = HorizontalAlignment.GENERAL,
    val vAlign: VerticalAlignment = VerticalAlignment.TOP
)
