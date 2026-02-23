package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.engine.rendering.CellContent
import io.github.jogakdal.tbeg.engine.rendering.TemplateAnalyzer
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 범위를 취급하는 마커의 중복 감지 테스트
 *
 * - repeat 마커: 같은 컬렉션 + 같은 대상 범위(시트+영역) → 중복
 * - image 마커: 같은 이름 + 같은 위치(시트+셀) + 같은 크기 → 중복
 *
 * 중복 시 경고 로그를 출력하고 마지막 마커만 유지한다.
 */
@DisplayName("중복 마커 감지 테스트")
class DuplicateRepeatDetectionTest {

    private val analyzer = TemplateAnalyzer()

    // ==================== repeat 마커 중복 ====================

    @Nested
    @DisplayName("repeat - 같은 시트 내 중복 감지")
    inner class RepeatSameSheetTest {

        @Test
        @DisplayName("같은 컬렉션과 같은 범위의 repeat 마커가 있으면 마지막 것만 유지한다")
        fun duplicateOnSameSheet() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("이름")
                        createCell(1).setCellValue("직급")
                    }
                    createRow(1).apply {
                        createCell(0).setCellValue("\${e.name}")
                        createCell(1).setCellValue("\${e.position}")
                    }
                    createRow(2).createCell(0).setCellValue("\${repeat(employees, A2:B2, e1)}")
                    createRow(3).createCell(0).setCellValue("\${repeat(employees, A2:B2, e2)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val regions = spec.sheets[0].repeatRegions

            assertEquals(1, regions.size, "중복이 제거되어 1개만 남아야 한다")
            assertEquals("e2", regions[0].variable, "마지막 마커(e2)만 유지되어야 한다")
        }

        @Test
        @DisplayName("3개 이상 중복되어도 마지막 것만 유지한다")
        fun tripleDuplicateOnSameSheet() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${e.name}")
                    createRow(1).createCell(0).setCellValue("\${repeat(employees, A1:A1, e1)}")
                    createRow(2).createCell(0).setCellValue("\${repeat(employees, A1:A1, e2)}")
                    createRow(3).createCell(0).setCellValue("\${repeat(employees, A1:A1, e3)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val regions = spec.sheets[0].repeatRegions

            assertEquals(1, regions.size)
            assertEquals("e3", regions[0].variable, "마지막 마커(e3)만 유지되어야 한다")
        }

        @Test
        @DisplayName("같은 컬렉션이지만 범위가 다르면 중복이 아니다")
        fun differentRangeNotDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${e1.name}")
                        createCell(1).setCellValue("\${e1.position}")
                        createCell(3).setCellValue("\${e2.name}")
                    }
                    createRow(1).apply {
                        createCell(0).setCellValue("\${repeat(employees, A1:B1, e1)}")
                        createCell(3).setCellValue("\${repeat(employees, D1:D1, e2)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val regions = spec.sheets[0].repeatRegions

            assertEquals(2, regions.size, "범위가 다르므로 중복이 아니다")
        }

        @Test
        @DisplayName("같은 범위지만 컬렉션이 다르면 중복이 아니다")
        fun differentCollectionNotDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${e.name}")
                    createRow(1).apply {
                        createCell(0).setCellValue("\${repeat(employees, A1:A1, e)}")
                        createCell(1).setCellValue("\${repeat(departments, A1:A1, d)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val regions = spec.sheets[0].repeatRegions

            assertEquals(2, regions.size, "다른 컬렉션이므로 중복이 아니다")
        }
    }

    @Nested
    @DisplayName("repeat - 시트 간 중복 감지")
    inner class RepeatCrossSheetTest {

        @Test
        @DisplayName("다른 시트에서 시트 접두사로 같은 대상 범위를 참조하면 중복이다")
        fun crossSheetDuplicateWithSheetPrefix() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${e.name}")
                        createCell(1).setCellValue("\${e.position}")
                    }
                    createRow(1).createCell(0).setCellValue("\${repeat(employees, A1:B1, e1)}")
                }
                createSheet("Sheet2").apply {
                    createRow(0).createCell(0).setCellValue("\${repeat(employees, 'Sheet1'!A1:B1, e2)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val sheet1Regions = spec.sheets[0].repeatRegions
            val sheet2Regions = spec.sheets[1].repeatRegions

            assertEquals(1, sheet1Regions.size, "대상 시트(Sheet1)에 repeat이 매핑되어야 한다")
            assertEquals(0, sheet2Regions.size, "Sheet2는 마커만 있고 대상 범위가 없다")
            assertEquals("e2", sheet1Regions[0].variable, "마지막 마커(e2)만 유지되어야 한다")
        }

        @Test
        @DisplayName("다른 시트에서 같은 셀 좌표지만 시트 접두사가 없으면 중복이 아니다")
        fun sameCoordinatesDifferentTargetSheets() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${e1.name}")
                    createRow(1).createCell(0).setCellValue("\${repeat(employees, A1:A1, e1)}")
                }
                createSheet("Sheet2").apply {
                    createRow(0).createCell(0).setCellValue("\${e2.name}")
                    createRow(1).createCell(0).setCellValue("\${repeat(employees, A1:A1, e2)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val sheet1Regions = spec.sheets[0].repeatRegions
            val sheet2Regions = spec.sheets[1].repeatRegions

            assertEquals(1, sheet1Regions.size, "Sheet1에 1개 유지")
            assertEquals(1, sheet2Regions.size, "Sheet2에 1개 유지")
            assertEquals("e1", sheet1Regions[0].variable)
            assertEquals("e2", sheet2Regions[0].variable)
        }

        @Test
        @DisplayName("3개 시트에서 같은 대상을 참조하면 마지막 시트의 마커만 유지한다")
        fun tripleSheetDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${e.name}")
                    createRow(1).createCell(0).setCellValue("\${repeat(employees, A1:A1, e1)}")
                }
                createSheet("Sheet2").apply {
                    createRow(0).createCell(0).setCellValue("\${repeat(employees, Sheet1!A1:A1, e2)}")
                }
                createSheet("Sheet3").apply {
                    createRow(0).createCell(0).setCellValue("\${repeat(employees, Sheet1!A1:A1, e3)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))

            val totalRegions = spec.sheets.sumOf { it.repeatRegions.size }
            assertEquals(1, totalRegions, "전체 시트에서 1개만 남아야 한다")

            val survivingSheet = spec.sheets.first { it.repeatRegions.isNotEmpty() }
            assertEquals("Sheet1", survivingSheet.sheetName, "대상 시트(Sheet1)에 repeat이 매핑되어야 한다")
            assertEquals("e3", survivingSheet.repeatRegions[0].variable)
        }
    }

    // ==================== image 마커 중복 ====================

    @Nested
    @DisplayName("image - 같은 시트 내 중복 감지")
    inner class ImageSameSheetTest {

        @Test
        @DisplayName("같은 이름, 같은 위치, 같은 크기의 image 마커가 있으면 마지막 것만 유지한다")
        fun duplicateImageOnSameSheet() {
            // 두 셀에 같은 이미지를 같은 위치(B1:C2)에 삽입하는 마커
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${image(logo, B1:C2)}")
                        createCell(3).setCellValue("\${image(logo, B1:C2)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val imageMarkers = spec.sheets[0].rows.flatMap { it.cells }
                .map { it.content }
                .filterIsInstance<CellContent.ImageMarker>()

            assertEquals(1, imageMarkers.size, "중복 image 마커가 제거되어 1개만 남아야 한다")
            assertEquals("logo", imageMarkers[0].imageName)
        }

        @Test
        @DisplayName("같은 이름, 같은 위치지만 크기가 다르면 중복이 아니다")
        fun differentSizeNotDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${image(logo, B1:C2, 100:50)}")
                        createCell(3).setCellValue("\${image(logo, B1:C2, 200:100)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val imageMarkers = spec.sheets[0].rows.flatMap { it.cells }
                .map { it.content }
                .filterIsInstance<CellContent.ImageMarker>()

            assertEquals(2, imageMarkers.size, "크기가 다르므로 중복이 아니다")
        }

        @Test
        @DisplayName("같은 이름이지만 위치가 다르면 중복이 아니다")
        fun differentPositionNotDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${image(logo, B1)}")
                        createCell(1).setCellValue("\${image(logo, D1)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val imageMarkers = spec.sheets[0].rows.flatMap { it.cells }
                .map { it.content }
                .filterIsInstance<CellContent.ImageMarker>()

            assertEquals(2, imageMarkers.size, "위치가 다르므로 중복이 아니다")
        }

        @Test
        @DisplayName("같은 위치지만 이름이 다르면 중복이 아니다")
        fun differentNameNotDuplicate() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).apply {
                        createCell(0).setCellValue("\${image(logo, B1:C2)}")
                        createCell(3).setCellValue("\${image(banner, B1:C2)}")
                    }
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val imageMarkers = spec.sheets[0].rows.flatMap { it.cells }
                .map { it.content }
                .filterIsInstance<CellContent.ImageMarker>()

            assertEquals(2, imageMarkers.size, "이름이 다르므로 중복이 아니다")
        }

        @Test
        @DisplayName("position이 없는 image 마커는 중복 체크 대상이 아니다")
        fun noPositionNeverDuplicate() {
            // position 없이 같은 이름으로 두 곳에 선언 → 각각 마커 셀 위치에 삽입되므로 중복 아님
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${image(logo)}")
                    createRow(1).createCell(0).setCellValue("\${image(logo)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))
            val imageMarkers = spec.sheets[0].rows.flatMap { it.cells }
                .map { it.content }
                .filterIsInstance<CellContent.ImageMarker>()

            assertEquals(2, imageMarkers.size, "position이 없으면 중복 체크를 하지 않는다")
        }
    }

    @Nested
    @DisplayName("image - 시트 간 중복 감지")
    inner class ImageCrossSheetTest {

        @Test
        @DisplayName("다른 시트에서 시트 접두사로 같은 대상 위치를 참조하면 중복이다")
        fun crossSheetDuplicateWithSheetPrefix() {
            // Sheet1: ${image(logo, B1:C2)} — 대상 시트 = Sheet1
            // Sheet2: ${image(logo, 'Sheet1'!B1:C2)} — 대상 시트 = Sheet1
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${image(logo, B1:C2)}")
                }
                createSheet("Sheet2").apply {
                    createRow(0).createCell(0).setCellValue("\${image(logo, 'Sheet1'!B1:C2)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))

            val allImageMarkers = spec.sheets.flatMap { sheet ->
                sheet.rows.flatMap { it.cells }.map { it.content }
            }.filterIsInstance<CellContent.ImageMarker>()

            assertEquals(1, allImageMarkers.size, "시트 간 중복이 제거되어 전체 1개만 남아야 한다")
        }

        @Test
        @DisplayName("다른 시트에서 같은 좌표지만 시트 접두사가 없으면 중복이 아니다")
        fun sameCoordinatesDifferentTargetSheets() {
            val template = createTemplate {
                createSheet("Sheet1").apply {
                    createRow(0).createCell(0).setCellValue("\${image(logo, B1:C2)}")
                }
                createSheet("Sheet2").apply {
                    createRow(0).createCell(0).setCellValue("\${image(logo, B1:C2)}")
                }
            }

            val spec = analyzer.analyze(ByteArrayInputStream(template))

            val allImageMarkers = spec.sheets.flatMap { sheet ->
                sheet.rows.flatMap { it.cells }.map { it.content }
            }.filterIsInstance<CellContent.ImageMarker>()

            assertEquals(2, allImageMarkers.size, "대상 시트가 다르므로 중복이 아니다")
        }
    }

    // ==================== 헬퍼 ====================

    private fun createTemplate(block: XSSFWorkbook.() -> Unit): ByteArray =
        XSSFWorkbook().use { workbook ->
            workbook.block()
            ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
        }
}
