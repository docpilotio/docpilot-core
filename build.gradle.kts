plugins {
    kotlin("jvm") version "2.4.0"
    id("application")
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
    mainClass.set("io.docpilot.core.cli.DocPilotCliKt")
}

tasks.test {
    useJUnitPlatform()
}
