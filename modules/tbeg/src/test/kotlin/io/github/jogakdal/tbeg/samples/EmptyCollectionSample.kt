package io.github.jogakdal.tbeg.samples

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.StreamingMode
import io.github.jogakdal.tbeg.simpleDataProvider
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 빈 컬렉션 + emptyRange 기능 샘플
 *
 * empty_collection_template.xlsx 템플릿을 사용하여
 * 빈 컬렉션일 때 emptyRange 내용이 출력되는지 확인합니다.
 *
 * 실행: ./gradlew :tbeg:runEmptyCollectionSample
 * 결과: build/samples-empty-collection/
 */
object EmptyCollectionSample {

    // 모듈 디렉토리 기준 출력 경로 (IDE에서 직접 실행해도 동작하도록)
    private val outputDir: Path by lazy {
        val moduleDir = findModuleDir()
        moduleDir.resolve("build/samples-empty-collection")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=".repeat(60))
        println("빈 컬렉션 + emptyRange 샘플")
        println("=".repeat(60))

        // XSSF 모드
        runSample(StreamingMode.DISABLED, "xssf")

        // SXSSF 모드
        runSample(StreamingMode.ENABLED, "sxssf")

        println()
        println("=".repeat(60))
        println("샘플 폴더: ${outputDir.toAbsolutePath()}")
        println("=".repeat(60))
    }

    /** 모듈 디렉토리를 찾는다 (build.gradle.kts 파일이 있는 디렉토리) */
    private fun findModuleDir(): Path {
        // 현재 클래스의 리소스 위치에서 모듈 디렉토리 추정
        val classUrl = EmptyCollectionSample::class.java.protectionDomain.codeSource?.location
        if (classUrl != null) {
            var path = Paths.get(classUrl.toURI())
            // build/classes/kotlin/test 또는 build/classes/java/test에서 모듈 루트로 이동
            while (path.parent != null) {
                if (path.resolve("build.gradle.kts").toFile().exists()) {
                    return path
                }
                path = path.parent
            }
        }
        // 찾지 못하면 현재 작업 디렉토리 사용
        return Paths.get("").toAbsolutePath()
    }

    private fun runSample(streamingMode: StreamingMode, modeName: String) {
        println()
        println("[${modeName.uppercase()}] 빈 컬렉션 렌더링")
        println("-".repeat(40))

        val config = TbegConfig(streamingMode = streamingMode)
        ExcelGenerator(config).use { generator ->
            val template = loadEmptyCollectionTemplate()
            val dataProvider = createEmptyCollectionProvider()

            val resultPath = generator.generateToFile(
                template = template,
                dataProvider = dataProvider,
                outputDir = outputDir,
                baseFileName = "empty_collection_$modeName"
            )

            println("결과: $resultPath")
        }
    }

    private fun loadEmptyCollectionTemplate() =
        EmptyCollectionSample::class.java.getResourceAsStream("/templates/empty_collection_template.xlsx")
            ?: throw IllegalStateException("empty_collection_template.xlsx 파일을 찾을 수 없습니다")

    private fun createEmptyCollectionProvider() = simpleDataProvider {
        // emptyCollection: 빈 컬렉션 -> emptyRange 내용이 출력됨
        items("emptyCollection", emptyList())
    }
}
