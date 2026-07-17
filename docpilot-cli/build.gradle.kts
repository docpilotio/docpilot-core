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
    implementation(project(":"))
    implementation(project(":docpilot-provider-ollama"))
    implementation(project(":docpilot-provider-openai"))

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.docpilot.cli.MainKt")
    applicationName = "docpilot"
}

tasks.test {
    useJUnitPlatform()
}
