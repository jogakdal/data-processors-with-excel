package io.github.jogakdal.tbeg.spring

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.SimpleDataProvider
import io.github.jogakdal.tbeg.StreamingMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

/**
 * TbegAutoConfiguration 테스트.
 */
class TbegAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TbegAutoConfiguration::class.java))

    @Test
    fun `default configuration should create ExcelGenerator bean`() {
        contextRunner.run { context ->
            assertTrue(context.containsBean("excelGenerator"))
            assertTrue(context.containsBean("tbegConfig"))

            val generator = context.getBean(ExcelGenerator::class.java)
            assertNotNull(generator)

            val config = context.getBean(TbegConfig::class.java)
            assertEquals(StreamingMode.ENABLED, config.streamingMode)

            generator.close()
        }
    }

    @Test
    fun `properties should be applied to config`() {
        contextRunner
            .withPropertyValues(
                "hunet.tbeg.streaming-mode=enabled",
                "hunet.tbeg.timestamp-format=yyyy-MM-dd",
                "hunet.tbeg.progress-report-interval=200"
            )
            .run { context ->
                val config = context.getBean(TbegConfig::class.java)

                assertEquals(StreamingMode.ENABLED, config.streamingMode)
                assertEquals("yyyy-MM-dd", config.timestampFormat)
                assertEquals(200, config.progressReportInterval)

                context.getBean(ExcelGenerator::class.java).close()
            }
    }

    @Test
    fun `custom bean should override auto-configuration`() {
        contextRunner
            .withUserConfiguration(CustomTbegConfig::class.java)
            .run { context ->
                val config = context.getBean(TbegConfig::class.java)

                // 커스텀 설정이 적용되어야 함
                assertEquals(StreamingMode.ENABLED, config.streamingMode)
                assertEquals(500, config.progressReportInterval)

                context.getBean(ExcelGenerator::class.java).close()
            }
    }

    @Test
    fun `should generate excel file using auto-configured bean`(@TempDir tempDir: Path) {
        contextRunner.run { context ->
            val generator = context.getBean(ExcelGenerator::class.java)

            // 템플릿 로드
            val templateStream = javaClass.getResourceAsStream("/templates/template.xlsx")
                ?: throw IllegalStateException("템플릿을 찾을 수 없습니다")

            // 이미지 로드
            val logo = javaClass.getResourceAsStream("/hunet_logo.png")?.readBytes()
            val ci = javaClass.getResourceAsStream("/hunet_ci.png")?.readBytes()

            // 데이터 준비
            data class Employee(val name: String, val position: String, val salary: Int)

            val data = mutableMapOf<String, Any>(
                "title" to "Spring Boot 테스트",
                "date" to "2024-01-06",
                "employees" to listOf(
                    Employee("황용호", "과장", 5000),
                    Employee("홍용호", "대리", 4000)
                )
            )
            logo?.let { data["logo"] = it }
            ci?.let { data["ci"] = it }

            // Excel 생성
            val resultPath = generator.generateToFile(
                template = templateStream,
                dataProvider = SimpleDataProvider.of(data),
                outputDir = tempDir,
                baseFileName = "spring_test"
            )

            // 검증
            assertTrue(Files.exists(resultPath))
            assertTrue(Files.size(resultPath) > 0)
            assertTrue(resultPath.fileName.toString().startsWith("spring_test_"))
            assertTrue(resultPath.fileName.toString().endsWith(".xlsx"))

            generator.close()
        }
    }

    @Configuration
    class CustomTbegConfig {
        @Bean
        fun tbegConfig(): TbegConfig = TbegConfig(
            streamingMode = StreamingMode.ENABLED,
            progressReportInterval = 500
        )
    }
}
