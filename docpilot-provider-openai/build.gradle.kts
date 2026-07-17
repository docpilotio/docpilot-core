plugins {
    kotlin("jvm")
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
    implementation(project(":"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
