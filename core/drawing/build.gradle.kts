plugins {
    id("plansnap.jvm.library")
}

dependencies {
    api(project(":core:domain"))

    // Shared reference rooms and generators for viewport and hit-test properties.
    testImplementation(project(":testing:fixtures"))
}
