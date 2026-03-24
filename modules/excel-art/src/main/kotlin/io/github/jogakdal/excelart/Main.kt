package io.github.jogakdal.excelart

import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("사용법: runExcelArt <이미지 파일> [출력 파일] [최대 색상 수] [셀 크기(pt)] [시작 셀]")
        println("예: runExcelArt logo.png logo.xlsx 256 0.75 B2")
        return
    }

    val imageFile = File(args[0])
    val outputFile = File(args.getOrElse(1) { "${imageFile.nameWithoutExtension}.xlsx" })
    val maxColors = args.getOrElse(2) { "256" }.toInt()
    val pixelSize = args.getOrElse(3) { "0.75" }.toFloat()
    val startCell = args.getOrElse(4) { "A1" }

    ImageToExcel.convert(imageFile, outputFile, maxColors, pixelSize, startCell)
}
