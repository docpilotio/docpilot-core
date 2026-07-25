plugins {
    kotlin("jvm")
    application
}

group = "io.docpilot"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.docpilot.release.MainKt")
    applicationName = "docpilot-release"
}

tasks.test {
    useJUnitPlatform()
}
