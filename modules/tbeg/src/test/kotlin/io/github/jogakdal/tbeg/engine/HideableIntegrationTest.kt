@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.UnmarkedHidePolicy
import io.github.jogakdal.tbeg.simpleDataProvider
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerValidationException
import io.github.jogakdal.tbeg.engine.preprocessing.setFormulaRaw
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * hideable 기능 통합 테스트
 */
class HideableIntegrationTest {

    /**
     * 프로그래밍 방식으로 테스트 템플릿을 생성한다.
     *
     * 템플릿 구조 (DOWN repeat):
     * | A      | B         | C                                              | D        |
     * | 이름   | 부서      | 급여                                             | 나이      |   <- 행 0 (타이틀)
     * | ${emp.name} | ${emp.dept} | ${hideable(value=emp.salary, bundle=C1:C3)} | ${emp.age} | <- 행 1 (데이터, 0-based)
     * | 합계   |           | =SUM(C2:C2)                                    |           |   <- 행 2
     * |        |           | ${repeat(employees, A2:D2, emp)}               |           |   <- 행 3
     *
     * 참고: 엑셀에서 C1:C3은 1-based, 내부적으로 행 0~2에 해당
     */
    private fun createTemplateWithHideable(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        // 행 0: 타이틀
        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        // 행 1: 데이터 (hideable 포함)
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${emp.dept}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C3)}")
        row1.createCell(3).setCellValue("\${emp.age}")

        // 행 2: 합계
        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("합계")
        row2.createCell(2).setCellFormula("SUM(C2:C2)")

        // 행 3: repeat 마커
        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    /**
     * bundle 없는 hideable 템플릿을 생성한다.
     */
    private fun createTemplateWithHideableNoBundleRange(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("급여")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${hideable(value=emp.salary)}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${repeat(employees, A2:B2, emp)}")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    @Test
    fun `hideFields 지정 시 bundle 범위가 삭제된다`() {
        val templateBytes = createTemplateWithHideable()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30),
                mapOf("name" to "홍용호", "dept" to "기획", "salary" to 6500, "age" to 25)
            ))
            hideFields("employees", "salary")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // C열(급여)이 제거되어, 원래 D열의 "나이"가 C열로 이동해야 한다
            // 행 0: 이름, 부서, 나이 (급여 제거)
            val headerRow = sheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("부서", headerRow.getCell(1)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(2)?.stringCellValue)

            // 데이터 행: 실제 데이터가 바인딩됨
            val dataRow1 = sheet.getRow(1)
            assertEquals("황용호", dataRow1.getCell(0)?.stringCellValue)
            assertEquals("개발", dataRow1.getCell(1)?.stringCellValue)
        }
    }

    @Test
    fun `hideFields 미지정 시 hideable이 일반 필드로 동작한다`() {
        val templateBytes = createTemplateWithHideable()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            // hideFields 미지정
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 모든 열이 존재해야 한다
            val headerRow = sheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("부서", headerRow.getCell(1)?.stringCellValue)
            assertEquals("급여", headerRow.getCell(2)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(3)?.stringCellValue)

            // salary가 정상적으로 바인딩되어야 한다
            val dataRow = sheet.getRow(1)
            assertEquals(8000.0, dataRow.getCell(2)?.numericCellValue)
        }
    }

    @Test
    fun `bundle 없는 hideable은 영향 범위 내 셀만 삭제된다`() {
        val templateBytes = createTemplateWithHideableNoBundleRange()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "salary" to 8000)
            ))
            hideFields("employees", "salary")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // bundle 미지정 시 repeat 범위(데이터 행)만 영향
            // 헤더 행은 repeat 범위 밖이므로 유지된다
            val headerRow = sheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("급여", headerRow.getCell(1)?.stringCellValue)

            // 데이터 행에서는 salary 열이 제거됨
            val dataRow = sheet.getRow(1)
            assertEquals("황용호", dataRow.getCell(0)?.stringCellValue)
        }
    }

    @Test
    fun `unmarkedHidePolicy ERROR 시 hideable 없는 필드가 hideFields에 있으면 예외 발생`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("\${emp.name}")
        row0.createCell(1).setCellValue("\${emp.salary}")  // 일반 ItemField (hideable 아님)
        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${repeat(employees, A1:B1, emp)}")

        val templateBytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val config = TbegConfig(unmarkedHidePolicy = UnmarkedHidePolicy.ERROR)
        val provider = simpleDataProvider {
            items("employees", listOf(mapOf("name" to "test", "salary" to 1000)))
            hideFields("employees", "salary")
        }

        assertThrows<MarkerValidationException> {
            ExcelGenerator(config).use { it.generate(ByteArrayInputStream(templateBytes), provider) }
        }
    }

    @Test
    fun `수식형 TBEG_HIDEABLE 마커가 있는 셀이 1st pass에서 정상 처리된다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${hideable(value=emp.dept, bundle=B1:B2)}")
        // 수식형 hideable: 셀 이동(copyCellValue) 시 POI 수식 파서를 우회해야 한다
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C2)}")
        (row1.createCell(3) as org.apache.poi.xssf.usermodel.XSSFCell).setFormulaRaw("TBEG_HIDEABLE(emp.age)")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        val templateBytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        // dept를 숨기면 C열(salary), D열(=TBEG_HIDEABLE(emp.age))이 왼쪽으로 이동
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            hideFields("employees", "dept")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { resultWorkbook ->
            val resultSheet = resultWorkbook.getSheetAt(0)
            val headerRow = resultSheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("급여", headerRow.getCell(1)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(2)?.stringCellValue)

            // 데이터가 정상 바인딩됨
            val dataRow = resultSheet.getRow(1)
            assertEquals("황용호", dataRow.getCell(0)?.stringCellValue)
            assertEquals(8000.0, dataRow.getCell(1)?.numericCellValue)
            assertEquals(30.0, dataRow.getCell(2)?.numericCellValue)
        }
    }

    @Test
    fun `hide 시 bundle 마커의 범위가 올바르게 조정된다`() {
        // 템플릿:
        // A: 이름 | B: 부서 | C: 급여 | D: 나이
        // ${emp.name} | ${hideable(emp.dept, B1:B2)} | ${emp.salary} | ${emp.age}
        // ${bundle(A1:D2)}
        // ${repeat(employees, A2:D2, emp)}
        //
        // dept 숨기면 C->B, D->C로 이동. bundle 범위도 A1:D2 -> A1:C2로 조정되어야 한다.
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${hideable(value=emp.dept, bundle=B1:B2)}")
        row1.createCell(2).setCellValue("\${emp.salary}")
        row1.createCell(3).setCellValue("\${emp.age}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${bundle(A1:D2)}")

        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        val templateBytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            hideFields("employees", "dept")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { resultWorkbook ->
            val resultSheet = resultWorkbook.getSheetAt(0)

            // 3개 열만 남아야 한다
            val headerRow = resultSheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("급여", headerRow.getCell(1)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(2)?.stringCellValue)

            // 데이터가 정상 바인딩됨
            val dataRow = resultSheet.getRow(1)
            assertEquals("황용호", dataRow.getCell(0)?.stringCellValue)
            assertEquals(8000.0, dataRow.getCell(1)?.numericCellValue)
            assertEquals(30.0, dataRow.getCell(2)?.numericCellValue)
        }
    }

    @Test
    fun `hide 시 수식 내 셀 참조가 올바르게 조정된다`() {
        // C열 삭제 시 D열의 SUM 수식이 C열 참조로 조정되어야 한다
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("급여")
        row0.createCell(2).setCellValue("보너스")
        row0.createCell(3).setCellValue("합계")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${emp.salary}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.bonus, bundle=C1:C2)}")
        row1.createCell(3).setCellFormula("B2+C2")  // salary + bonus

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        val templateBytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "salary" to 8000, "bonus" to 1000)
            ))
            hideFields("employees", "bonus")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { resultWorkbook ->
            val resultSheet = resultWorkbook.getSheetAt(0)

            // 합계 열이 C로 이동, 수식이 B2+C2 -> B2 참조만 남거나 조정됨
            val headerRow = resultSheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("급여", headerRow.getCell(1)?.stringCellValue)
            assertEquals("합계", headerRow.getCell(2)?.stringCellValue)

            // 수식이 조정되었는지 확인
            val dataRow = resultSheet.getRow(1)
            assertEquals(CellType.FORMULA, dataRow.getCell(2)?.cellType)
        }
    }

    // ===================== DIM 모드 테스트 =====================

    /**
     * DIM 모드 템플릿을 생성한다.
     *
     * | A         | B           | C (DIM)                                            | D          |
     * | 이름      | 부서         | 급여                                                  | 나이        |  <- 행 0
     * | ${emp.name} | ${emp.dept} | ${hideable(value=emp.salary, bundle=C1:C3, mode=dim)} | ${emp.age} | <- 행 1
     * | 합계      |              | =SUM(C2:C2)                                          |            |  <- 행 2
     * |           |              | ${repeat(employees, A2:D2, emp)}                     |            |  <- 행 3
     */
    private fun createTemplateWithDimHideable(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${emp.dept}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}")
        row1.createCell(3).setCellValue("\${emp.age}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("합계")
        row2.createCell(2).setCellFormula("SUM(C2:C2)")

        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    @Test
    fun `DIM 모드 - repeat 데이터 영역만 dim 처리 + 구조 유지`() {
        val templateBytes = createTemplateWithDimHideable()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30),
                mapOf("name" to "홍용호", "dept" to "기획", "salary" to 6500, "age" to 25)
            ))
            hideFields("employees", "salary")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // 구조 유지: 4개 열이 모두 존재해야 한다 (DELETE와 달리 이동하지 않음)
            val headerRow = sheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("부서", headerRow.getCell(1)?.stringCellValue)
            // C열(급여) 타이틀은 repeat 영역 밖이므로 원래 텍스트/스타일 모두 유지
            assertEquals("급여", headerRow.getCell(2)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(3)?.stringCellValue)

            // 타이틀 행(repeat 밖): 배경은 DIM 적용 안 됨, 글자색만 DIM 적용
            val headerStyle = headerRow.getCell(2)?.cellStyle as? org.apache.poi.xssf.usermodel.XSSFCellStyle
            if (headerStyle != null) {
                assertNotEquals(
                    FillPatternType.SOLID_FOREGROUND, headerStyle.fillPattern,
                    "repeat 밖 셀에는 DIM 배경이 적용되지 않아야 한다"
                )
                val headerFont = headerStyle.font as? XSSFFont
                assertNotNull(headerFont, "repeat 밖 셀의 폰트가 존재해야 한다")
                assertArrayEquals(
                    byteArrayOf(0xBF.toByte(), 0xBF.toByte(), 0xBF.toByte()),
                    headerFont!!.xssfColor?.rgb,
                    "repeat 밖 bundle 영역의 글자색은 DIM 색상(#BFBFBF)이어야 한다"
                )
            }

            // 합계 행도 repeat 영역 밖: 텍스트 유지 + 글자색만 DIM 적용
            val sumRow = sheet.getRow(2 + 1) // 데이터 2건이므로 repeat 확장 후 합계는 행 3
            assertEquals("합계", sumRow?.getCell(0)?.stringCellValue)
            val sumCellStyle = sumRow?.getCell(2)?.cellStyle as? org.apache.poi.xssf.usermodel.XSSFCellStyle
            if (sumCellStyle != null) {
                val sumFont = sumCellStyle.font as? XSSFFont
                assertArrayEquals(
                    byteArrayOf(0xBF.toByte(), 0xBF.toByte(), 0xBF.toByte()),
                    sumFont?.xssfColor?.rgb,
                    "합계 행의 글자색도 DIM 색상(#BFBFBF)이어야 한다"
                )
            }

            // 데이터 행(repeat 내): salary(C열)는 DIM 처리 -- 값 비움 + 스타일 적용
            val dataRow1 = sheet.getRow(1)
            assertEquals("황용호", dataRow1.getCell(0)?.stringCellValue)
            assertEquals("개발", dataRow1.getCell(1)?.stringCellValue)
            assertTrue(
                dataRow1.getCell(2) == null ||
                dataRow1.getCell(2).cellType == CellType.BLANK,
                "DIM 데이터 영역의 셀은 비어있어야 한다"
            )
            assertEquals(30.0, dataRow1.getCell(3)?.numericCellValue)

            // DIM 스타일 확인: 데이터 행의 C열에 회색 배경이 적용되어야 한다
            val dimDataCell = dataRow1.getCell(2)
            if (dimDataCell != null) {
                val style = dimDataCell.cellStyle as? org.apache.poi.xssf.usermodel.XSSFCellStyle
                assertNotNull(style)
                assertEquals(FillPatternType.SOLID_FOREGROUND, style!!.fillPattern)
                assertArrayEquals(
                    byteArrayOf(0xD9.toByte(), 0xD9.toByte(), 0xD9.toByte()),
                    style.fillForegroundXSSFColor?.rgb,
                    "DIM 배경색은 #D9D9D9이어야 한다"
                )
            }
        }
    }

    @Test
    fun `DIM 모드 - hideFields 미지정 시 일반 필드로 동작`() {
        val templateBytes = createTemplateWithDimHideable()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            // hideFields 미지정
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            // salary가 정상적으로 바인딩되어야 한다
            val dataRow = sheet.getRow(1)
            assertEquals(8000.0, dataRow.getCell(2)?.numericCellValue)
        }
    }

    /**
     * DIM + DELETE 혼합 템플릿을 생성한다.
     *
     * | A          | B (DELETE)                                    | C (DIM)                                             | D          |
     * | ${emp.name} | ${hideable(emp.dept, B1:B2)}                 | ${hideable(emp.salary, C1:C3, dim)}                 | ${emp.age} |
     * | 합계        |                                              | =SUM(C2:C2)                                         |            |
     * |             | ${repeat(employees, A2:D2, emp)}             |                                                     |            |
     */
    private fun createTemplateWithMixedHideable(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${hideable(value=emp.dept, bundle=B1:B2)}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}")
        row1.createCell(3).setCellValue("\${emp.age}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("합계")
        row2.createCell(2).setCellFormula("SUM(C2:C2)")

        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    @Test
    fun `DIM + DELETE 혼합 - dept DELETE, salary DIM 동시 처리`() {
        val templateBytes = createTemplateWithMixedHideable()
        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            hideFields("employees", "dept", "salary")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
            val sheet = workbook.getSheetAt(0)

            // B열(부서)이 DELETE로 삭제 -> C열이 B로, D열이 C로 이동
            // 이동 후 B열은 급여(DIM), C열은 나이
            val headerRow = sheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            // B열(원래 C열): DIM -- 타이틀은 repeat 영역 밖이므로 원래 텍스트 유지
            assertEquals("급여", headerRow.getCell(1)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(2)?.stringCellValue)

            // 데이터 행: name은 바인딩, salary(B열)는 DIM으로 비워짐, age(C열)는 바인딩
            val dataRow = sheet.getRow(1)
            assertEquals("황용호", dataRow.getCell(0)?.stringCellValue)
            assertTrue(
                dataRow.getCell(1) == null ||
                dataRow.getCell(1).cellType == CellType.BLANK,
                "DIM 데이터 영역의 셀은 비어있어야 한다"
            )
            assertEquals(30.0, dataRow.getCell(2)?.numericCellValue)
        }
    }

    @Test
    fun `여러 필드를 동시에 숨길 수 있다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${hideable(value=emp.dept, bundle=B1:B2)}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C2)}")
        row1.createCell(3).setCellValue("\${emp.age}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${repeat(employees, A2:D2, emp)}")

        val templateBytes = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val provider = simpleDataProvider {
            items("employees", listOf(
                mapOf("name" to "황용호", "dept" to "개발", "salary" to 8000, "age" to 30)
            ))
            hideFields("employees", "dept", "salary")
        }

        val result = ExcelGenerator().use { it.generate(ByteArrayInputStream(templateBytes), provider) }

        XSSFWorkbook(ByteArrayInputStream(result)).use { resultWorkbook ->
            val resultSheet = resultWorkbook.getSheetAt(0)
            // B, C열이 삭제되어 이름, 나이만 남아야 한다
            val headerRow = resultSheet.getRow(0)
            assertEquals("이름", headerRow.getCell(0)?.stringCellValue)
            assertEquals("나이", headerRow.getCell(1)?.stringCellValue)
        }
    }
}
