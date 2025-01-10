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

val paperVersion: List<String> = (property("gameVersions") as String).split(",").map { it.trim() }

val nmsVersion: String = project.findProperty("nmsVersion") as? String ?: "1.19.1-R0.1-SNAPSHOT"

// Extract the short version number, e.g., "1_19_1" from "1.19.1-R0.1-SNAPSHOT"
val nmsVersionShort = nmsVersion.substringBefore("-R").replace('.', '_')

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    idea
    id("io.papermc.hangar-publish-plugin") version "0.1.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
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
    // Protocollib
    maven("https://repo.dmulloy2.net/repository/public/")
}

sourceSets {
    val main by getting

    // val testNms by creating {
    //    // Include the version-specific test code
    //    kotlin.srcDir("src/testNms/v$nmsVersionShort/kotlin")

    //    // Include main output
    //    compileClasspath += main.output
    //    runtimeClasspath += main.output
    // }

    // val test by getting {
    //    // Include only the classes from testNms, not its dependencies
    //    compileClasspath += sourceSets["testNms"].output
    //    runtimeClasspath += sourceSets["testNms"].output
    // }
    val integrationTest by creating {
        kotlin.setSrcDirs(listOf("src/integrationTest/kotlin"))
        resources.setSrcDirs(listOf("src/integrationTest/resources"))
        // Include only the compiled classes from 'main', excluding resources
        compileClasspath += sourceSets["main"].output.classesDirs + configurations["compileClasspath"]
        runtimeClasspath += output + compileClasspath + configurations["runtimeClasspath"]
    }
}

configurations {
    compileOnly
    implementation

    val integrationTestImplementation by getting {
        extendsFrom(configurations.implementation.get())
    }
}

dependencies {
    // Main dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    // For BSON file serialisation
    implementation("org.mongodb:bson:5.0.1")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.116.Final")
    // Placeholder API
    compileOnly("me.clip:placeholderapi:2.11.5")
    // ProtocolLib
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    // Spigot
    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")

    // Depend on the shared code in the nms parent project
    implementation(project(":nms"))

    // Integration test dependencies
    val integrationTestImplementation: Configuration by configurations.getting
    val integrationTestCompileOnly: Configuration by configurations.getting
    // Kotlin dependencies
    integrationTestImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    integrationTestImplementation("org.jetbrains.kotlin:kotlin-test:2.1.0")
    integrationTestImplementation("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
    integrationTestImplementation("io.kotest:kotest-runner-junit5:6.0.0.M1")
    integrationTestCompileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    // NMS dependencies
    integrationTestImplementation(project(":nms:nms_v1_19_2"))

    /*
    // Mojang mappings for NMS
    testNmsCompileOnly("com.mojang:authlib:4.0.43")
     */
}

group = "kernitus.plugin.OldCombatMechanics"

version = "3.0.0-beta" // x-release-please-version

description = "OldCombatMechanics"

kotlin {
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Substitute ${pluginVersion} in plugin.yml with version defined above
tasks.named<Copy>("processResources") {
    inputs.property("pluginVersion", version)
    filesMatching("plugin.yml") { expand("pluginVersion" to version) }
}

val shadowJarTask =
    tasks.named<ShadowJar>("shadowJar") {
        dependsOn("jar")
        archiveFileName.set("${project.name}.jar")
        dependencies { relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats") }
    }

// Function to execute Git commands
fun executeGitCommand(vararg command: String): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        commandLine = listOf("git", *command)
        standardOutput = byteOut
    }
    return byteOut.toString(Charsets.UTF_8.name()).trim()
}

// Function to get the latest commit message
fun latestCommitMessage(): String {
    return executeGitCommand("log", "-1", "--pretty=%B")
}

// Function to get the short commit hash
fun getShortCommitHash(): String {
    return executeGitCommand("rev-parse", "--short", "HEAD")
}

val versionString: String = project.version as String
val isRelease: Boolean = !versionString.contains('-')

val suffixedVersion: String =
    if (isRelease) {
        versionString
    } else {
        // Append the short commit hash to the version for snapshots
        "$versionString+${getShortCommitHash()}"
    }

// Use the latest commit message for the changelog
val changelogContent: String = latestCommitMessage()

tasks.register("printIsRelease") {
    doLast {
        val isRelease = !project.version.toString().contains('-')
        println(if (isRelease) "true" else "false")
    }
}

val integrationTestJarTask = tasks.register<ShadowJar>("integrationTestJar") {
    archiveClassifier.set("tests")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    dependsOn("compileIntegrationTestKotlin")

    // Include integration test output
    from(sourceSets["integrationTest"].output)

    // Use 'project.configurations' to avoid shadowing
    project.configurations["integrationTestRuntimeClasspath"].forEach { file: File ->
        from(if (file.isDirectory) file else zipTree(file))
    }

    // Exclude signature files to prevent SecurityException
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/*.EC")
    exclude("META-INF/*.MF")
    exclude("module-info.class")
    exclude("META-INF/versions/**/module-info.class")
}

val serverRunDir = file("run")

// Setup server.properties
val writeServerProperties = tasks.register<WriteProperties>("writeProperties") {
    encoding = "UTF-8"
    property("online-mode", false)
    destinationFile.set(serverRunDir.resolve("server.properties"))
}

// Configure the runServer task
tasks.named<RunServer>("runServer") {
    dependsOn(writeServerProperties)
    runDirectory.set(serverRunDir)

    minecraftVersion("1.19.2")
    jvmArgs("-Dcom.mojang.eula.agree=true")

    // Use the task variables to reference the output files
    pluginJars.from(shadowJarTask.flatMap { it.archiveFile })
    pluginJars.from(integrationTestJarTask.flatMap { it.archiveFile })
}

tasks.register("integrationTest") {
    group = "verification"
    description = "Runs integration tests with a live Paper server."

    dependsOn(shadowJarTask, integrationTestJarTask, tasks.named("runServer"))
    finalizedBy("checkTestResults") // Ensure we check test results after the server stops

    // No need to execute runServer manually; Gradle will handle task execution order
}

tasks.register("checkTestResults") {
    doLast {
        // Read the test results file generated by the test plugin
        val resultFile = file("build/run/plugins/YourPluginDirectory/test-results.txt")

        if (!resultFile.exists()) {
            throw GradleException("Test results file not found. Tests may not have run correctly.")
        }

        val result = resultFile.readText().trim()
        if (result == "FAIL") {
            throw GradleException("Integration tests failed.")
        } else if (result != "PASS") {
            throw GradleException("Unknown test result: $result")
        }

        // If result is "PASS", tests succeeded
        println("Integration tests passed.")
    }
}

hangarPublish {
    publications.register("plugin") {
        version.set(suffixedVersion)
        channel.set(if (isRelease) "Release" else "Snapshot")
        id.set("OldCombatMechanics")
        apiKey.set(System.getenv("HANGAR_API_TOKEN"))
        changelog.set(System.getenv("HANGAR_CHANGELOG") ?: changelogContent)
        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.shadowJar.flatMap { it.archiveFile })
                platformVersions.set(paperVersion)
            }
        }
    }
}
