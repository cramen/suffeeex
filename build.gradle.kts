import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    id("maven-publish")
    `java-library`
}

group = "ru.cramen"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.10")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.7.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
