plugins {
    id("plansnap.android.library")
    id("plansnap.android.compose")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:export"))
    implementation(project(":core:designsystem"))
}
