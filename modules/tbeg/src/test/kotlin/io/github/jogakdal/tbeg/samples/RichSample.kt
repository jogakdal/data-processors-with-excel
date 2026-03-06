package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * TBEG 기능을 시각적으로 보여주는 Rich Sample.
 *
 * 분기 매출 실적 보고서를 생성하며, 다음 기능을 한 장의 스크린샷으로 보여준다:
 * - 이미지 삽입 (로고)
 * - 변수 치환 (제목, 기간, 작성자)
 * - repeat 확장 (부서별 실적 행)
 * - 수식 자동 조정 (SUM, AVERAGE 범위 확장)
 * - 조건부 서식 복제 (달성률 색상)
 * - 차트 데이터 범위 자동 반영
 * - 차트 제목 변수 치환
 * - 숫자/퍼센트 서식
 * - 자동 셀 병합 (부서/팀별 merge)
 */
object RichSample {

    data class DeptResult(val deptName: String, val revenue: Long, val cost: Long, val target: Long)
    data class ProductCategory(val category: String, val revenue: Long)
    data class Employee(
        val dept: String, val team: String, val name: String, val rank: String,
        val revenue: Long, val cost: Long, val target: Long
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val outputDir = findOutputDir()
        Files.createDirectories(outputDir)

        println("=" .repeat(60))
        println("Rich Sample - Quarterly Sales Report")
        println("=" .repeat(60))

        val provider = simpleDataProvider {
            value("reportTitle", "Q1 2026 Sales Performance Report")
            value("period", "Jan 2026 ~ Mar 2026")
            value("author", "Yongho Hwang")
            value("reportDate", LocalDate.now().toString())
            value("subtitle_emp", "Employee Performance Details")
            image("logo", loadImage("hunet_logo.png") ?: byteArrayOf())
            image("ci", loadImage("hunet_ci.png") ?: byteArrayOf())

            items("depts") {
                listOf(
                    DeptResult("Common Platform", 52000, 31000, 50000),
                    DeptResult("IT Strategy",     38000, 22000, 40000),
                    DeptResult("HR Management",   28000, 19000, 30000),
                    DeptResult("Education Biz",   95000, 61000, 90000),
                    DeptResult("Content Dev",     42000, 28000, 45000),
                ).iterator()
            }

            items("products") {
                listOf(
                    ProductCategory("Online Courses", 128000),
                    ProductCategory("Consulting", 67000),
                    ProductCategory("Certification", 45000),
                    ProductCategory("Contents License", 15000),
                ).iterator()
            }

            items("employees") {
                listOf(
                    Employee("Common Platform", "Strategy",  "Hwang Yongho",  "Manager",  18000, 11000, 17000),
                    Employee("Common Platform", "Strategy",  "Park Sungjun",   "Senior",   15000,  9000, 14000),
                    Employee("Common Platform", "Backend", "Choi Changmin",  "Senior",   12000,  7000, 13000),
                    Employee("Common Platform", "Backend", "Kim Hyunkyung",   "Junior",    7000,  4000,  6000),
                    Employee("IT Strategy",     "Planning", "Byun Jaemyung", "Manager",  20000, 12000, 20000),
                    Employee("IT Strategy",     "Planning", "Kim Minchul",  "Senior",   11000,  6000, 12000),
                    Employee("IT Strategy",     "Analysis", "Kim Minhee",    "Senior",    7000,  4000,  8000),
                    Employee("Education Biz",   "Sales",    "Yoon Seojin", "Manager",  35000, 22000, 30000),
                    Employee("Education Biz",   "Sales",    "Kang Minwoo", "Senior",   28000, 18000, 25000),
                    Employee("Education Biz",   "Sales",    "Lim Soyeon",  "Junior",   15000, 10000, 15000),
                    Employee("Education Biz",   "Support",  "Oh Junhyeok", "Senior",   17000, 11000, 20000),
                ).iterator()
            }
        }

        ExcelGenerator().use { generator ->
            val template = loadTemplate("rich_sample_template.xlsx")
            val result = generator.generateToFile(template, provider, outputDir, "quarterly_report")
            println("Result: $result")
        }

        println("=" .repeat(60))
        println("Output: ${outputDir.toAbsolutePath()}")
        println("=" .repeat(60))
    }

    private fun loadTemplate(fileName: String) =
        RichSample::class.java.getResourceAsStream("/templates/$fileName")
            ?: throw IllegalStateException("템플릿 파일을 찾을 수 없습니다: /templates/$fileName")

    private fun loadImage(fileName: String): ByteArray? =
        RichSample::class.java.getResourceAsStream("/$fileName")?.use { it.readBytes() }

    private fun findOutputDir(): Path {
        val classLocation = RichSample::class.java.protectionDomain.codeSource?.location
        if (classLocation != null) {
            var current = Path.of(classLocation.toURI())
            while (current.parent != null) {
                if (current.fileName?.toString() == "build" && Files.exists(current.resolveSibling("src"))) {
                    return current.resolveSibling("build/samples")
                }
                current = current.parent
            }
        }
        return Path.of("").toAbsolutePath().resolve("build/samples")
    }
}
