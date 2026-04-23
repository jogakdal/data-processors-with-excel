package io.github.jogakdal.tbeg

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * ZIP 스트리밍 후처리(`StylesXmlHandler`, `SheetXmlHandler`)의 회귀 방지 테스트.
 *
 * 1.2.4에서 해결한 두 회귀를 재발 방지한다.
 *
 * 1. `cellXf` 자식 요소 순서 OOXML 스키마(`alignment` -> `protection` -> `extLst`) 준수.
 *    과거에는 `<protection>`만 가진 `cellXf`에 `<alignment>`를 `appendChild`해서 순서를 위반했고,
 *    보호된 `.xlsm` 양식에서 Excel이 `styles.xml`을 거부했다.
 * 2. FORMULA 셀 원본 스타일 유지.
 *    과거에는 General 수식 셀에 정수 서식(`#,##0`)을 자동 적용해서, 수식 결과가 문자열인 경우
 *    사용자 의도가 훼손됐다.
 *
 * 샘플 템플릿(`templates/formula_style_preservation_sample.xlsx`)은 두 회귀 조건을 모두 가진다:
 * 보호된 시트 + `protection`만 있는 `cellXf` + General 수식 셀.
 */
class StylesSchemaComplianceTest {

    private lateinit var generator: ExcelGenerator

    @BeforeEach
    fun setUp() {
        generator = ExcelGenerator()
    }

    @AfterEach
    fun tearDown() {
        generator.close()
    }

    @Test
    fun `protection-only cellXf에 NUMERIC 변형이 추가될 때 자식 순서가 OOXML 스키마를 준수한다`() {
        val templateBytes = loadSample()

        val resultBytes = generator.generate(
            template = ByteArrayInputStream(templateBytes),
            data = emptyMap<String, Any>()
        )

        val violations = findCellXfChildOrderViolations(extractZipEntry(resultBytes, "xl/styles.xml"))
        assertTrue(
            violations.isEmpty(),
            "cellXf 자식 요소 순서가 OOXML 스키마를 위반한다: $violations"
        )
    }

    @Test
    fun `General 수식 셀의 스타일 인덱스가 변경되지 않는다`() {
        val templateBytes = loadSample()
        val templateIdx = findFormulaCellStyleIndex(templateBytes)
        assertNotNull(templateIdx, "샘플 템플릿에 FORMULA 셀이 존재해야 한다")

        val resultBytes = generator.generate(
            template = ByteArrayInputStream(templateBytes),
            data = emptyMap<String, Any>()
        )
        val resultIdx = findFormulaCellStyleIndex(resultBytes)

        assertEquals(
            templateIdx, resultIdx,
            "FORMULA 셀의 스타일 인덱스는 변경되지 않아야 한다 (원본 서식 보존)"
        )
    }

    // === helpers ===

    private fun loadSample(): ByteArray =
        StylesSchemaComplianceTest::class.java
            .getResourceAsStream("/templates/formula_style_preservation_sample.xlsx")
            ?.use { it.readBytes() }
            ?: error("샘플 템플릿을 찾을 수 없습니다")

    private fun extractZipEntry(bytes: ByteArray, entryName: String): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            generateSequence { zis.nextEntry }.forEach { entry ->
                if (entry.name == entryName) return zis.readAllBytes()
                zis.closeEntry()
            }
        }
        error("ZIP 엔트리를 찾을 수 없습니다: $entryName")
    }

    private fun findCellXfChildOrderViolations(stylesBytes: ByteArray): List<String> {
        val doc = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(stylesBytes))
        val cellXfs = doc.getElementsByTagName("cellXfs").item(0) as? Element ?: return emptyList()
        val schemaOrder = listOf("alignment", "protection", "extLst")

        return buildList {
            for (i in 0 until cellXfs.childNodes.length) {
                val xf = cellXfs.childNodes.item(i) as? Element ?: continue
                if (xf.tagName != "xf") continue
                val children = collectChildTagNames(xf)
                val expected = children.sortedBy { name ->
                    schemaOrder.indexOf(name).takeIf { it >= 0 } ?: Int.MAX_VALUE
                }
                if (children != expected) add("xf[$i]: $children (expected: $expected)")
            }
        }
    }

    private fun collectChildTagNames(xf: Element): List<String> = buildList {
        for (j in 0 until xf.childNodes.length) {
            val child = xf.childNodes.item(j) as? Element ?: continue
            add(child.tagName)
        }
    }

    private fun findFormulaCellStyleIndex(excelBytes: ByteArray): Short? =
        WorkbookFactory.create(ByteArrayInputStream(excelBytes)).use { wb ->
            wb.getSheetAt(0)
                .flatMap { row -> row.toList() }
                .firstOrNull { it.cellType == CellType.FORMULA }
                ?.cellStyle
                ?.index
        }
}
