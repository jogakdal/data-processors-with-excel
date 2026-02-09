package io.github.jogakdal.tbeg

import java.io.InputStream

/**
 * 테스트에서 공통으로 사용하는 데이터 클래스 및 유틸리티
 */
object TestUtils {

    /**
     * 테스트용 직원 데이터 클래스
     */
    data class Employee(val name: String, val position: String, val salary: Int)

    /**
     * 테스트용 부서 데이터 클래스
     */
    data class Department(val name: String, val members: Int, val office: String)

    /**
     * 기본 템플릿 로드
     */
    fun loadTemplate(): InputStream =
        TestUtils::class.java.getResourceAsStream("/templates/template.xlsx")
            ?: throw IllegalStateException("템플릿 파일을 찾을 수 없습니다")

    /**
     * 빈 컬렉션 테스트용 템플릿 로드
     */
    fun loadEmptyCollectionTemplate(): InputStream =
        TestUtils::class.java.getResourceAsStream("/templates/empty_collection_template.xlsx")
            ?: throw IllegalStateException("빈 컬렉션 템플릿 파일을 찾을 수 없습니다")

    /**
     * 이미지 파일 로드
     */
    fun loadImage(fileName: String): ByteArray? =
        TestUtils::class.java.getResourceAsStream("/$fileName")?.use { it.readBytes() }

    /**
     * 대용량 테스트 데이터 생성
     */
    fun generateEmployees(count: Int): List<Employee> {
        val positions = listOf("사원", "대리", "과장", "차장", "부장")
        val names = listOf("황", "김", "이", "박", "최", "정", "강", "조", "윤", "장")
        return (1..count).map { i ->
            Employee(
                name = "${names[i % names.size]}용호$i",
                position = positions[i % positions.size],
                salary = 3000 + (i % 5) * 1000
            )
        }
    }

    /**
     * 테스트용 부서 목록 생성
     */
    fun generateDepartments(): List<Department> = listOf(
        Department("개발팀", 15, "본관 3층"),
        Department("기획팀", 8, "본관 2층"),
        Department("인사팀", 5, "별관 1층")
    )
}
