package io.github.jogakdal.tbeg.engine

import io.github.jogakdal.tbeg.ExcelGenerator
import io.github.jogakdal.tbeg.TbegConfig
import io.github.jogakdal.tbeg.simpleDataProvider
import com.sun.net.httpserver.HttpServer
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.imageio.ImageIO

/**
 * 이미지 URL 다운로드 기능 테스트.
 *
 * 로컬 HTTP 서버를 띄워 이미지 URL을 제공하고,
 * TBEG가 URL에서 이미지를 다운로드하여 Excel에 삽입하는지 검증한다.
 */
class ImageUrlTest {

    companion object {
        private lateinit var server: HttpServer
        private var port: Int = 0
        private val testPng = createTestPng()

        private fun createTestPng(): ByteArray {
            val image = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.color = Color.BLUE
            g.fillRect(0, 0, 10, 10)
            g.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(image, "PNG", it) }.toByteArray()
        }

        @JvmStatic
        @BeforeAll
        fun startServer() {
            server = HttpServer.create(InetSocketAddress(0), 0)
            port = server.address.port

            // 정상 이미지 응답
            server.createContext("/image.png") { exchange ->
                exchange.responseHeaders.add("Content-Type", "image/png")
                exchange.sendResponseHeaders(200, testPng.size.toLong())
                exchange.responseBody.use { it.write(testPng) }
            }

            // 리다이렉트 응답
            server.createContext("/redirect") { exchange ->
                exchange.responseHeaders.add("Location", "http://localhost:$port/image.png")
                exchange.sendResponseHeaders(302, -1)
            }

            // 404 응답
            server.createContext("/notfound") { exchange ->
                exchange.sendResponseHeaders(404, -1)
            }

            server.start()
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.stop(0)
        }
    }

    private fun createTemplateWithImageMarker(): ByteArray {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        sheet.createRow(0).createCell(0).setCellValue("\${image(logo)}")
        return ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }
    }

    @Test
    fun `URL 문자열로 이미지를 삽입할 수 있다`() {
        val template = createTemplateWithImageMarker()
        val dataProvider = simpleDataProvider {
            imageUrl("logo", "http://localhost:$port/image.png")
        }

        ExcelGenerator().use { generator ->
            val result = generator.generate(
                ByteArrayInputStream(template), dataProvider
            )

            // 생성된 Excel에 이미지가 포함되어 있는지 확인
            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                val pictures = workbook.allPictures
                assertEquals(1, pictures.size, "이미지가 1개 삽입되어야 한다")
                assertTrue(pictures[0].mimeType.contains("png"), "PNG 이미지여야 한다")
            }
        }
    }

    @Test
    fun `리다이렉트 URL도 이미지를 다운로드할 수 있다`() {
        val template = createTemplateWithImageMarker()
        val dataProvider = simpleDataProvider {
            imageUrl("logo", "http://localhost:$port/redirect")
        }

        ExcelGenerator().use { generator ->
            val result = generator.generate(
                ByteArrayInputStream(template), dataProvider
            )

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                assertEquals(1, workbook.allPictures.size)
            }
        }
    }

    @Test
    fun `다운로드 실패 시 이미지 없이 정상 생성된다`() {
        val template = createTemplateWithImageMarker()
        val dataProvider = simpleDataProvider {
            imageUrl("logo", "http://localhost:$port/notfound")
        }

        ExcelGenerator().use { generator ->
            val result = generator.generate(
                ByteArrayInputStream(template), dataProvider
            )

            XSSFWorkbook(ByteArrayInputStream(result)).use { workbook ->
                assertEquals(0, workbook.allPictures.size, "다운로드 실패 시 이미지가 없어야 한다")
            }
        }
    }

    @Test
    fun `ByteArray 이미지와 URL 이미지를 혼합 사용할 수 있다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        sheet.createRow(0).createCell(0).setCellValue("\${image(logo)}")
        sheet.createRow(1).createCell(0).setCellValue("\${image(banner)}")
        val template = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val dataProvider = simpleDataProvider {
            imageUrl("logo", "http://localhost:$port/image.png")
            image("banner", testPng)
        }

        ExcelGenerator().use { generator ->
            val result = generator.generate(
                ByteArrayInputStream(template), dataProvider
            )

            XSSFWorkbook(ByteArrayInputStream(result)).use { wb ->
                assertEquals(2, wb.allPictures.size, "URL 이미지와 ByteArray 이미지 모두 삽입되어야 한다")
            }
        }
    }

    @Test
    fun `TTL 캐시가 활성화되면 동일 URL을 재다운로드하지 않는다`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Sheet1")
        sheet.createRow(0).createCell(0).setCellValue("\${image(img1)}")
        sheet.createRow(1).createCell(0).setCellValue("\${image(img2)}")
        val template = ByteArrayOutputStream().use { out ->
            workbook.write(out)
            workbook.close()
            out.toByteArray()
        }

        val url = "http://localhost:$port/image.png"
        val dataProvider = simpleDataProvider {
            imageUrl("img1", url)
            imageUrl("img2", url)
        }

        val config = TbegConfig(imageUrlCacheTtlSeconds = 60)

        ExcelGenerator(config).use { generator ->
            val result = generator.generate(
                ByteArrayInputStream(template), dataProvider
            )

            XSSFWorkbook(ByteArrayInputStream(result)).use { wb ->
                assertEquals(2, wb.allPictures.size, "같은 URL이라도 두 이미지 모두 삽입되어야 한다")
            }
        }
    }
}
