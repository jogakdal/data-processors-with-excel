package io.github.jogakdal.tbeg.spring

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * TBEG (Template Based Excel Generator) Auto-Configuration.
 *
 * ExcelGenerator 클래스가 클래스패스에 있을 때 자동으로 Bean을 등록한다.
 *
 * ## 기본 사용법
 * 의존성만 추가하면 자동으로 ExcelGenerator Bean이 등록된다:
 * ```kotlin
 * @Service
 * class ReportService(
 *     private val excelGenerator: ExcelGenerator
 * ) {
 *     fun generate(data: Map<String, Any>): ByteArray {
 *         return excelGenerator.generate(templateStream, data)
 *     }
 * }
 * ```
 *
 * ## 설정 커스터마이징
 * application.yml에서 설정 가능:
 * ```yaml
 * tbeg:
 *   streaming-mode: auto
 *   streaming-row-threshold: 1000
 * ```
 *
 * ## Bean 커스터마이징
 * 직접 Bean을 정의하면 자동 설정이 비활성화된다:
 * ```kotlin
 * @Configuration
 * class CustomTbegConfig {
 *     @Bean
 *     fun excelGenerator(): ExcelGenerator {
 *         return ExcelGenerator(TbegConfig.forLargeData())
 *     }
 * }
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(ExcelGenerator::class)
@EnableConfigurationProperties(TbegProperties::class)
class TbegAutoConfiguration {

    /**
     * TbegConfig Bean.
     *
     * 프로퍼티 설정을 기반으로 Config를 생성한다.
     * 사용자가 직접 정의한 Bean이 있으면 이 Bean은 생성되지 않습니다.
     */
    @Bean
    @ConditionalOnMissingBean
    fun tbegConfig(properties: TbegProperties) = properties.toConfig()

    /**
     * ExcelGenerator Bean.
     *
     * destroyMethod로 close()가 자동 호출되어 리소스가 정리된다.
     * 사용자가 직접 정의한 Bean이 있으면 이 Bean은 생성되지 않습니다.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    fun excelGenerator(config: TbegConfig) = ExcelGenerator(config)
}
