plugins {
    id("plansnap.jvm.library")
}

dependencies {
    api(project(":core:common"))
    api(libs.kotlinx.collections.immutable)
}
