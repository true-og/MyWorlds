rootProject.name = "MyWorlds"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://ci.mg-dev.eu/plugin/repository/everything/")
    }
}

// Fetch submodule content before the libraries below are wired in as subprojects.
ProcessBuilder("sh", "bootstrap.sh").directory(rootDir).inheritIO().start().let {
    if (it.waitFor() != 0) throw GradleException("bootstrap.sh failed")
}

// Every git submodule under libs/ with a Gradle build becomes :libs:<name>, like Template-OG.
file("libs")
    .listFiles()
    ?.filter { it.isDirectory && !it.name.startsWith(".") && file("${it.path}/build.gradle.kts").exists() }
    ?.forEach { dir ->
        include(":libs:${dir.name}")
        project(":libs:${dir.name}").projectDir = dir
    }
