plugins {
    id("plansnap.android.application")
}

dependencies {
    implementation(project(":feature:projects"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:export"))
    implementation(project(":feature:settings"))
    implementation(project(":core:data"))
    implementation(project(":core:export"))
}
