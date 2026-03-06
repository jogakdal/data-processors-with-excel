package io.github.jogakdal.tbeg

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * 피벗 테이블 기능 테스트
 */
class PivotTableTest {

    @Test
    fun `rowHeaderCaption should be preserved from template`() {
        // Given: 템플릿과 데이터 준비
        val template = javaClass.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("Template not found")

        val data = mapOf(
            "title" to "테스트",
            "date" to "2024-01-06",
            "employees" to listOf(
                mapOf("name" to "황용호", "position" to "부장", "salary" to 8000),
                mapOf("name" to "홍용호", "position" to "과장", "salary" to 6500),
                mapOf("name" to "한용호", "position" to "대리", "salary" to 4500)
            )
        )

        // When: Excel 생성
        val generator = ExcelGenerator()
        val bytes = generator.generate(template, data)

        // Then: 피벗 테이블의 rowHeaderCaption 확인
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
            var foundCaption: String? = null
            var foundPivotTableName: String? = null

            for (sheetIndex in 0 until workbook.numberOfSheets) {
                val sheet = workbook.getSheetAt(sheetIndex) as? org.apache.poi.xssf.usermodel.XSSFSheet
                val pivotTables = sheet?.pivotTables ?: continue

                for (pt in pivotTables) {
                    val def = pt.ctPivotTableDefinition
                    // rowHeaderCaption이 있는 피벗 테이블을 찾음
                    if (def.rowHeaderCaption != null) {
                        foundCaption = def.rowHeaderCaption
                        foundPivotTableName = def.name
                    }
                }
            }
            assertNotNull(foundCaption, "rowHeaderCaption이 설정되어야 합니다")
            assertEquals("직급", foundCaption, "rowHeaderCaption이 '직급'이어야 합니다")
        }

        generator.close()
    }
}
