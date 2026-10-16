pluginManagement {
    repositories {
        mavenLocal() // published from C:/dev/jpackage-gradle-plugin
        gradlePluginPortal()
    }
    plugins {
        // Version pinned here once for all modules.
        id("com.example.placeholder.jpackage") version "0.1.2"
    }
}

rootProject.name = "duplicates"

include("common")
include("finder")
include("deleter")
