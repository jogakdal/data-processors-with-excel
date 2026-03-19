package io.github.jogakdal.tbeg.benchmark

import io.github.jogakdal.tbeg.simpleDataProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

/**
 * 벤치마크 공통 지원: 템플릿 생성, 데이터 제공, 결과 출력
 */
object BenchmarkSupport {

    data class Employee(val name: String, val position: String, val salary: Int)

    private val POSITIONS = listOf("사원", "대리", "과장", "차장", "부장")
    private val NAMES = listOf("황", "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")

    /**
     * 벤치마크용 미니멀 템플릿을 생성한다.
     * - 단순 DOWN 방향 repeat + SUM 수식
     */
    fun createMinimalTemplate(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Data")

        sheet.createRow(0).createCell(0).setCellValue("\${title}")

        sheet.createRow(1).createCell(0).setCellValue("\${repeat(employees, A3:C3, emp)}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("\${emp.name}")
        row2.createCell(1).setCellValue("\${emp.position}")
        row2.createCell(2).setCellValue("\${emp.salary}")

        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("합계")
        row3.createCell(2).cellFormula = "SUM(C3:C3)"

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    /**
     * 지정된 크기만큼 Employee 데이터를 Sequence로 생성한다.
     */
    fun generateData(count: Int): Sequence<Employee> = sequence {
        repeat(count) { i ->
            yield(
                Employee(
                    name = "${NAMES[i % NAMES.size]}용호${i + 1}",
                    position = POSITIONS[i % POSITIONS.size],
                    salary = 3000 + (i % 5) * 1000
                )
            )
        }
    }

    /**
     * Map 방식 데이터를 생성한다 (전체를 메모리에 적재).
     */
    fun createMapData(rowCount: Int): Map<String, Any> = mapOf(
        "title" to "벤치마크 ($rowCount 행)",
        "employees" to generateData(rowCount).toList()
    )

    /**
     * DataProvider 방식 데이터를 생성한다 (Iterator 기반 지연 로딩).
     */
    fun createDataProvider(rowCount: Int) = simpleDataProvider {
        value("title", "벤치마크 ($rowCount 행)")
        items("employees", rowCount) {
            generateData(rowCount).iterator()
        }
    }

    /**
     * JMH 결과에서 GC 관련 메트릭을 추출하는 키
     */
    object GcMetrics {
        const val ALLOC_RATE = "gc.alloc.rate.norm"
        const val GC_COUNT = "gc.count"
        const val GC_TIME = "gc.time"
    }

    /**
     * 바이트를 MB 문자열로 변환한다.
     */
    fun bytesToMb(bytes: Double) = String.format("%.1fMB", bytes / (1024 * 1024))

    /**
     * 밀리초를 포맷된 문자열로 변환한다.
     */
    fun formatMs(ms: Double) = String.format("%,.0fms", ms)
}
