plugins {
    id("plansnap.android.library")
    id("plansnap.android.robolectric")
    id("com.google.devtools.ksp")
}

android {
    // Exported Room schemas are versioned in the repository and feed the migration tests (ARCHITECTURE §7).
    sourceSets {
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:document"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(project(":testing:fixtures"))
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
