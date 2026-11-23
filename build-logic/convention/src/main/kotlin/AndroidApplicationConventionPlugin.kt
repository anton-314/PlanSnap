import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register

/**
 * Application module. Also registers a manifest permission check for every variant that fails the
 * build when the merged manifest declares a permission (REQ-NFR-004).
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig {
                    applicationId = NAMESPACE_BASE
                    targetSdk = TARGET_SDK
                    versionCode = 1
                    versionName = "0.1.0"
                }
                buildTypes {
                    release {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            }
            configureTestLimits()
            pluginManager.apply(ArchitectureConventionPlugin::class.java)
            pluginManager.apply(QualityConventionPlugin::class.java)

            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    val variantName = variant.name.replaceFirstChar { it.uppercase() }
                    val check =
                        tasks.register<CheckManifestPermissionsTask>("check${variantName}ManifestPermissions") {
                            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                            // AndroidX Core adds this app-internal signature permission; it grants no system access.
                            allowedPermissions.add("$NAMESPACE_BASE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                        }
                    tasks.matching { it.name == "check" }.configureEach { dependsOn(check) }
                    if (variant.buildType == "debug") {
                        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(check) }
                    }
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit4").get())
            }
        }
}
