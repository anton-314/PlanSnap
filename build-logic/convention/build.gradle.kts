plugins {
    `kotlin-dsl`
}

group = "app.plansnap.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.agp.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)
    implementation(libs.roborazzi.gradle.plugin)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    maxParallelForks = 2
    systemProperty("plansnap.catalog", rootProject.file("../gradle/libs.versions.toml").absolutePath)
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "plansnap.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "plansnap.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "plansnap.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidRobolectric") {
            id = "plansnap.android.robolectric"
            implementationClass = "AndroidRobolectricConventionPlugin"
        }
        register("androidRoborazzi") {
            id = "plansnap.android.roborazzi"
            implementationClass = "AndroidRoborazziConventionPlugin"
        }
        register("androidApplication") {
            id = "plansnap.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("quality") {
            id = "plansnap.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("architecture") {
            id = "plansnap.architecture"
            implementationClass = "ArchitectureConventionPlugin"
        }
        register("root") {
            id = "plansnap.root"
            implementationClass = "RootConventionPlugin"
        }
        register("androidTest") {
            id = "plansnap.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
    }
}
