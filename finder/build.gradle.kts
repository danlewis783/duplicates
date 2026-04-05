plugins {
    java
    application
}

application {
    mainClass.set("acme.duplicates.DuplicateFinder")
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