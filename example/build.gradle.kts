plugins {
    application
}

application {
    applicationDefaultJvmArgs = listOf(
        "-javaagent:${rootProject.tasks.jar.get().archiveFile.get().asFile.absolutePath}=com/google/"
    )
    mainClass = "org.example.Main"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.run.configure {
    dependsOn(rootProject.tasks.assemble)
}