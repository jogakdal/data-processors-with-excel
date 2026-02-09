plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.mavenPublish)
}

group = property("GROUP") as String
version = property("VERSION_NAME") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)

    // Spring Boot BOM (버전 관리용)
    implementation(platform(libs.spring.boot.dependencies))
    kapt(platform(libs.spring.boot.dependencies))

    // Spring Boot Auto-configuration (compileOnly - 선택적 의존성)
    compileOnly(libs.spring.boot.autoconfigure)
    kapt(libs.spring.boot.config.processor)

    // Apache POI (Excel 처리)
    implementation(libs.poi.ooxml)
    implementation(libs.poi.ooxml.full)

    // Kotlin Coroutines (비동기 지원)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.jdk8)

    // Kryo (객체 직렬화)
    implementation(libs.kryo)

    // Logback
    implementation(libs.logback.classic)

    // Test
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }

mavenPublishing {
    coordinates(
        groupId = property("GROUP") as String,
        artifactId = "tbeg",
        version = property("VERSION_NAME") as String
    )
}

// 샘플 실행 태스크 (Kotlin)
tasks.register<JavaExec>("runSample") {
    group = "application"
    description = "TBEG Kotlin 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.TbegSample")
}

// 샘플 실행 태스크 (Java)
tasks.register<JavaExec>("runJavaSample") {
    group = "application"
    description = "TBEG Java 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.TbegJavaSample")
}

// Spring Boot 샘플 실행 태스크 (Kotlin)
tasks.register<JavaExec>("runSpringBootSample") {
    group = "application"
    description = "TBEG Spring Boot Kotlin 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.TbegSpringBootSample")
}

// Spring Boot 샘플 실행 태스크 (Java)
tasks.register<JavaExec>("runSpringBootJavaSample") {
    group = "application"
    description = "TBEG Spring Boot Java 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.TbegSpringBootJavaSample")
}

// TemplateRenderingEngine 샘플 실행 태스크
tasks.register<JavaExec>("runRenderingEngineSample") {
    group = "application"
    description = "TemplateRenderingEngine 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.TemplateRenderingEngineSample")
}

// 빈 컬렉션 + emptyRange 샘플 실행 태스크
tasks.register<JavaExec>("runEmptyCollectionSample") {
    group = "application"
    description = "빈 컬렉션 + emptyRange 기능 샘플 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.samples.EmptyCollectionSample")
}

// 성능 벤치마크 실행 태스크
tasks.register<JavaExec>("runBenchmark") {
    group = "application"
    description = "TBEG 성능 벤치마크 실행"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.jogakdal.tbeg.benchmark.PerformanceBenchmark")
    maxHeapSize = "2g"
}
