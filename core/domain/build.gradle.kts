plugins {
    id("plansnap.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))

    // Shared reference rooms, generators and coroutine test utilities. Fixtures use only this module's main output.
    testImplementation(project(":testing:fixtures"))
}
