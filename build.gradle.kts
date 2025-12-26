/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.hangarpublishplugin.model.Platforms
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.runpaper.task.RunServer
import java.io.ByteArrayOutputStream

val paperVersion: List<String> = (property("gameVersions") as String)
        .split(",")
        .map { it.trim() }


plugins {
    `java-library`
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    // For ingametesting
    //id("io.papermc.paperweight.userdev") version "1.5.10"
    idea
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
}

// Make sure javadocs are available to IDE
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

repositories {
    mavenCentral()
    // Spigot API
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    // Placeholder API
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // CodeMC Repo for bStats
    maven("https://repo.codemc.org/repository/maven-public/")
    // Auth library from Minecraft
    maven("https://libraries.minecraft.net/")
}

group = "kernitus.plugin.OldCombatMechanics"
version = "2.2.0" // x-release-please-version
description = "OldCombatMechanics"

java {
    toolchain {
        // We can build with Java 17 but still support MC >=1.9
        // This is because MC >=1.9 server can be run with higher Java versions
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    main {
        java {
            exclude("kernitus/plugin/OldCombatMechanics/tester/**")
        }
    }

    val integrationTest by creating {
        kotlin.setSrcDirs(listOf("src/integrationTest/kotlin"))
        resources.setSrcDirs(listOf("src/integrationTest/resources"))
        compileClasspath += main.get().output
        runtimeClasspath += output + main.get().output
    }
}

configurations {
    val integrationTestImplementation by getting {
        extendsFrom(configurations.implementation.get())
    }
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.0.2")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.106.Final")
    // Placeholder API
    compileOnly("me.clip:placeholderapi:2.11.5")
    // For BSON file serialisation
    implementation("org.mongodb:bson:5.0.1")
    // Spigot
    compileOnly("org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    // ProtocolLib
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    // XSeries
    implementation("com.github.cryptomorin:XSeries:13.3.3")

     //For ingametesting
    // Mojang mappings for NMS
    /*
    compileOnly("com.mojang:authlib:4.0.43")
    paperweight.paperDevBundle("1.19.2-R0.1-SNAPSHOT")
    // For reflection remapping
    implementation("xyz.jpenilla:reflection-remapper:0.1.1")
     */

    // Integration test dependencies
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-test:2.1.0")
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    add("integrationTestImplementation", "io.kotest:kotest-runner-junit5:6.0.0.M1")
    add("integrationTestImplementation", "io.kotest:kotest-assertions-core:6.0.0.M1")
    add("integrationTestImplementation", "net.kyori:adventure-api:4.18.0")
    add("integrationTestImplementation", "xyz.jpenilla:reflection-remapper:0.1.1")

    add("integrationTestCompileOnly", "org.spigotmc:spigot-api:1.21.8-R0.1-SNAPSHOT")
    add("integrationTestCompileOnly", "com.mojang:authlib:4.0.43")
    add("integrationTestCompileOnly", "io.netty:netty-all:4.1.106.Final")
}

// Substitute ${pluginVersion} in plugin.yml with version defined above
tasks.named<Copy>("processResources") {
    inputs.property("pluginVersion", version)
    filesMatching("plugin.yml") {
        expand("pluginVersion" to version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val shadowJarTask = tasks.named<ShadowJar>("shadowJar") {
    dependsOn("jar")
    archiveFileName.set("${project.name}.jar")
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats")
        relocate("com.cryptomorin.xseries", "kernitus.plugin.OldCombatMechanics.lib.xseries")
    }
}

// For ingametesting
/*
tasks.reobfJar {
    outputJar.set(File(buildDir, "libs/${project.name}.jar"))
}
 */

tasks.assemble {
    // For ingametesting
    //dependsOn("reobfJar")
    dependsOn("shadowJar")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

val integrationTestJarTask = tasks.register<ShadowJar>("integrationTestJar") {
    archiveClassifier.set("tests")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn("compileIntegrationTestKotlin")

    from(sourceSets["integrationTest"].output)

    project.configurations["integrationTestRuntimeClasspath"].forEach { file: File ->
        from(if (file.isDirectory) file else zipTree(file))
    }

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.EC")
    exclude("META-INF/*.MF")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
}

val integrationTestMinecraftVersion =
    (findProperty("integrationTestMinecraftVersion") as String?) ?: "1.19.2"

val defaultIntegrationTestVersions = listOf(integrationTestMinecraftVersion, "1.21.11")
    .distinct()

val integrationTestVersions: List<String> = (findProperty("integrationTestVersions") as String?)
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.ifEmpty { defaultIntegrationTestVersions }
    ?: defaultIntegrationTestVersions

val integrationTestJavaVersionLegacy =
    (findProperty("integrationTestJavaVersionLegacy") as String?)?.toInt() ?: 17
val integrationTestJavaVersionModern =
    (findProperty("integrationTestJavaVersionModern") as String?)?.toInt() ?: 25

fun parseMinecraftVersion(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return Triple(major, minor, patch)
}

fun requiresModernJava(version: String): Boolean {
    val (major, minor, patch) = parseMinecraftVersion(version)
    if (major > 1) return true
    if (minor > 20) return true
    return minor == 20 && patch >= 5
}

fun requiredJavaVersion(version: String): Int {
    return if (requiresModernJava(version)) integrationTestJavaVersionModern else integrationTestJavaVersionLegacy
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests against all configured Paper versions."
    dependsOn("integrationTestMatrix")
}

tasks.named<RunServer>("runServer") {
    enabled = false
    description = "Disabled. Use integrationTest/integrationTestMatrix instead."
}

val integrationTestMatrixTasks = mutableListOf<TaskProvider<Task>>()
var previousMatrixTask: TaskProvider<Task>? = null

fun versionTaskSuffix(version: String): String {
    return version.replace(Regex("[^A-Za-z0-9]"), "_")
}

for (version in integrationTestVersions) {
    val suffix = versionTaskSuffix(version)
    val runDir = file("run/$version")
    val resultFile = runDir.resolve("plugins/OldCombatMechanicsTest/test-results.txt")

    val writePropsTask = tasks.register<WriteProperties>("writeProperties${suffix}") {
        encoding = "UTF-8"
        property("online-mode", false)
        destinationFile.set(runDir.resolve("server.properties"))
    }

    val runServerTask = tasks.register<RunServer>("runServer${suffix}") {
        dependsOn(writePropsTask)
        runDirectory.set(runDir)
        minecraftVersion(version)
        jvmArgs("-Dcom.mojang.eula.agree=true")
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(requiredJavaVersion(version)))
        })

        pluginJars.from(shadowJarTask.flatMap { it.archiveFile })
        pluginJars.from(integrationTestJarTask.flatMap { it.archiveFile })

        doFirst {
            if (resultFile.exists()) {
                resultFile.delete()
            }
        }
    }

    val checkTask = tasks.register("checkTestResults${suffix}") {
        doLast {
            if (!resultFile.exists()) {
                throw GradleException("Test results file not found for $version. Tests may not have run correctly.")
            }
            val result = resultFile.readText().trim()
            if (result == "FAIL") {
                throw GradleException("Integration tests failed for $version.")
            } else if (result != "PASS") {
                throw GradleException("Unknown test result for $version: $result")
            }
            println("Integration tests passed for $version.")
        }
    }

    val testTask = tasks.register("integrationTest${suffix}") {
        group = "verification"
        description = "Runs integration tests with a live Paper server ($version)."
        dependsOn(shadowJarTask, integrationTestJarTask, runServerTask)
        finalizedBy(checkTask)
    }

    val priorTask = previousMatrixTask
    if (priorTask != null) {
        // Chain tasks to enforce order and fail fast.
        testTask.configure { dependsOn(priorTask) }
    }
    previousMatrixTask = testTask
    integrationTestMatrixTasks.add(testTask)
}

tasks.register("integrationTestMatrix") {
    group = "verification"
    description = "Runs integration tests against multiple Paper versions."
    dependsOn(integrationTestMatrixTasks)
}

val versionStringProvider = providers.provider { project.version.toString() }
val isReleaseProvider = versionStringProvider.map { !it.contains('-') }

val gitShortHashProvider = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

val gitChangelogProvider = providers.exec {
    commandLine("git", "log", "-1", "--pretty=%B")
}.standardOutput.asText.map { it.trim() }

val suffixedVersionProvider = providers.provider {
    val version = project.version.toString()
    if (!version.contains('-')) {
        version
    } else {
        "$version+${gitShortHashProvider.get()}"
    }
}

val changelogProvider = providers.environmentVariable("HANGAR_CHANGELOG").orElse(gitChangelogProvider)

tasks.register("printIsRelease") {
    doLast {
        println(if (!project.version.toString().contains('-')) "true" else "false")
    }
}

hangarPublish {
    publications.register("plugin") {
        version.set(suffixedVersionProvider)
        channel.set(isReleaseProvider.map { if (it) "Release" else "Snapshot" })
        id.set("OldCombatMechanics")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))
        changelog.set(changelogProvider)
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(paperVersion)
            }
        }
    }
}
