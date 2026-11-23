plugins {
    id("plansnap.android.library")
    id("plansnap.android.compose")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:render"))
    implementation(project(":core:drawing"))
    implementation(project(":core:designsystem"))
}
