import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.llamalad7.coverageagent"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-tree:9.6")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Premain-Class" to "com.llamalad7.coverageagent.CoverageAgent"
        )
    }
}

tasks.withType<ShadowJar> {
    tasks.getByName("build").dependsOn(this)
    archiveClassifier = null
}

tasks.getByName<Jar>("jar") {
    archiveClassifier = "slim"
}