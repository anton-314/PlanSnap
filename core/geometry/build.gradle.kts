plugins {
    id("plansnap.jvm.library")
}

dependencies {
    api(project(":core:common"))

    // Shared reference rooms for analytic tests. Fixtures depend on this module's main output only, so there is no cycle.
    testImplementation(project(":testing:fixtures"))
}
