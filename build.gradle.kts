/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.papermc.hangarpublishplugin.model.Platforms
import java.io.ByteArrayOutputStream

val paperVersion: List<String> = (property("gameVersions") as String)
        .split(",")
        .map { it.trim() }


plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    // Protocollib
    maven("https://repo.dmulloy2.net/repository/public/")
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
    compileOnly("org.spigotmc:spigot-api:1.20.6-R0.1-SNAPSHOT")
    // ProtocolLib
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")

     //For ingametesting
    // Mojang mappings for NMS
    /*
    compileOnly("com.mojang:authlib:4.0.43")
    paperweight.paperDevBundle("1.19.2-R0.1-SNAPSHOT")
    // For reflection remapping
    implementation("xyz.jpenilla:reflection-remapper:0.1.1")
     */
}

group = "kernitus.plugin.OldCombatMechanics"
version = "2.0.4" // x-release-please-version
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

tasks.named<ShadowJar>("shadowJar") {
    dependsOn("jar")
    archiveFileName.set("${project.name}.jar")
    dependencies {
        relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats")
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

val suffixedVersion: String = if (isRelease) {
    versionString
} else {
    // Append the short commit hash to the version for snapshots
    "$versionString+${getShortCommitHash()}"
}

// Use the latest commit message for the changelog
val changelogContent: String = latestCommitMessage()

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
