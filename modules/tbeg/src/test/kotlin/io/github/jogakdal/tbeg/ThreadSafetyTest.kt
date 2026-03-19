package io.github.jogakdal.tbeg

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ExcelGenerator의 스레드 안전성 검증 테스트.
 * 하나의 ExcelGenerator 싱글톤 인스턴스를 여러 스레드에서 동시에 사용하여
 * 데이터 레이스 없이 정상 생성되는지 확인한다.
 */
class ThreadSafetyTest {

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
    fun `싱글톤 인스턴스에서 동시 생성 시 데이터 격리 검증`() {
        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = ConcurrentHashMap<Int, ByteArray>()
        val errors = ConcurrentHashMap<Int, Exception>()

        // 각 스레드별 고유 데이터 생성
        val threads = (0 until threadCount).map { index ->
            Thread {
                try {
                    startLatch.await(10, TimeUnit.SECONDS)

                    val data = mapOf(
                        "title" to "보고서_$index",
                        "date" to "2026-03-${18 + index}",
                        "employees" to (1..5).map { i ->
                            mapOf(
                                "name" to "직원${index}_$i",
                                "position" to "직급$i",
                                "salary" to (1000 * (index + 1) + i * 100)
                            )
                        }
                    )

                    val template = javaClass.getResourceAsStream("/templates/simple_template.xlsx")
                        ?: throw IllegalStateException("Template not found")

                    val bytes = generator.generate(template, data)
                    results[index] = bytes
                } catch (e: Exception) {
                    errors[index] = e
                } finally {
                    doneLatch.countDown()
                }
            }.apply { start() }
        }

        // 모든 스레드 동시 시작
        startLatch.countDown()
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "모든 스레드가 시간 내에 완료되어야 합니다")

        // 예외 없이 모든 스레드 완료 확인
        if (errors.isNotEmpty()) {
            val errorMessages = errors.entries.joinToString("\n") { (idx, e) ->
                "스레드 $idx: ${e.message}"
            }
            fail<Unit>("일부 스레드에서 예외 발생:\n$errorMessages")
        }

        assertEquals(threadCount, results.size, "모든 스레드가 결과를 생성해야 합니다")

        // 각 결과가 유효한 xlsx이고 데이터가 정확한지 검증
        results.forEach { (index, bytes) ->
            XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                assertTrue(workbook.numberOfSheets > 0, "스레드 $index: 시트가 있어야 합니다")

                val sheet = workbook.getSheetAt(0)

                // 첫 번째 데이터 행 (A6 = row index 5)에서 해당 스레드의 고유 데이터 확인
                val firstDataRow = sheet.getRow(5)
                assertNotNull(firstDataRow, "스레드 $index: 데이터 행이 있어야 합니다")

                val nameCell = firstDataRow.getCell(0)?.stringCellValue
                assertTrue(
                    nameCell?.startsWith("직원${index}_") == true,
                    "스레드 $index: 데이터가 다른 스레드와 섞이지 않아야 합니다 (실제: $nameCell)"
                )
            }
        }
    }

    @Test
    fun `피벗 테이블 포함 템플릿의 동시 생성 시 캐시 경합 검증`() {
        val threadCount = 10
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val results = ConcurrentHashMap<Int, ByteArray>()
        val errors = ConcurrentHashMap<Int, Exception>()

        val threads = (0 until threadCount).map { index ->
            Thread {
                try {
                    startLatch.await(10, TimeUnit.SECONDS)

                    val data = mapOf(
                        "title" to "피벗_$index",
                        "date" to "2026-03-18",
                        "employees" to (1..3).map { i ->
                            mapOf(
                                "name" to "사원${index}_$i",
                                "position" to if (i % 2 == 0) "과장" else "대리",
                                "salary" to (3000 + index * 100 + i * 50)
                            )
                        }
                    )

                    val template = javaClass.getResourceAsStream("/templates/template.xlsx")
                        ?: throw IllegalStateException("Template not found")

                    val bytes = generator.generate(template, data)
                    results[index] = bytes
                } catch (e: Exception) {
                    errors[index] = e
                } finally {
                    doneLatch.countDown()
                }
            }.apply { start() }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "모든 스레드가 시간 내에 완료되어야 합니다")

        if (errors.isNotEmpty()) {
            val errorMessages = errors.entries.joinToString("\n") { (idx, e) ->
                "스레드 $idx: ${e.message}\n${e.stackTraceToString()}"
            }
            fail<Unit>("피벗 테이블 동시 생성 중 예외 발생:\n$errorMessages")
        }

        assertEquals(threadCount, results.size, "모든 스레드가 결과를 생성해야 합니다")

        // 각 결과가 유효한 xlsx인지 검증
        results.forEach { (index, bytes) ->
            XSSFWorkbook(ByteArrayInputStream(bytes)).use { workbook ->
                assertTrue(workbook.numberOfSheets > 0, "스레드 $index: 시트가 있어야 합니다")
            }
        }
    }
}
