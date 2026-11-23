pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "PlanSnap"

include(
    ":app",
    ":core:common",
    ":core:geometry",
    ":core:model",
    ":core:domain",
    ":core:document",
    ":core:drawing",
    ":core:render",
    ":core:data",
    ":core:export",
    ":core:designsystem",
    ":feature:projects",
    ":feature:editor",
    ":feature:export",
    ":feature:settings",
    ":testing:fixtures",
    ":benchmark",
)
