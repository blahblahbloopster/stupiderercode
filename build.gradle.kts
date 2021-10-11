import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
}

group = "com.github.blahblahbloopster"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val fatJar = task("fatJar", type = Jar::class) {
    archiveBaseName.set(project.name)
    manifest {
        attributes["Main-Class"] = "com.github.blahblahbloopster.StupidererCodeParserKt"
    }
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.INCLUDE
    from(configurations.runtimeClasspath.get().files.map { if (it.isDirectory) it else zipTree(it) })
    with(tasks["jar"] as CopySpec)
}
