/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.papermc.paperweight.userdev") version "1.5.10"
}

repositories {
    mavenCentral()
    // Spigot API
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    // Placeholder API for reflection utils
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // CodeMC Repo for bStats
    maven("https://repo.codemc.org/repository/maven-public/")
    // Netty library from Minecraft
    maven("https://libraries.minecraft.net/")
    // Spartan API
    maven("https://repo.crazycrew.us/api")
    maven("https://repo.minebench.de/")

    // For local maven repo (mojang mappings jar)
    mavenLocal()
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.0.2")
    //compileOnly("org.spigotmc:spigot-api:1.20-R0.1-SNAPSHOT")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.93.Final")
    compileOnly("me.clip:placeholderapi:2.11.3")
    // Spartan API
    compileOnly("me.vagdedes.spartan:SpartanAPI:9.0")
    // Mojang mappings for NMS
    compileOnly("com.mojang:authlib:4.0.43")

    paperweight.paperDevBundle("1.19.2-R0.1-SNAPSHOT")
}

group = "kernitus.plugin.OldCombatMechanics"
version = "1.12.3-beta"
description = "OldCombatMechanics"

java {
    toolchain {
        // At least 17 required for MC 1.19
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
    main {
        java {
            //exclude("kernitus/plugin/OldCombatMechanics/tester/**")
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
    //archiveFileName.set("${project.name}.jar")
    dependencies {
        relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats")
    }
}

tasks.reobfJar {
    outputJar.set(File(buildDir, "libs/${project.name}.jar"))
}

tasks.assemble {
    dependsOn("reobfJar")
}