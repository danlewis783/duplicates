pluginManagement {
    repositories {
        mavenLocal() // for the jpackage plugin until it is live on the Plugin Portal
        gradlePluginPortal()
    }
    plugins {
        // Version pinned here once for all modules.
        id("io.github.danlewis783.jpackage") version "0.1.0"
    }
}

rootProject.name = "duplicates"

include("common")
include("finder")
include("deleter")
