plugins {
    id("plansnap.jvm.library")
}

// Shared test data, generators and fakes. Other modules may only use this module as a test dependency.
dependencies {
    api(project(":core:common"))
    api(project(":core:model"))
    api(project(":core:geometry"))
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.test)
}
