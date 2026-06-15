// First-pass build for the cashly locations module. Versions are illustrative;
// align them with cashly's version catalog when dropping this into the monorepo.

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val coroutinesVersion = "1.8.1"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // vdbs backend: Ktor HTTP client + JSON content negotiation
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // API layer: Ktor server routing (engine/app wiring lives in the host service)
    implementation("io.ktor:ktor-server-core:$ktorVersion")

    // Tests: drive the vdbs stack through a mock engine
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
