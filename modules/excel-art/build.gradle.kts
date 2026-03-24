plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.poi.ooxml)
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.23.1")
    implementation("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runExcelArt") {
    group = "application"
    description = "이미지를 Excel 셀 배경색으로 변환하는 샘플 실행"
    mainClass.set("io.github.jogakdal.excelart.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Fat JAR: 모든 의존성을 포함한 단독 실행 JAR 생성
tasks.register<Jar>("fatJar") {
    group = "build"
    description = "의존성을 모두 포함한 단독 실행 JAR 생성"
    archiveBaseName.set("excel-art")
    archiveClassifier.set("")

    manifest {
        attributes["Main-Class"] = "io.github.jogakdal.excelart.MainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// 단독 실행 파일: 런처 스크립트 + base64 인코딩된 JAR 결합
tasks.register("standalone") {
    group = "build"
    description = "셸 스크립트 + base64 JAR이 결합된 단독 실행 파일 생성"
    dependsOn("fatJar")

    val fatJar = layout.buildDirectory.file("libs/excel-art.jar")
    val output = layout.buildDirectory.file("excel-art")

    doLast {
        val jar = fatJar.get().asFile
        val out = output.get().asFile

        val launcher = """
            |#!/bin/bash
            |# excel-art — 이미지를 Excel 셀 배경색으로 변환
            |# 자기 완결형 실행 파일 (셸 스크립트 + base64 JAR 내장)
            |if ! command -v java &>/dev/null; then echo "오류: java가 필요합니다." >&2; exit 1; fi
            |I=""; S="A1"; O=""; C="256"; P="1"
            |while [ ${'$'}# -gt 0 ]; do case "${'$'}1" in
            |  -s|--start-cell) S="${'$'}2"; shift 2;; -o|--output) O="${'$'}2"; shift 2;;
            |  -c|--colors) C="${'$'}2"; shift 2;; -p|--pixel-size) P="${'$'}2"; shift 2;;
            |  -h|--help) echo "excel-art — 이미지를 Excel 셀 배경색으로 변환"; echo "";
            |    echo "사용법: excel-art <이미지 파일> [옵션]"; echo "";
            |    echo "  -s, --start-cell    시작 셀 (기본: A1)";
            |    echo "  -o, --output        출력 파일 (기본: <이미지명>.xlsx)";
            |    echo "  -c, --colors        최대 색상 수 (기본: 256, 최대: 64000)";
            |    echo "  -p, --pixel-size    셀 크기 pt (기본: 1)"; exit 0;;
            |  -*) echo "알 수 없는 옵션: ${'$'}1" >&2; exit 1;;
            |  *) if [ -z "${'$'}I" ]; then I="${'$'}1"; else echo "알 수 없는 인자: ${'$'}1" >&2; exit 1; fi; shift;;
            |esac; done
            |if [ -z "${'$'}I" ]; then echo "사용법: excel-art <이미지 파일> [옵션]"; exit 1; fi
            |O="${'$'}{O:-${'$'}{I%.*}.xlsx}"
            |M="${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd)/${'$'}(basename "${'$'}0")"
            |D="${'$'}{TMPDIR:-/tmp}/excel-art-cache"; K=${'$'}(cksum "${'$'}M" | cut -d' ' -f1); J="${'$'}D/ea-${'$'}{K}.jar"
            |if [ ! -f "${'$'}J" ]; then echo "JAR 추출 중... (${'$'}D)"; mkdir -p "${'$'}D"; awk '/^__BASE64_JAR__${'$'}/{f=1;next}/^__END__${'$'}/{f=0}f' "${'$'}M" | base64 -d > "${'$'}J"; echo "JAR 추출 완료"; fi
            |java -jar "${'$'}J" "${'$'}I" "${'$'}O" "${'$'}C" "${'$'}P" "${'$'}S"; exit ${'$'}?
            |__BASE64_JAR__
        """.trimMargin()

        out.writeText(launcher + "\n")

        // base64 인코딩하여 추가 (시스템 base64 명령 사용)
        val process = ProcessBuilder("base64", "-i", jar.absolutePath)
            .redirectErrorStream(true).start()
        out.appendBytes(process.inputStream.readBytes())
        process.waitFor()
        out.appendText("\n__END__\n")
        out.setExecutable(true)

        val sizeMB = out.length() / 1024 / 1024
        println("✓ 단독 실행 파일 (macOS/Linux): ${out.absolutePath} (${sizeMB}MB)")
    }
}

// 윈도우즈 배포: .bat + .jar
tasks.register("standaloneWin") {
    group = "build"
    description = "윈도우즈용 .bat + .jar 배포 패키지 생성"
    dependsOn("fatJar")

    val fatJar = layout.buildDirectory.file("libs/excel-art.jar")
    val outputDir = layout.buildDirectory.dir("win")

    doLast {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val jar = fatJar.get().asFile
        val bat = File(dir, "excel-art.bat")

        // JAR 복사
        jar.copyTo(File(dir, "excel-art.jar"), overwrite = true)

        // .bat 파일 생성
        bat.writeText("""
            |@echo off
            |REM excel-art — 이미지를 Excel 셀 배경색으로 변환
            |setlocal enabledelayedexpansion
            |
            |where java >nul 2>nul
            |if %errorlevel% neq 0 (
            |    echo 오류: java가 설치되어 있지 않습니다. 1>&2
            |    exit /b 1
            |)
            |
            |set "IMAGE="
            |set "START_CELL=A1"
            |set "OUTPUT="
            |set "COLORS=256"
            |set "PIXEL_SIZE=1"
            |
            |:parse
            |if "%~1"=="" goto :check
            |if /i "%~1"=="-s"          ( set "START_CELL=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="--start-cell" ( set "START_CELL=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="-o"          ( set "OUTPUT=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="--output"    ( set "OUTPUT=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="-c"          ( set "COLORS=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="--colors"    ( set "COLORS=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="-p"          ( set "PIXEL_SIZE=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="--pixel-size" ( set "PIXEL_SIZE=%~2" & shift & shift & goto :parse )
            |if /i "%~1"=="-h"          goto :help
            |if /i "%~1"=="--help"      goto :help
            |if "%~1:~0,1%"=="-" (
            |    echo 알 수 없는 옵션: %~1 1>&2
            |    exit /b 1
            |)
            |if "!IMAGE!"=="" (
            |    set "IMAGE=%~1"
            |    shift & goto :parse
            |)
            |echo 알 수 없는 인자: %~1 1>&2
            |exit /b 1
            |
            |:check
            |if "!IMAGE!"=="" (
            |    echo 사용법: excel-art ^<이미지 파일^> [옵션]
            |    echo 도움말: excel-art --help
            |    exit /b 1
            |)
            |if "!OUTPUT!"=="" set "OUTPUT=!IMAGE:~0,-4!.xlsx"
            |
            |set "JAR=%~dp0excel-art.jar"
            |java -jar "!JAR!" "!IMAGE!" "!OUTPUT!" "!COLORS!" "!PIXEL_SIZE!" "!START_CELL!"
            |exit /b %errorlevel%
            |
            |:help
            |echo excel-art — 이미지를 Excel 셀 배경색으로 변환
            |echo.
            |echo 사용법: excel-art ^<이미지 파일^> [옵션]
            |echo.
            |echo   -s, --start-cell    시작 셀 (기본: A1)
            |echo   -o, --output        출력 파일 (기본: ^<이미지명^>.xlsx)
            |echo   -c, --colors        최대 색상 수 (기본: 256, 최대: 64000)
            |echo   -p, --pixel-size    셀 크기 pt (기본: 1)
            |exit /b 0
        """.trimMargin())

        println("✓ 윈도우즈 배포 패키지: ${dir.absolutePath}/")
        println("  excel-art.bat (${bat.length() / 1024}KB)")
        println("  excel-art.jar (${jar.length() / 1024 / 1024}MB)")
    }
}
