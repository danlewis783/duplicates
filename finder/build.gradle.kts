plugins {
    java
    id("com.example.placeholder.jpackage")
}

jpackage {
    appName.set("DuplicateFinder")
    mainClass.set("acme.duplicates.DuplicateFinder")
    winConsole.set(true)
    docFiles.from(rootProject.file("LICENSE"), file("src/main/dist/README.md"))
    devInstallSubdirectory.set("duplicate-finder")
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