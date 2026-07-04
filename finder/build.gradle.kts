plugins {
    java
    application
    id("io.github.danlewis783.jpackage")
}

application {
    mainClass.set("acme.duplicates.DuplicateFinder")
}

jpackage {
    appName.set("DuplicateFinder")
    mainClass.set(application.mainClass)
    winConsole.set(true)
    docFiles.from(rootProject.file("LICENSE"), rootProject.file("README.md"))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    testImplementation(libs.assertj)
}

testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter(libs.versions.junit.jupiter.get())
        }
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}