plugins {
    id("plansnap.android.library")
    id("plansnap.android.compose")
    id("plansnap.android.hilt")
}

dependencies {
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(project(":core:domain"))
    implementation(project(":core:export"))
    implementation(project(":core:designsystem"))
}
