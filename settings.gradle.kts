pluginManagement {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "deckbuild"

include(":core")

// Das :app-Modul braucht das Android SDK. Auf Maschinen ohne SDK (CI-Container,
// Server ohne Android-Tooling) wird es ausgelassen, damit ":core" trotzdem
// gebaut und getestet werden kann. Android Studio bringt das SDK immer mit,
// dort taucht das Modul also normal auf.
val androidSdkAvailable: Boolean =
    System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidSdkAvailable) {
    include(":app")
} else {
    println(
        "[deckbuild] Kein Android SDK gefunden (ANDROID_HOME / local.properties). " +
            "Modul :app wird uebersprungen, :core bleibt baubar.",
    )
}
