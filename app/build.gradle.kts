plugins {
    id("plansnap.android.application")
    id("plansnap.android.compose")
    id("plansnap.android.hilt")
    id("plansnap.android.robolectric")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":feature:projects"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:export"))
    implementation(project(":feature:settings"))
    implementation(project(":core:data"))
    implementation(project(":core:export"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testing:fixtures"))
}
