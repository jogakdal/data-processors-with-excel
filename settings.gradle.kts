pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "data-processors-with-excel"
include(":tbeg")
project(":tbeg").projectDir = file("modules/tbeg")
