package io.github.jogakdal.excelart

import java.awt.image.BufferedImage

/**
 * Median Cut 알고리즘으로 이미지의 색상을 지정된 수 이하로 양자화한다.
 */
object ColorQuantizer {

    /**
     * 이미지를 양자화하여 2D RGB 배열을 반환한다.
     * @param image 원본 이미지
     * @param maxColors 최대 색상 수 (1~64000)
     * @return 양자화된 RGB 값의 2D 배열 [row][col]
     */
    fun quantize(image: BufferedImage, maxColors: Int): Array<IntArray> {
        require(maxColors in 1..64000) { "maxColors는 1~64000 사이여야 합니다: $maxColors" }

        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)

        // 고유 색상 수가 maxColors 이하면 양자화 불필요
        val uniqueColors = pixels.toSet()
        if (uniqueColors.size <= maxColors) {
            return Array(height) { row -> IntArray(width) { col -> pixels[row * width + col] and 0xFFFFFF } }
        }

        // Median Cut으로 팔레트 생성
        val palette = medianCut(pixels.map { it and 0xFFFFFF }.distinct(), maxColors)
        val colorMap = buildColorMap(palette)

        // 각 픽셀을 가장 가까운 팔레트 색상으로 매핑
        return Array(height) { row ->
            IntArray(width) { col ->
                val rgb = pixels[row * width + col] and 0xFFFFFF
                colorMap.getOrPut(rgb) { findClosestColor(rgb, palette) }
            }
        }
    }

    private fun medianCut(colors: List<Int>, maxColors: Int): List<Int> {
        if (colors.size <= maxColors) return colors

        val buckets = mutableListOf(colors.toMutableList())

        while (buckets.size < maxColors) {
            // 가장 큰 버킷을 찾아 분할
            val largest = buckets.maxByOrNull { it.size } ?: break
            if (largest.size <= 1) break

            buckets.remove(largest)

            val (bucket1, bucket2) = splitBucket(largest)
            buckets.add(bucket1)
            buckets.add(bucket2)
        }

        // 각 버킷의 평균 색상을 대표색으로
        return buckets.map { bucket -> averageColor(bucket) }
    }

    private fun splitBucket(colors: MutableList<Int>): Pair<MutableList<Int>, MutableList<Int>> {
        // RGB 각 채널의 범위 계산
        val reds = colors.map { (it shr 16) and 0xFF }
        val greens = colors.map { (it shr 8) and 0xFF }
        val blues = colors.map { it and 0xFF }

        val redRange = (reds.max() - reds.min())
        val greenRange = (greens.max() - greens.min())
        val blueRange = (blues.max() - blues.min())

        // 가장 넓은 범위의 채널로 정렬 후 중앙에서 분할
        val comparator = when (maxOf(redRange, greenRange, blueRange)) {
            redRange -> Comparator.comparingInt<Int> { (it shr 16) and 0xFF }
            greenRange -> Comparator.comparingInt<Int> { (it shr 8) and 0xFF }
            else -> Comparator.comparingInt<Int> { it and 0xFF }
        }
        colors.sortWith(comparator)

        val mid = colors.size / 2
        return colors.subList(0, mid).toMutableList() to colors.subList(mid, colors.size).toMutableList()
    }

    private fun averageColor(colors: List<Int>): Int {
        val r = colors.sumOf { (it shr 16) and 0xFF } / colors.size
        val g = colors.sumOf { (it shr 8) and 0xFF } / colors.size
        val b = colors.sumOf { it and 0xFF } / colors.size
        return (r shl 16) or (g shl 8) or b
    }

    private fun findClosestColor(rgb: Int, palette: List<Int>): Int {
        val r1 = (rgb shr 16) and 0xFF
        val g1 = (rgb shr 8) and 0xFF
        val b1 = rgb and 0xFF

        return palette.minBy { color ->
            val r2 = (color shr 16) and 0xFF
            val g2 = (color shr 8) and 0xFF
            val b2 = color and 0xFF
            (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)
        }
    }

    private fun buildColorMap(palette: List<Int>): MutableMap<Int, Int> {
        // 팔레트 색상은 자기 자신으로 매핑
        return palette.associateWith { it }.toMutableMap()
    }
}
