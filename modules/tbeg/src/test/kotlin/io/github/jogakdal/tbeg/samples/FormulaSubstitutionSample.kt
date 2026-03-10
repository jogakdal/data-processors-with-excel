package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 변수형 마커 수식 치환 샘플
 *
 * 변수형 마커에 "="로 시작하는 값을 바인딩하면 Excel 수식으로 처리되는 기능을 보여준다.
 * repeat에 의한 수식 범위 자동 조정도 적용된다.
 *
 * 실행: ./gradlew :tbeg:runFormulaSubstitutionSample
 * 결과: build/samples-formula-substitution/
 */
object FormulaSubstitutionSample {

    private val outputDir: Path by lazy {
        findModuleDir().resolve("build/samples-formula-substitution")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(60))
        println("변수형 마커 수식 치환 샘플")
        println("=".repeat(60))

        runSimpleSample()
        runRepeatWithFormulaSample()

        println()
        println("=".repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=".repeat(60))
    }

    /**
     * 시나리오 1: 단순 변수 마커에 수식 바인딩
     *
     * 동일한 템플릿에 다른 수식을 바인딩하여 두 가지 결과를 생성한다.
     */
    private fun runSimpleSample() {
        println()
        println("시나리오 1: 단순 변수 마커에 수식 바인딩")
        println("-".repeat(40))

        ExcelGenerator().use { generator ->
            // 결과 1: SUM 수식
            val sumData = simpleDataProvider {
                value("title", "월간 매출 집계 (SUM)")
                value("formula", "=SUM(B2:B4)")
            }
            val result1 = generator.generateToFile(
                loadTemplate("formula_simple_template.xlsx"), sumData, outputDir, "1_simple_result_sum"
            )
            println("  SUM 결과: $result1")

            // 결과 2: AVERAGE 수식
            val avgData = simpleDataProvider {
                value("title", "월간 매출 평균 (AVERAGE)")
                value("formula", "=AVERAGE(B2:B4)")
            }
            val result2 = generator.generateToFile(
                loadTemplate("formula_simple_template.xlsx"), avgData, outputDir, "1_simple_result_average"
            )
            println("  AVERAGE 결과: $result2")
        }
    }

    /**
     * 시나리오 2: repeat + 수식 범위 자동 조정
     *
     * repeat으로 행이 확장될 때 변수형 마커로 치환된 수식의 범위도 자동 조정되는 것을 보여준다.
     * 아이템 필드에서의 수식 바인딩도 포함한다.
     */
    private fun runRepeatWithFormulaSample() {
        println()
        println("시나리오 2: repeat + 수식 범위 자동 조정")
        println("-".repeat(40))

        ExcelGenerator().use { generator ->
            val data = simpleDataProvider {
                value("title", "부서별 매출 실적")
                // 집계 수식: repeat 확장 후 범위가 자동 조정됨
                // repeat 바로 아래 행의 집계 마커
                value("totalRevenue", "=SUM(C3:C3)")
                value("totalTarget", "=SUM(D3:D3)")
                value("avrRate", "=AVERAGE(E3:E3)")
                // 빈 행 아래의 집계 마커
                value("totalFormula", "=SUM(C3:C3)")
                value("avgFormula", "=AVERAGE(C3:C3)")
                value("maxFormula", "=MAX(C3:C3)")

                items("sales") {
                    listOf(
                        mapOf(
                            "dept" to "영업1팀",
                            "product" to "솔루션A",
                            "amount" to 15000,
                            "target" to 20000,
                            // 아이템 필드에서 수식 바인딩: 달성률 = 실적/목표
                            "rateFormula" to "=C3/D3"
                        ),
                        mapOf(
                            "dept" to "영업2팀",
                            "product" to "솔루션B",
                            "amount" to 22000,
                            "target" to 18000,
                            "rateFormula" to "=C3/D3"
                        ),
                        mapOf(
                            "dept" to "영업3팀",
                            "product" to "플랫폼",
                            "amount" to 18500,
                            "target" to 25000,
                            "rateFormula" to "=C3/D3"
                        ),
                        mapOf(
                            "dept" to "마케팅팀",
                            "product" to "광고",
                            "amount" to 9800,
                            "target" to 12000,
                            "rateFormula" to "=C3/D3"
                        ),
                        mapOf(
                            "dept" to "해외사업팀",
                            "product" to "컨설팅",
                            "amount" to 31000,
                            "target" to 30000,
                            "rateFormula" to "=C3/D3"
                        ),
                    ).iterator()
                }
            }

            val resultPath = generator.generateToFile(
                loadTemplate("formula_repeat_template.xlsx"), data, outputDir, "2_repeat_result"
            )
            println("  결과: $resultPath")
            println()
            println("  [확인 포인트]")
            println("  - E3~E7: 달성률 수식(=C3/D3)이 행마다 =C3/D3, =C4/D4, ... 로 시프트")
            println("  - C8: =SUM(C3:C7), D8: =SUM(D3:D7), E8: =AVERAGE(E3:E7) (repeat 바로 아래 집계)")
            println("  - C10: =SUM(C3:C7), C11: =AVERAGE(C3:C7), C12: =MAX(C3:C7) (하단 집계)")
        }
    }

    private fun loadTemplate(fileName: String) =
        FormulaSubstitutionSample::class.java.getResourceAsStream("/templates/$fileName")
            ?: throw IllegalStateException("$fileName 파일을 찾을 수 없습니다")

    private fun findModuleDir(): Path {
        val classUrl = FormulaSubstitutionSample::class.java.protectionDomain.codeSource?.location
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
