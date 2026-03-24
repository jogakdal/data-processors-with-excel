package io.github.jogakdal.excelart

/**
 * 양자화된 이미지에서 동일 색상이 연속된 직사각형 영역을 찾아 병합 영역 목록을 생성한다.
 */
object CellMergeOptimizer {

    data class MergeRegion(
        val startRow: Int,
        val startCol: Int,
        val endRow: Int,
        val endCol: Int,
        val rgb: Int
    ) {
        val width get() = endCol - startCol + 1
        val height get() = endRow - startRow + 1
        val cellCount get() = width * height
    }

    /**
     * 양자화된 색상 배열에서 병합 가능한 직사각형 영역을 찾는다.
     * @param pixels 양자화된 RGB 값의 2D 배열 [row][col]
     * @return 병합 영역 목록 (1x1 셀은 제외)
     */
    fun findMergeRegions(pixels: Array<IntArray>): List<MergeRegion> {
        val height = pixels.size
        if (height == 0) return emptyList()
        val width = pixels[0].size

        val visited = Array(height) { BooleanArray(width) }
        val regions = mutableListOf<MergeRegion>()

        for (row in 0 until height) {
            for (col in 0 until width) {
                if (visited[row][col]) continue

                val rgb = pixels[row][col]

                // 오른쪽으로 같은 색상이 연속된 최대 너비 계산
                var maxWidth = 1
                while (col + maxWidth < width && !visited[row][col + maxWidth] && pixels[row][col + maxWidth] == rgb) {
                    maxWidth++
                }

                // 아래로 확장: 동일 너비의 같은 색상 행이 이어지는지 확인
                var maxHeight = 1
                outer@ while (row + maxHeight < height) {
                    for (c in col until col + maxWidth) {
                        if (visited[row + maxHeight][c] || pixels[row + maxHeight][c] != rgb) break@outer
                    }
                    maxHeight++
                }

                // 방문 표시
                for (r in row until row + maxHeight) {
                    for (c in col until col + maxWidth) {
                        visited[r][c] = true
                    }
                }

                // 1x1은 병합 불필요 → 별도 처리하지 않지만 리스트에는 포함하지 않음
                if (maxWidth > 1 || maxHeight > 1) {
                    regions.add(MergeRegion(row, col, row + maxHeight - 1, col + maxWidth - 1, rgb))
                }
            }
        }

        return regions
    }
}
