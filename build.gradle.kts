import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.kotest.multiplatform)
    `maven-publish`
}

group = "com.github.xyzboom"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

publishing {
    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
    }
}

fun KotlinNativeTarget.configureNativeTarget() {
    binaries {
        sharedLib {

        }
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    js {
        browser { }
        nodejs { }
    }
    if (System.getenv("JITPACK") == null) {
        linuxX64 {
            configureNativeTarget()
        }
        mingwX64 {
            configureNativeTarget()
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kaml)
                implementation(libs.okio)
                implementation(libs.antlr.kotlin)
            }
            kotlin {
                srcDir(layout.buildDirectory.dir("generatedAntlr"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.framework.engine)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotest.runner.junit5)
            }
        }
    }
}

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    // ANTLR .g4 files are under {example-project}/antlr
    // Only include *.g4 files. This allows tools (e.g., IDE plugins)
    // to generate temporary files inside the base path
    source = fileTree(layout.projectDirectory.dir("src/antlr")) {
        include("**/*.g4")
    }

    // We want the generated source files to have this package name
    val pkgName = "com.github.xyzboom.bf.generated"
    packageName = pkgName

    // We want visitors alongside listeners.
    // The Kotlin target language is implicit, as is the file encoding (UTF-8)
    arguments = listOf("-visitor")

    // Generated files are outputted inside build/generatedAntlr/{package-name}
    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

tasks.withType<KotlinCompilationTask<*>> {
    dependsOn(generateKotlinGrammarSource)
}

tasks.withType<Jar> {
    dependsOn(generateKotlinGrammarSource)
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}