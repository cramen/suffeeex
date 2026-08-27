import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("kapt") version "1.9.10"
    id("me.champeau.jmh") version "0.7.2"
    id("maven-publish")
    `java-library`
}

group = "ru.cramen"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.ow2.asm:asm:9.7")

    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.10")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.7.2")

    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

jmh {
    jmhVersion.set("1.37")
    fork.set(1)
    warmupIterations.set(3)
    iterations.set(5)
}
