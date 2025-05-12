import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.ksp)
    `maven-publish`
}

group = "com.github.xyzboom"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/")
}

publishing {
    repositories {
        maven {
            url = uri("https://jitpack.io")
        }
    }
    publications {
        create<MavenPublication>("source") {
            groupId = "com.github.XYZboom"
            artifactId = "CodeSmith"
            version = "1.0-SNAPSHOT"

            // 配置要上传的源码
            sourceSets.filter {
                "test" !in it.name
            }.forEach { sourceSet ->
                artifact(tasks.register<Jar>("${sourceSet.name}SourcesJar") {
                    from(sourceSet.allSource)
                    archiveClassifier.set("sources")
                }) {
                    classifier = "sources"
                }
            }
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
    jvmToolchain(11)
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    js {
        browser { }
        nodejs { }
    }
    linuxX64 {
        configureNativeTarget()
    }
    mingwX64 {
        configureNativeTarget()
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlin.logging)
                implementation(libs.kaml)
                implementation(libs.okio)
                implementation(libs.antlr.kotlin)
//                implementation(libs.konst)
                implementation(libs.jetbrains.anno)
            }
            kotlin {
                srcDirs(
                    layout.buildDirectory.dir("generatedAntlr"),
                )
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotest.assertions.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.ksp)
                implementation(libs.kotlin.poet)
                implementation(libs.kotlin.poet.ksp)
            }
        }
        jvmTest {
            dependencies {
                runtimeOnly("org.apache.logging.log4j:log4j-api:2.20.0")
                runtimeOnly("org.slf4j:slf4j-log4j12:2.0.16")
                runtimeOnly("org.slf4j:slf4j-api:2.0.16")
            }
        }
        all {
            languageSettings.enableLanguageFeature("WhenGuards")
            languageSettings.enableLanguageFeature("ContextParameters")
        }
    }
}

dependencies {
    add("kspJvmTest", rootProject)
    add("kspJsTest", rootProject)
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