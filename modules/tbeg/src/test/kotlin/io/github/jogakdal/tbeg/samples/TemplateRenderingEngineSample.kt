package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * TemplateRenderingEngine 샘플 실행 클래스.
 *
 * 템플릿 엔진을 사용하여 Excel 파일을 생성합니다.
 *
 * ## 지원 문법
 * - `${변수명}` - 단순 변수 치환
 * - `${item.field}` - 반복 항목의 필드 접근
 * - `${object.method()}` - 메서드 호출 (예: `${employees.size()}`)
 * - `${repeat(collection, range, var)}` - 반복 처리
 * - `${image.name}` - 이미지 삽입
 * - 수식 내 변수 치환: `HYPERLINK("${url}", "${text}")`
 *
 * ## 처리 방식
 * - **비스트리밍 모드(XSSF)**: 템플릿 변환 방식 (shiftRows + copyRowFrom)
 * - **스트리밍 모드(SXSSF)**: 명세 기반 순차 생성 (메모리 효율적)
 *
 * ## Spring Boot 설정 예시
 * ```kotlin
 * @Configuration
 * class ExcelConfig {
 *     @Bean
 *     fun excelGenerator(): ExcelGenerator {
 *         val config = TbegConfig(
 *             streamingMode = StreamingMode.ENABLED
 *         )
 *         return ExcelGenerator(config)
 *     }
 * }
 * ```
 */
object TemplateRenderingEngineSample {

    data class Employee(val name: String, val position: String, val salary: Int)

    @JvmStatic
    fun main(args: Array<String>) {
        val moduleDir = findModuleDir()
        val outputDir = moduleDir.resolve("build/samples/template-rendering")
        Files.createDirectories(outputDir)

        println("=".repeat(60))
        println("TemplateRenderingEngine 샘플 실행")
        println("=".repeat(60))

        // 1. 비스트리밍 모드 (XSSF)
        runXssfExample(outputDir)

        // 2. 스트리밍 모드 (SXSSF)
        runSxssfExample(outputDir)

        // 3. 대용량 스트리밍 테스트
        runLargeSxssfExample(outputDir)

        println("\n" + "=".repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=".repeat(60))
    }

    // ==================== 1. 비스트리밍 모드 (XSSF) ====================

    /**
     * 비스트리밍 모드 (XSSF)
     *
     * 템플릿 변환 방식으로 처리합니다.
     * - shiftRows()로 행 삽입 공간 확보
     * - copyRowFrom()으로 템플릿 행 복사 (수식 자동 조정)
     * - 소량 데이터에 적합
     */
    private fun runXssfExample(outputDir: Path) {
        println("\n[1] 비스트리밍 모드 (XSSF)")
        println("-".repeat(40))

        val config = TbegConfig(
            streamingMode = StreamingMode.DISABLED
        )

        ExcelGenerator(config).use { generator ->
            val data = mapOf(
                "title" to "템플릿 렌더링 테스트 (XSSF)",
                "date" to LocalDate.now().toString(),
                "linkText" to "(주)휴넷 홈페이지",
                "url" to "https://www.hunet.co.kr",
                "employees" to listOf(
                    Employee("황용호", "부장", 8000),
                    Employee("한용호", "과장", 6500),
                    Employee("홍용호", "대리", 4500),
                    Employee("허용호", "사원", 3500)
                ),
                "logo" to loadImage("hunet_logo.png"),
                "ci" to loadImage("hunet_ci.png")
            ).filterValues { it != null }.mapValues { it.value!! }

            val template = loadTemplate()
            val resultPath = generator.generateToFile(
                template = template,
                dataProvider = SimpleDataProvider.of(data),
                outputDir = outputDir,
                baseFileName = "xssf_example"
            )

            println("\t결과: $resultPath")
        }
    }

    // ==================== 2. 스트리밍 모드 (SXSSF) ====================

    /**
     * 스트리밍 모드 (SXSSF)
     *
     * 청사진 기반 순차 생성 방식으로 처리합니다.
     * - 템플릿을 분석하여 명세(WorkbookSpec) 생성
     * - 명세에 따라 순차적으로 행 출력
     * - 메모리 효율적 (대용량 데이터에 적합)
     */
    private fun runSxssfExample(outputDir: Path) {
        println("\n[2] 스트리밍 모드 (SXSSF)")
        println("-".repeat(40))

        val config = TbegConfig(
            streamingMode = StreamingMode.ENABLED
        )

        ExcelGenerator(config).use { generator ->
            val dataProvider = simpleDataProvider {
                value("title", "템플릿 렌더링 테스트 (SXSSF)")
                value("date", LocalDate.now().toString())
                value("linkText", "(주)휴넷 홈페이지")
                value("url", "https://www.hunet.co.kr")
                image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
                image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

                items("employees") {
                    listOf(
                        Employee("황용호", "부장", 8000),
                        Employee("한용호", "과장", 6500),
                        Employee("홍용호", "대리", 4500),
                        Employee("허용호", "사원", 3500),
                        Employee("하용호", "인턴", 2500)
                    ).iterator()
                }
            }

            val template = loadTemplate()
            val resultPath = generator.generateToFile(
                template = template,
                dataProvider = dataProvider,
                outputDir = outputDir,
                baseFileName = "sxssf_example"
            )

            println("\t결과: $resultPath")
        }
    }

    // ==================== 3. 대용량 스트리밍 테스트 ====================

    /**
     * 대용량 스트리밍 테스트
     *
     * 스트리밍 모드에서 대용량 데이터를 처리합니다.
     * - 메모리 사용량을 최소화하면서 대량의 행 생성
     * - 실제 환경에서는 DB 스트리밍 쿼리와 연동
     */
    private fun runLargeSxssfExample(outputDir: Path) {
        println("\n[3] 대용량 스트리밍 테스트")
        println("-".repeat(40))

        val config = TbegConfig(
            streamingMode = StreamingMode.ENABLED
        )

        val dataCount = 1000

        ExcelGenerator(config).use { generator ->
            val dataProvider = simpleDataProvider {
                value("title", "템플릿 렌더링 대용량 테스트 (${dataCount}건)")
                value("date", LocalDate.now().toString())
                value("linkText", "(주)휴넷 홈페이지")
                value("url", "https://www.hunet.co.kr")
                image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
                image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

                items("employees") {
                    generateLargeDataSet(dataCount).iterator()
                }
            }

            println("\tDataProvider 생성 완료 (${dataCount}건 데이터)")

            val startTime = System.currentTimeMillis()
            val template = loadTemplate()
            val resultPath = generator.generateToFile(
                template = template,
                dataProvider = dataProvider,
                outputDir = outputDir,
                baseFileName = "large_sxssf_example"
            )
            val duration = System.currentTimeMillis() - startTime

            println("\t결과: $resultPath")
            println("\t소요시간: ${duration}ms")
        }
    }

    // ==================== 유틸리티 메서드 ====================

    private fun generateLargeDataSet(count: Int): Sequence<Employee> = sequence {
        val positions = listOf("사원", "대리", "과장", "차장", "부장")
        val names = listOf("황", "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임")

        repeat(count) { i ->
            yield(
                Employee(
                    name = "${names[i % names.size]}용호${i + 1}",
                    position = positions[i % positions.size],
                    salary = 3000 + (i % 5) * 1000
                )
            )
        }
    }

    private fun loadTemplate() =
        TemplateRenderingEngineSample::class.java.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿 파일을 찾을 수 없습니다: /templates/template.xlsx")

    private fun loadImage(fileName: String): ByteArray? =
        TemplateRenderingEngineSample::class.java.getResourceAsStream("/$fileName")?.use { it.readBytes() }

    private fun findModuleDir(): Path {
        val classLocation = TemplateRenderingEngineSample::class.java.protectionDomain.codeSource?.location
        if (classLocation != null) {
            val classPath = Path.of(classLocation.toURI())
            var current = classPath
            while (current.parent != null) {
                if (current.fileName?.toString() == "build" &&
                    Files.exists(current.resolveSibling("src"))
                ) {
                    return current.parent
                }
                current = current.parent
            }
        }

        val workingDir = Path.of("").toAbsolutePath()
        if (Files.exists(workingDir.resolve("src/main/kotlin/com/hunet/common/excel"))) {
            return workingDir
        }

        val moduleFromRoot = workingDir.resolve("modules/core/excel-generator")
        if (Files.exists(moduleFromRoot)) {
            return moduleFromRoot
        }

        return workingDir
    }
}
