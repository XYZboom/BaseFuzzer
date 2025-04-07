import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "2.1.20"
}

group = "com.github.xyzboom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    commonTestImplementation(kotlin("test"))
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
            jvmTarget = JvmTarget.JVM_1_8
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
}