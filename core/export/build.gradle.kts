plugins {
    id("plansnap.android.library")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:drawing"))
    api(project(":core:render"))
    api(project(":core:document"))
}
