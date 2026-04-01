import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.9"
    eclipse
}

group = "com.bergerkiller.bukkit"
version = "1.19.4-v1"

val buildNumber =
    providers.environmentVariable("BUILD_NUMBER")
        .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
        .orElse("NO-CI")
val pluginName = "MyWorlds"
val mcApiVersion = "1.19.4-R0.1-SNAPSHOT"
val bkCommonLibVersion = "1.19.4-v2"
val pluginPreloaderVersion = "1.8"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    main {
        resources {
            srcDir("src/main/java")
            include("plugin.yml")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://ci.mg-dev.eu/plugin/repository/everything") {
        name = "mg-dev"
    }
    maven("https://hub.spigotmc.org/nexus/content/groups/public/") {
        name = "spigot"
    }
    maven("file://${System.getProperty("user.home")}/.m2/repository") {
        name = "mavenLocalFallback"
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:$mcApiVersion")
    compileOnly("com.bergerkiller.bukkit:BKCommonLib:$bkCommonLibVersion")

    compileOnly("com.onarandombox.multiversecore:Multiverse-Core:4.2.0") {
        exclude(group = "org.bukkit", module = "craftbukkit")
    }
    compileOnly("me.clip:placeholderapi:2.10.9")

    implementation("com.bergerkiller.bukkit.preloader:PluginPreloader:$pluginPreloaderVersion")

    testImplementation("junit:junit:3.8.1")
}

tasks.processResources {
    val projectProps =
        mapOf(
            "version" to project.version,
            "build" to mapOf("number" to buildNumber.get()),
        )
    val props =
        mapOf(
            "version" to project.version,
            "build" to buildNumber.get(),
            "project" to projectProps,
        )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.jar {
    enabled = false
}

tasks.register<Jar>("sourcesJar") {
    enabled = false
}

tasks.register<Jar>("javadocJar") {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set(pluginName)
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    relocate("com.bergerkiller.bukkit.preloader", "com.bergerkiller.bukkit.mw")
    relocate("org.objectweb.asm", "com.bergerkiller.mountiplex.dep.org.objectweb.asm")

    dependencies {
        include(dependency("com.bergerkiller.bukkit.preloader:PluginPreloader"))
    }

    exclude("META-INF/*.MF")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
    options.compilerArgs.add("-Xlint:deprecation")
    options.isFork = true
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    isFailOnError = false
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addStringOption("Xdoclint:none", "-quiet")
        source("8")
    }
}

tasks.test {
    useJUnit()
}
