import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("kapt") version "1.9.24"
    id("me.champeau.jmh") version "0.7.2"
    // 0.29.0: first line with native Central Portal support (0.28+); runs on
    // Gradle 8.1+ (0.30.x needs a newer Gradle); publishing targets the
    // Central Portal API directly (see publishToMavenCentral below)
    id("com.vanniktech.maven.publish") version "0.29.0"
    id("maven-publish")
    `java-library`
}

group = "io.github.cramen"
version = "0.2.0"

repositories {
    mavenCentral()
}

dependencies {

    implementation("org.ow2.asm:asm:9.7")

    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.24")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.24")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.7.2")

    kaptJmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
}

// Signing is mandatory on Maven Central but must not break keyless local
// builds: it activates only when GPG credentials are present (in
// ~/.gradle/gradle.properties): either signing.keyId + signing.password +
// signing.secretKeyRingFile, or signing.keyId + signing.password +
// signing.secretKey (in-memory armored key).
val signingKeyConfigured = providers.gradleProperty("signing.keyId").isPresent

mavenPublishing {
    // native Central Portal Publisher API; the Portal UI still requires
    // finishing the deployment manually (no automaticRelease)
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    pom {
        name.set("suffeeex")
        description.set(
            "Super fast extensible expression executor for the JVM — statically typed parsing," +
                " ASM bytecode generation, near-native speed, symbolic differentiation"
        )
        url.set("https://github.com/cramen/suffeeex")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("cramen")
                name.set("cramen")
                url.set("https://github.com/cramen")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/cramen/suffeeex.git")
            developerConnection.set("scm:git:https://github.com/cramen/suffeeex.git")
            url.set("https://github.com/cramen/suffeeex")
        }
    }
    if (signingKeyConfigured) {
        signAllPublications()
    }
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
