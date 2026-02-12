@file:Suppress("NonAsciiCharacters")

package io.github.jogakdal.tbeg.engine.parser

import io.github.jogakdal.tbeg.engine.rendering.CellContent
import io.github.jogakdal.tbeg.engine.rendering.ImageSizeSpec
import io.github.jogakdal.tbeg.engine.rendering.RepeatDirection
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerDefinition
import io.github.jogakdal.tbeg.engine.rendering.parser.MarkerValidationException
import io.github.jogakdal.tbeg.engine.rendering.parser.ParameterParser
import io.github.jogakdal.tbeg.engine.rendering.parser.UnifiedMarkerParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 마커 파서 단위 테스트
 */
class MarkerParserTest {

    @Nested
    inner class ParameterParserTest {

        @Test
        fun `위치 기반 파라미터 파싱`() {
            val result = ParameterParser.parse("employees, A2:C2, emp", MarkerDefinition.REPEAT)

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `명시적 파라미터 파싱`() {
            val result = ParameterParser.parse(
                "collection=employees, range=A2:C2, var=emp",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `혼합 파라미터 사용 시 예외 발생`() {
            val exception = assertThrows<IllegalArgumentException> {
                ParameterParser.parse(
                    "employees, A2:C2, empty=A10:C10",
                    MarkerDefinition.REPEAT
                )
            }

            assertTrue(exception.message!!.contains("혼용"))
        }

        @Test
        fun `명시적 파라미터 NULL 값 처리`() {
            // b=NULL은 b를 생략한 것과 동일
            val withNull = ParameterParser.parse(
                "collection=employees, range=A2:C2, var=NULL, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            val withoutVar = ParameterParser.parse(
                "collection=employees, range=A2:C2, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            assertEquals(withNull, withoutVar)
            assertEquals("employees", withNull["collection"])
            assertEquals("A2:C2", withNull["range"])
            assertNull(withNull["var"])  // NULL은 생략과 동일
            assertEquals("DOWN", withNull["direction"])
        }

        @Test
        fun `명시적 파라미터 null 값 처리 - 대소문자 무관`() {
            val result1 = ParameterParser.parse("collection=employees, var=NULL", MarkerDefinition.REPEAT)
            val result2 = ParameterParser.parse("collection=employees, var=null", MarkerDefinition.REPEAT)
            val result3 = ParameterParser.parse("collection=employees, var=Null", MarkerDefinition.REPEAT)

            assertEquals(result1, result2)
            assertEquals(result2, result3)
            assertNull(result1["var"])
        }

        @Test
        fun `명시적 파라미터 빈 값 처리 - name= 형식`() {
            // var= (빈 값)은 var=NULL과 동일
            val withEmpty = ParameterParser.parse(
                "collection=employees, range=A2:C2, var=, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            val withNull = ParameterParser.parse(
                "collection=employees, range=A2:C2, var=NULL, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            val withoutVar = ParameterParser.parse(
                "collection=employees, range=A2:C2, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            assertEquals(withEmpty, withNull)
            assertEquals(withNull, withoutVar)
            assertNull(withEmpty["var"])
        }

        @Test
        fun `따옴표 처리 - 큰따옴표`() {
            val result = ParameterParser.parse(
                """"employees", "A2:C2", "emp"""",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `따옴표 처리 - 작은따옴표`() {
            val result = ParameterParser.parse(
                "'employees', 'A2:C2', 'emp'",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `따옴표 처리 - 백틱`() {
            val result = ParameterParser.parse(
                "`employees`, `A2:C2`, `emp`",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `파라미터 별칭 처리 - variable`() {
            val result = ParameterParser.parse(
                "collection=employees, range=A2:C2, variable=emp",
                MarkerDefinition.REPEAT
            )

            // variable은 var의 별칭이므로 var로 정규화됨
            assertEquals("emp", result["var"])
        }

        @Test
        fun `파라미터 별칭 처리 - emptyRange`() {
            val result = ParameterParser.parse(
                "collection=employees, range=A2:C2, emptyRange=A10:C10",
                MarkerDefinition.REPEAT
            )

            // emptyRange는 empty의 별칭이므로 empty로 정규화됨
            assertEquals("A10:C10", result["empty"])
        }

        @Test
        fun `이미지 마커 파라미터 파싱`() {
            val result = ParameterParser.parse("logo, B2, 200:150", MarkerDefinition.IMAGE)

            assertEquals("logo", result["name"])
            assertEquals("B2", result["position"])
            assertEquals("200:150", result["size"])
        }

        @Test
        fun `이미지 마커 명시적 파라미터`() {
            val result = ParameterParser.parse(
                "name=logo, position=B2:D4, size=fit",
                MarkerDefinition.IMAGE
            )

            assertEquals("logo", result["name"])
            assertEquals("B2:D4", result["position"])
            assertEquals("fit", result["size"])
        }

        @Test
        fun `size 마커 파라미터 파싱`() {
            val result = ParameterParser.parse("employees", MarkerDefinition.SIZE)

            assertEquals("employees", result["collection"])
        }

        @Test
        fun `size 마커 명시적 파라미터`() {
            val result = ParameterParser.parse("collection=employees", MarkerDefinition.SIZE)

            assertEquals("employees", result["collection"])
        }

        @Test
        fun `공백 처리`() {
            val result = ParameterParser.parse(
                "  employees  ,  A2:C2  ,  emp  ",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
        }

        @Test
        fun `명시적 파라미터 공백 처리`() {
            val result = ParameterParser.parse(
                "collection = employees , range = A2:C2",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
        }

        @Test
        fun `명시적 파라미터 순서 무관 - repeat`() {
            // 정방향
            val result1 = ParameterParser.parse(
                "collection=employees, range=A2:C2, var=emp, direction=DOWN",
                MarkerDefinition.REPEAT
            )

            // 역순
            val result2 = ParameterParser.parse(
                "direction=DOWN, var=emp, range=A2:C2, collection=employees",
                MarkerDefinition.REPEAT
            )

            // 랜덤 순서
            val result3 = ParameterParser.parse(
                "var=emp, collection=employees, direction=DOWN, range=A2:C2",
                MarkerDefinition.REPEAT
            )

            // 모두 동일한 결과
            assertEquals(result1, result2)
            assertEquals(result2, result3)
            assertEquals("employees", result1["collection"])
            assertEquals("A2:C2", result1["range"])
            assertEquals("emp", result1["var"])
            assertEquals("DOWN", result1["direction"])
        }

        @Test
        fun `명시적 파라미터 순서 무관 - image`() {
            val result1 = ParameterParser.parse(
                "name=logo, position=B2, size=200:150",
                MarkerDefinition.IMAGE
            )

            val result2 = ParameterParser.parse(
                "size=200:150, name=logo, position=B2",
                MarkerDefinition.IMAGE
            )

            assertEquals(result1, result2)
        }

        @Test
        fun `위치 기반 파라미터 중간 생략`() {
            // repeat(collection, range, var, direction, empty)에서 direction 생략
            val result = ParameterParser.parse(
                "employees, A2:C2, emp, , A10:C10",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertEquals("emp", result["var"])
            assertNull(result["direction"])  // 생략됨
            assertEquals("A10:C10", result["empty"])
        }

        @Test
        fun `위치 기반 파라미터 여러 개 생략`() {
            // repeat(collection, range, var, direction, empty)에서 var, direction 생략
            val result = ParameterParser.parse(
                "employees, A2:C2, , , A10:C10",
                MarkerDefinition.REPEAT
            )

            assertEquals("employees", result["collection"])
            assertEquals("A2:C2", result["range"])
            assertNull(result["var"])  // 생략됨
            assertNull(result["direction"])  // 생략됨
            assertEquals("A10:C10", result["empty"])
        }

        @Test
        fun `위치 기반 파라미터 image 중간 생략`() {
            // image(name, position, size)에서 position 생략
            val result = ParameterParser.parse(
                "logo, , 200:150",
                MarkerDefinition.IMAGE
            )

            assertEquals("logo", result["name"])
            assertNull(result["position"])  // 생략됨
            assertEquals("200:150", result["size"])
        }
    }

    @Nested
    inner class UnifiedMarkerParserTest {

        @Test
        fun `텍스트 repeat 마커 파싱 - 기본`() {
            val content = UnifiedMarkerParser.parse(
                "\${repeat(employees, A2:C2, emp)}",
                isFormula = false
            )

            assertTrue(content is CellContent.RepeatMarker)
            val marker = content as CellContent.RepeatMarker
            assertEquals("employees", marker.collection)
            assertEquals("A2:C2", marker.range)
            assertEquals("emp", marker.variable)
            assertEquals(RepeatDirection.DOWN, marker.direction)
            assertNull(marker.emptyRange)
        }

        @Test
        fun `텍스트 repeat 마커 파싱 - direction과 empty`() {
            val content = UnifiedMarkerParser.parse(
                "\${repeat(employees, A2:C2, emp, RIGHT, A10:C10)}",
                isFormula = false
            )

            assertTrue(content is CellContent.RepeatMarker)
            val marker = content as CellContent.RepeatMarker
            assertEquals(RepeatDirection.RIGHT, marker.direction)
            assertEquals("A10:C10", marker.emptyRange)
        }

        @Test
        fun `텍스트 repeat 마커 파싱 - 명시적 파라미터`() {
            val content = UnifiedMarkerParser.parse(
                "\${repeat(collection=employees, range=A2:C2, var=emp, direction=DOWN, empty=A10:C10)}",
                isFormula = false
            )

            assertTrue(content is CellContent.RepeatMarker)
            val marker = content as CellContent.RepeatMarker
            assertEquals("employees", marker.collection)
            assertEquals("A2:C2", marker.range)
            assertEquals("emp", marker.variable)
            assertEquals(RepeatDirection.DOWN, marker.direction)
            assertEquals("A10:C10", marker.emptyRange)
        }

        @Test
        fun `수식 repeat 마커 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "TBEG_REPEAT(employees, A2:C2, emp)",
                isFormula = true
            )

            assertTrue(content is CellContent.RepeatMarker)
            val marker = content as CellContent.RepeatMarker
            assertEquals("employees", marker.collection)
        }

        @Test
        fun `수식 repeat 마커 파싱 - = 포함`() {
            val content = UnifiedMarkerParser.parse(
                "=TBEG_REPEAT(employees, A2:C2, emp)",
                isFormula = true
            )

            assertTrue(content is CellContent.RepeatMarker)
        }

        @Test
        fun `텍스트 image 마커 파싱 - 기본`() {
            val content = UnifiedMarkerParser.parse(
                "\${image(logo)}",
                isFormula = false
            )

            assertTrue(content is CellContent.ImageMarker)
            val marker = content as CellContent.ImageMarker
            assertEquals("logo", marker.imageName)
            assertNull(marker.position)
            assertEquals(ImageSizeSpec.FIT_TO_CELL, marker.sizeSpec)
        }

        @Test
        fun `텍스트 image 마커 파싱 - 위치와 크기`() {
            val content = UnifiedMarkerParser.parse(
                "\${image(logo, B2, 200:150)}",
                isFormula = false
            )

            assertTrue(content is CellContent.ImageMarker)
            val marker = content as CellContent.ImageMarker
            assertEquals("logo", marker.imageName)
            assertEquals("B2", marker.position)
            assertEquals(ImageSizeSpec(200, 150), marker.sizeSpec)
        }

        @Test
        fun `텍스트 image 마커 파싱 - 명시적 파라미터`() {
            val content = UnifiedMarkerParser.parse(
                "\${image(name=logo, position=B2:D4, size=original)}",
                isFormula = false
            )

            assertTrue(content is CellContent.ImageMarker)
            val marker = content as CellContent.ImageMarker
            assertEquals("logo", marker.imageName)
            assertEquals("B2:D4", marker.position)
            assertEquals(ImageSizeSpec.ORIGINAL, marker.sizeSpec)
        }

        @Test
        fun `수식 image 마커 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "TBEG_IMAGE(logo, B2)",
                isFormula = true
            )

            assertTrue(content is CellContent.ImageMarker)
            val marker = content as CellContent.ImageMarker
            assertEquals("logo", marker.imageName)
            assertEquals("B2", marker.position)
        }

        @Test
        fun `텍스트 size 마커 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "\${size(employees)}",
                isFormula = false
            )

            assertTrue(content is CellContent.SizeMarker)
            val marker = content as CellContent.SizeMarker
            assertEquals("employees", marker.collectionName)
        }

        @Test
        fun `텍스트 size 마커 파싱 - 명시적 파라미터`() {
            val content = UnifiedMarkerParser.parse(
                "\${size(collection=employees)}",
                isFormula = false
            )

            assertTrue(content is CellContent.SizeMarker)
            val marker = content as CellContent.SizeMarker
            assertEquals("employees", marker.collectionName)
        }

        @Test
        fun `수식 size 마커 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "TBEG_SIZE(employees)",
                isFormula = true
            )

            assertTrue(content is CellContent.SizeMarker)
        }

        @Test
        fun `단순 변수 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "\${title}",
                isFormula = false
            )

            assertTrue(content is CellContent.Variable)
            val variable = content as CellContent.Variable
            assertEquals("title", variable.variableName)
        }

        @Test
        fun `아이템 필드 파싱`() {
            val content = UnifiedMarkerParser.parse(
                "\${emp.name}",
                isFormula = false,
                repeatItemVariables = setOf("emp")
            )

            assertTrue(content is CellContent.ItemField)
            val field = content as CellContent.ItemField
            assertEquals("emp", field.itemVariable)
            assertEquals("name", field.fieldPath)
        }

        @Test
        fun `아이템 필드 파싱 - 중첩 필드`() {
            val content = UnifiedMarkerParser.parse(
                "\${emp.department.name}",
                isFormula = false,
                repeatItemVariables = setOf("emp")
            )

            assertTrue(content is CellContent.ItemField)
            val field = content as CellContent.ItemField
            assertEquals("emp", field.itemVariable)
            assertEquals("department.name", field.fieldPath)
        }

        @Test
        fun `수식 - 변수 포함`() {
            val content = UnifiedMarkerParser.parse(
                """HYPERLINK("${"\${url}"}", "${"\${text}"}")""",
                isFormula = true
            )

            assertTrue(content is CellContent.FormulaWithVariables)
            val formula = content as CellContent.FormulaWithVariables
            assertTrue("url" in formula.variableNames)
            assertTrue("text" in formula.variableNames)
        }

        @Test
        fun `수식 - 변수 없음`() {
            val content = UnifiedMarkerParser.parse(
                "SUM(A1:A10)",
                isFormula = true
            )

            assertTrue(content is CellContent.Formula)
        }

        @Test
        fun `정적 문자열`() {
            val content = UnifiedMarkerParser.parse(
                "일반 텍스트",
                isFormula = false
            )

            assertTrue(content is CellContent.StaticString)
            assertEquals("일반 텍스트", (content as CellContent.StaticString).value)
        }

        @Test
        fun `빈 문자열`() {
            val content = UnifiedMarkerParser.parse("", isFormula = false)
            assertTrue(content is CellContent.Empty)
        }

        @Test
        fun `null 입력`() {
            val content = UnifiedMarkerParser.parse(null, isFormula = false)
            assertTrue(content is CellContent.Empty)
        }
    }

    @Nested
    inner class ParameterValidationTest {

        @Test
        fun `repeat - direction에 범위 형식이 들어오면 오류 발생`() {
            val exception = assertThrows<MarkerValidationException> {
                UnifiedMarkerParser.parse(
                    "TBEG_REPEAT(emptyCollection, A6:C6, emp, A13:C13)",
                    isFormula = true
                )
            }

            assertTrue(exception.message!!.contains("direction"))
            assertTrue(exception.message!!.contains("DOWN"))
            assertTrue(exception.message!!.contains("RIGHT"))
        }

        @Test
        fun `repeat - direction이 DOWN 또는 RIGHT이면 정상 처리`() {
            val contentDown = UnifiedMarkerParser.parse(
                "TBEG_REPEAT(list, A1:C1, item, DOWN, A10:C10)",
                isFormula = true
            ) as CellContent.RepeatMarker
            assertEquals(RepeatDirection.DOWN, contentDown.direction)
            assertEquals("A10:C10", contentDown.emptyRange)

            val contentRight = UnifiedMarkerParser.parse(
                "TBEG_REPEAT(list, A1:C1, item, RIGHT)",
                isFormula = true
            ) as CellContent.RepeatMarker
            assertEquals(RepeatDirection.RIGHT, contentRight.direction)
        }

        @Test
        fun `repeat - direction 생략 시 empty 파라미터 정상 처리`() {
            val content = UnifiedMarkerParser.parse(
                "TBEG_REPEAT(list, A1:C1, item, , A10:C10)",
                isFormula = true
            ) as CellContent.RepeatMarker

            assertEquals(RepeatDirection.DOWN, content.direction)
            assertEquals("A10:C10", content.emptyRange)
        }

        @Test
        fun `repeat - range에 잘못된 형식이 들어오면 오류 발생`() {
            val exception = assertThrows<MarkerValidationException> {
                UnifiedMarkerParser.parse(
                    "\${repeat(list, invalid-range, item)}",
                    isFormula = false
                )
            }

            assertTrue(exception.message!!.contains("range"))
        }

        @Test
        fun `repeat - 시트 참조 범위 정상 처리`() {
            val content = UnifiedMarkerParser.parse(
                "\${repeat(list, Sheet1!A1:C3, item, DOWN, 'Data Sheet'!A10:C10)}",
                isFormula = false
            ) as CellContent.RepeatMarker

            assertEquals("Sheet1!A1:C3", content.range)
            assertEquals("'Data Sheet'!A10:C10", content.emptyRange)
        }

        @Test
        fun `image - size에 잘못된 형식이 들어오면 오류 발생`() {
            val exception = assertThrows<MarkerValidationException> {
                UnifiedMarkerParser.parse(
                    "\${image(logo, B2, invalid)}",
                    isFormula = false
                )
            }

            assertTrue(exception.message!!.contains("size"))
        }

        @Test
        fun `image - 유효한 size 형식 정상 처리`() {
            // fit
            val fitContent = UnifiedMarkerParser.parse(
                "\${image(logo, B2, fit)}",
                isFormula = false
            ) as CellContent.ImageMarker
            assertEquals(ImageSizeSpec.FIT_TO_CELL, fitContent.sizeSpec)

            // original
            val originalContent = UnifiedMarkerParser.parse(
                "\${image(logo, B2, original)}",
                isFormula = false
            ) as CellContent.ImageMarker
            assertEquals(ImageSizeSpec.ORIGINAL, originalContent.sizeSpec)

            // 숫자:숫자
            val customContent = UnifiedMarkerParser.parse(
                "\${image(logo, B2, 200:150)}",
                isFormula = false
            ) as CellContent.ImageMarker
            assertEquals(ImageSizeSpec(200, 150), customContent.sizeSpec)
        }
    }
}
