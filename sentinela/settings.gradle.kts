pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // O endpoint repo.maven.apache.org vem respondendo 429 neste ambiente;
        // repo1.maven.org é o mesmo Maven Central e responde normalmente.
        maven { url = uri("https://repo1.maven.org/maven2/") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://repo1.maven.org/maven2/") }
    }
}

rootProject.name = "Sentinela"
include(":app")
