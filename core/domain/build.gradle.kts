plugins {
    id("plansnap.jvm.library")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:geometry"))
}
