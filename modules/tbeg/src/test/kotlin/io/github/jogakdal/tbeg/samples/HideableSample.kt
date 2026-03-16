package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * hideable 기능 샘플
 *
 * 동일한 템플릿에서 hideFields 지정 여부에 따라 다른 결과를 생성하는 것을 보여준다.
 *
 * 시나리오:
 * 1. 전체 컬럼 출력 (hideFields 미지정)
 * 2. 급여 컬럼 숨김 (bundle로 타이틀+합계 포함 삭제)
 * 3. 급여 + 입사일 동시 숨김
 *
 * 실행: ./gradlew :tbeg:runHideableSample
 * 결과: build/samples-hideable/
 */
object HideableSample {

    private val outputDir: Path by lazy {
        findModuleDir().resolve("build/samples-hideable")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(60))
        println("hideable 기능 샘플")
        println("=".repeat(60))

        runFullOutput()
        runHiddenColumns()
        runMultipleHidden()
        runDimMode()

        println()
        println("=".repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=".repeat(60))
    }

    /**
     * 시나리오 1: 전체 컬럼 출력 (hideFields 미지정)
     *
     * hideable 마커가 있어도 hideFields를 지정하지 않으면 일반 필드로 동작한다.
     */
    private fun runFullOutput() {
        println()
        println("시나리오 1: 전체 컬럼 출력 (hideFields 미지정)")
        println("-".repeat(40))

        val data = simpleDataProvider {
            items("employees", employeeData())
            image("logo", createLogoImage())
        }

        ExcelGenerator().use { generator ->
            val result = generator.generateToFile(
                loadTemplate("hideable_template.xlsx"), data, outputDir, "1_full_output"
            )
            println("  결과: $result")
            println("  [확인] 이름, 부서, 급여, 입사일, 나이 -- 5개 컬럼 모두 출력")
        }
    }

    /**
     * 시나리오 2: 급여 컬럼 숨김 (bundle 범위 포함 삭제)
     *
     * '급여' 필드의 hideable 마커에 bundle=C1:C4가 지정되어 있으므로,
     * 타이틀(C1), 데이터(C2), 빈 행(C3), 합계(C4) 전체가 삭제된다.
     */
    private fun runHiddenColumns() {
        println()
        println("시나리오 2: 급여 컬럼 숨김")
        println("-".repeat(40))

        val data = simpleDataProvider {
            items("employees", employeeData())
            hideFields("employees", "salary")
            image("logo", createLogoImage())
        }

        ExcelGenerator().use { generator ->
            val result = generator.generateToFile(
                loadTemplate("hideable_template.xlsx"), data, outputDir, "2_salary_hidden"
            )
            println("  결과: $result")
            println("  [확인] 급여 컬럼이 타이틀+합계와 함께 삭제되어 4개 컬럼만 출력")
        }
    }

    /**
     * 시나리오 3: 급여 + 입사일 + 나이 동시 숨김
     *
     * 여러 필드를 동시에 숨길 수 있다.
     */
    private fun runMultipleHidden() {
        println()
        println("시나리오 3: 급여 + 입사일 + 나이 동시 숨김")
        println("-".repeat(40))

        val data = simpleDataProvider {
            items("employees", employeeData())
            hideFields("employees", "salary", "hireDate", "age")
            image("logo", createLogoImage())
        }

        ExcelGenerator().use { generator ->
            val result = generator.generateToFile(
                loadTemplate("hideable_template.xlsx"), data, outputDir, "3_salary_hiredate_age_hidden"
            )
            println("  결과: $result")
            println("  [확인] 급여, 입사일, 나이 컬럼이 삭제되어 2개 컬럼(이름, 부서)만 출력")
        }
    }

    /**
     * 시나리오 4: DIM 모드 - 급여 컬럼 비활성화
     *
     * DIM 모드를 사용하면 컬럼을 삭제하는 대신 비활성화 스타일(회색 배경 + 연한 글자색)을 적용하고
     * 셀 값만 제거한다. 레이아웃 구조는 유지된다.
     *
     * 주의: 이 시나리오는 hideable_template.xlsx에 mode=dim 마커가 있는 경우에만 동작한다.
     * 현재 hideable_template.xlsx는 기본(DELETE) 모드이므로 별도 템플릿이 필요하다.
     * 여기서는 프로그래밍 방식으로 템플릿을 생성한다.
     */
    private fun runDimMode() {
        println()
        println("시나리오 4: DIM 모드 - 급여 컬럼 비활성화")
        println("-".repeat(40))

        // DIM 모드 템플릿을 프로그래밍 방식으로 생성
        val templateBytes = createDimTemplate()

        val data = simpleDataProvider {
            items("employees", employeeData())
            hideFields("employees", "salary")
        }

        ExcelGenerator().use { generator ->
            val result = generator.generateToFile(
                ByteArrayInputStream(templateBytes), data, outputDir, "4_salary_dim"
            )
            println("  결과: $result")
            println("  [확인] 급여 컬럼이 회색 배경으로 비활성화, 값만 제거 (구조 유지)")
        }
    }

    private fun createDimTemplate(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")

        val row0 = sheet.createRow(0)
        row0.createCell(0).setCellValue("이름")
        row0.createCell(1).setCellValue("부서")
        row0.createCell(2).setCellValue("급여")
        row0.createCell(3).setCellValue("입사일")
        row0.createCell(4).setCellValue("나이")

        val row1 = sheet.createRow(1)
        row1.createCell(0).setCellValue("\${emp.name}")
        row1.createCell(1).setCellValue("\${emp.dept}")
        row1.createCell(2).setCellValue("\${hideable(value=emp.salary, bundle=C1:C3, mode=dim)}")
        row1.createCell(3).setCellValue("\${emp.hireDate}")
        row1.createCell(4).setCellValue("\${emp.age}")

        val row2 = sheet.createRow(2)
        row2.createCell(0).setCellValue("합계")
        row2.createCell(2).setCellFormula("SUM(C2:C2)")

        val row3 = sheet.createRow(3)
        row3.createCell(0).setCellValue("\${repeat(employees, A2:E2, emp)}")

        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    private fun employeeData() = listOf(
        mapOf("name" to "황용호", "dept" to "개발팀", "salary" to 8500, "hireDate" to "2018-03-15", "age" to 35),
        mapOf("name" to "김민수", "dept" to "기획팀", "salary" to 6200, "hireDate" to "2020-07-01", "age" to 28),
        mapOf("name" to "이지은", "dept" to "디자인팀", "salary" to 7100, "hireDate" to "2019-11-20", "age" to 31),
        mapOf("name" to "박서준", "dept" to "개발팀", "salary" to 9200, "hireDate" to "2015-01-10", "age" to 40),
        mapOf("name" to "정하나", "dept" to "마케팅팀", "salary" to 5800, "hireDate" to "2022-04-05", "age" to 26),
    )

    private fun createLogoImage(): ByteArray {
        val img = BufferedImage(120, 40, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x33, 0x66, 0x99)
        g.fillRoundRect(0, 0, 120, 40, 10, 10)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.BOLD, 16)
        g.drawString("LOGO", 30, 27)
        g.dispose()
        return ByteArrayOutputStream().use { out -> ImageIO.write(img, "png", out); out.toByteArray() }
    }

    private fun loadTemplate(fileName: String) =
        HideableSample::class.java.getResourceAsStream("/templates/$fileName")
            ?: throw IllegalStateException("$fileName 파일을 찾을 수 없습니다")

    private fun findModuleDir(): Path {
        val classUrl = HideableSample::class.java.protectionDomain.codeSource?.location
        if (classUrl != null) {
            var path = Paths.get(classUrl.toURI())
            while (path.parent != null) {
                if (path.resolve("build.gradle.kts").toFile().exists()) return path
                path = path.parent
            }
        }
        return Paths.get("").toAbsolutePath()
    }
}
