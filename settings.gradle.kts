plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "base-fuzzer"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
        }
    }
}

