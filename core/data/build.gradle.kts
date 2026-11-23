plugins {
    id("plansnap.android.library")
    id("plansnap.android.robolectric")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:document"))
}
