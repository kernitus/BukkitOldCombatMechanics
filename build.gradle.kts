/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonSlurper
import io.papermc.hangarpublishplugin.model.Platforms
import org.gradle.api.Action
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.file.FileCopyDetails
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.runpaper.task.RunServer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.Serializable
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest

val paperVersion: List<String> =
    (property("gameVersions") as String)
        .split(",")
        .map { it.trim() }

plugins {
    `java-library`
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    idea
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
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
    maven("https://repo.papermc.io/repository/maven-public/")
    // Spigot API
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://oss.sonatype.org/content/repositories/central")
    // PacketEvents
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    // Placeholder API
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // CodeMC Repo for bStats
    maven("https://repo.codemc.org/repository/maven-public/")
    // Auth library from Minecraft
    maven("https://libraries.minecraft.net/")
}

group = "kernitus.plugin.OldCombatMechanics"
version = "2.3.1" // x-release-please-version
description = "OldCombatMechanics"

java {
    toolchain {
        // We can build with Java 17 but still support MC >=1.9
        // This is because MC >=1.9 server can be run with higher Java versions
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets {
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
    create("integrationTestServerPlugins") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
}

configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}
configurations.named("integrationTestCompileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
}

dependencies {
    implementation("org.bstats:bstats-bukkit:3.1.0")
    // Shaded in by Bukkit
    compileOnly("io.netty:netty-all:4.1.130.Final")
    // Placeholder API
    compileOnly("me.clip:placeholderapi:2.11.6")
    // For BSON file serialisation
    implementation("org.mongodb:bson:5.6.2")
    // Spigot
    compileOnly("org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    // JSR-305 annotations (javax.annotation.Nullable)
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    // PacketEvents
    implementation("com.github.retrooper:packetevents-spigot:2.11.2")
    // XSeries
    implementation("com.github.cryptomorin:XSeries:13.6.0")

    // For ingametesting
    // Mojang mappings for NMS
    /*
    compileOnly("com.mojang:authlib:6.0.54")
    paperweight.paperDevBundle("1.19.2-R0.1-SNAPSHOT")
    // For reflection remapping
    implementation("xyz.jpenilla:reflection-remapper:0.1.3")
     */

    // Integration test dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.0")
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.0")
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-test:2.3.0")
    add("integrationTestImplementation", "org.jetbrains.kotlin:kotlin-reflect:2.3.0")
    add("integrationTestImplementation", "io.kotest:kotest-runner-junit5-jvm:5.9.1")
    add("integrationTestImplementation", "io.kotest:kotest-assertions-core-jvm:5.9.1")
    add("integrationTestImplementation", "net.kyori:adventure-api:4.26.1")
    add("integrationTestImplementation", "xyz.jpenilla:reflection-remapper:0.1.3")
    add("integrationTestCompileOnly", "org.spigotmc:spigot-api:1.21.11-R0.1-SNAPSHOT")
    add("integrationTestCompileOnly", "com.mojang:authlib:6.0.54")
    add("integrationTestCompileOnly", "io.netty:netty-all:4.1.130.Final")
}

// Substitute ${pluginVersion} in plugin.yml with version defined above
class ExpandPluginVersionAction(
    private val version: String,
) : Action<FileCopyDetails>,
    Serializable {
    override fun execute(details: FileCopyDetails) {
        details.expand(mapOf("pluginVersion" to version))
    }
}

val pluginVersion = project.version.toString()
val expandPluginVersionAction = ExpandPluginVersionAction(pluginVersion)
tasks.named<Copy>("processResources") {
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml", expandPluginVersionAction)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
}

val shadowJarTask =
    tasks.named<ShadowJar>("shadowJar") {
        dependsOn("jar")
        archiveFileName.set("${project.name}.jar")
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            relocate("org.bstats", "kernitus.plugin.OldCombatMechanics.lib.bstats")
            relocate("com.cryptomorin.xseries", "kernitus.plugin.OldCombatMechanics.lib.xseries")
            relocate("com.github.retrooper.packetevents", "kernitus.plugin.OldCombatMechanics.lib.packetevents.api")
            relocate("io.github.retrooper.packetevents", "kernitus.plugin.OldCombatMechanics.lib.packetevents.impl")
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
    // dependsOn("reobfJar")
    dependsOn("shadowJar")
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

val relocateIntegrationTestClasses =
    tasks.register<ShadowJar>("relocateIntegrationTestClasses") {
        archiveClassifier.set("tests-relocated")
        dependsOn("compileIntegrationTestKotlin")
        configurations = emptyList()
        from(sourceSets["integrationTest"].output)
        relocate("com.github.retrooper.packetevents", "kernitus.plugin.OldCombatMechanics.lib.packetevents.api")
        relocate("io.github.retrooper.packetevents", "kernitus.plugin.OldCombatMechanics.lib.packetevents.impl")
    }

val integrationTestJarTask =
    tasks.register<Jar>("integrationTestJar") {
        archiveClassifier.set("tests")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        dependsOn(relocateIntegrationTestClasses)

        from(relocateIntegrationTestClasses.flatMap { it.archiveFile }.map { zipTree(it.asFile) })

        project.configurations["integrationTestRuntimeClasspath"].forEach { file: File ->
            if (file.name.contains("packetevents", ignoreCase = true)) {
                return@forEach
            }
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

val defaultIntegrationTestVersions =
    listOf(integrationTestMinecraftVersion, "1.21.11", "1.12", "1.9.4")
        .distinct()

val integrationTestVersions: List<String> =
    (findProperty("integrationTestVersions") as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.ifEmpty { defaultIntegrationTestVersions }
        ?: defaultIntegrationTestVersions

val integrationTestJavaVersionLegacy =
    (findProperty("integrationTestJavaVersionLegacy") as String?)?.toInt() ?: 17
val integrationTestJavaVersionLegacyPre13 =
    (findProperty("integrationTestJavaVersionLegacyPre13") as String?)?.toInt() ?: 8
val integrationTestJavaVersionLegacy16 =
    (findProperty("integrationTestJavaVersionLegacy16") as String?)?.toInt() ?: 11
val integrationTestJavaVersionModern =
    (findProperty("integrationTestJavaVersionModern") as String?)?.toInt() ?: 25

fun parseMinecraftVersion(version: String): Triple<Int, Int, Int> {
    val parts = version.split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return Triple(major, minor, patch)
}

fun needsLegacyVanillaJar(version: String): Boolean {
    val (major, minor, _) = parseMinecraftVersion(version)
    return major == 1 && minor <= 12
}

fun requiresModernJava(version: String): Boolean {
    val (major, minor, patch) = parseMinecraftVersion(version)
    if (major > 1) return true
    if (minor > 20) return true
    return minor == 20 && patch >= 5
}

fun requiredJavaVersion(version: String): Int {
    if (needsLegacyVanillaJar(version)) return integrationTestJavaVersionLegacyPre13
    val (_, minor, _) = parseMinecraftVersion(version)
    if (minor <= 16) return integrationTestJavaVersionLegacy16
    return if (requiresModernJava(version)) integrationTestJavaVersionModern else integrationTestJavaVersionLegacy
}

data class KotestSummary(
    val specsPassed: Int?,
    val specsFailed: Int?,
    val specsTotal: Int?,
    val testsPassed: Int?,
    val testsFailed: Int?,
    val testsIgnored: Int?,
    val testsTotal: Int?,
    val failures: List<String>,
    val failureDetails: List<String>,
)

fun parseKotestSummary(logFile: File): KotestSummary? {
    if (!logFile.exists()) return null

    val lines = logFile.readLines()
    var inFailures = false
    var blockContext: String? = null // "specs" or "tests"

    var specsPassed: Int? = null
    var specsFailed: Int? = null
    var specsTotal: Int? = null

    var testsPassed: Int? = null
    var testsFailed: Int? = null
    var testsIgnored: Int? = null
    var testsTotal: Int? = null

    val failures = mutableListOf<String>()
    val failureDetails = mutableListOf<String>()

    val numberLine = Regex("^(\\d+) (passed|failed|ignored|total)$")
    val inlineSpecsLine = Regex("^Specs:\\s*(\\d+) passed,\\s*(\\d+) failed,\\s*(\\d+) total$")
    val inlineTestsLine = Regex("^Tests:\\s*(\\d+) passed,\\s*(\\d+) failed,\\s*(\\d+) ignored,\\s*(\\d+) total$")
    val stackTopLine = Regex("^\\s*[^\\s].*\\(([^)]+:\\d+)\\)\\s*$")

    var lastTestName: String? = null
    var pendingFailureName: String? = null
    var pendingFailureMessage: String? = null

    for (raw in lines) {
        val line = raw.substringAfter("]:", raw).trim()

        when {
            line.startsWith(">> There were test failures") -> {
                inFailures = true
                blockContext = null
            }

            line.startsWith("Specs:") -> {
                inFailures = false
                val inline = inlineSpecsLine.matchEntire(line)
                if (inline != null) {
                    specsPassed = inline.groupValues[1].toInt()
                    specsFailed = inline.groupValues[2].toInt()
                    specsTotal = inline.groupValues[3].toInt()
                    blockContext = null
                } else {
                    blockContext = "specs"
                }
            }

            line.startsWith("Tests:") -> {
                inFailures = false
                val inline = inlineTestsLine.matchEntire(line)
                if (inline != null) {
                    testsPassed = inline.groupValues[1].toInt()
                    testsFailed = inline.groupValues[2].toInt()
                    testsIgnored = inline.groupValues[3].toInt()
                    testsTotal = inline.groupValues[4].toInt()
                    blockContext = null
                } else {
                    blockContext = "tests"
                }
            }

            inFailures -> {
                val cleaned = line.removePrefix("-").trim()
                if (cleaned.isNotBlank()) {
                    failures.add(cleaned)
                }
            }

            line.startsWith("- ") -> {
                lastTestName = line.removePrefix("-").trim()
            }

            line == "FAILED" -> {
                pendingFailureName = lastTestName
                pendingFailureMessage = null
            }

            pendingFailureName != null && pendingFailureMessage == null && line.isNotBlank() -> {
                // First line after FAILED is usually the assertion message.
                pendingFailureMessage = line
            }

            pendingFailureName != null && pendingFailureMessage != null -> {
                val match = stackTopLine.matchEntire(line)
                if (match != null) {
                    val location = match.groupValues[1]
                    val name = pendingFailureName!!
                    val message = pendingFailureMessage!!
                    failureDetails.add("$name: $message ($location)")
                    pendingFailureName = null
                    pendingFailureMessage = null
                }
            }

            blockContext != null -> {
                val match = numberLine.matchEntire(line)
                if (match != null) {
                    val value = match.groupValues[1].toInt()
                    when (blockContext) {
                        "specs" -> {
                            when (match.groupValues[2]) {
                                "passed" -> specsPassed = value
                                "failed" -> specsFailed = value
                                "total" -> specsTotal = value
                            }
                        }

                        "tests" -> {
                            when (match.groupValues[2]) {
                                "passed" -> testsPassed = value
                                "failed" -> testsFailed = value
                                "ignored" -> testsIgnored = value
                                "total" -> testsTotal = value
                            }
                        }
                    }
                }
            }
        }
    }

    if (
        specsPassed == null && specsFailed == null && specsTotal == null &&
        testsPassed == null && testsFailed == null && testsIgnored == null && testsTotal == null &&
        failures.isEmpty()
    ) {
        return null
    }

    return KotestSummary(
        specsPassed,
        specsFailed,
        specsTotal,
        testsPassed,
        testsFailed,
        testsIgnored,
        testsTotal,
        failures,
        failureDetails.distinct(),
    )
}

fun sha1(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
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

tasks.withType<RunServer>().configureEach {
    notCompatibleWithConfigurationCache("run-paper tasks access Project at execution time.")
}

val integrationTestMatrixTasks = mutableListOf<TaskProvider<Task>>()
var previousMatrixTask: TaskProvider<Task>? = null

val kotestSpecFilterProvider = providers.systemProperty("kotest.filter.specs")
val kotestTestFilterProvider = providers.systemProperty("kotest.filter.tests")

fun versionTaskSuffix(version: String): String = version.replace(Regex("[^A-Za-z0-9]"), "_")

for (version in integrationTestVersions) {
    val suffix = versionTaskSuffix(version)
    val runDir = file("run/$version")
    val resultFile = runDir.resolve("plugins/OldCombatMechanicsTest/test-results.txt")
    val failuresFile = runDir.resolve("plugins/OldCombatMechanicsTest/test-failures.txt")
    val vanillaCacheFile = runDir.resolve("cache/mojang_$version.jar")
    val logFile = layout.buildDirectory.file("integration-test-logs/$suffix.log")

    val writePropsTask =
        tasks.register<WriteProperties>("writeProperties$suffix") {
            encoding = "UTF-8"
            property("online-mode", false)
            destinationFile.set(runDir.resolve("server.properties"))
        }

    val downloadVanillaTask =
        if (needsLegacyVanillaJar(version)) {
            tasks.register("downloadVanilla$suffix") {
                outputs.file(vanillaCacheFile)
                notCompatibleWithConfigurationCache("Downloads vanilla server jar for legacy Paper versions.")
                doLast {
                    val slurper = JsonSlurper()
                    val manifestText =
                        URI("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
                            .toURL()
                            .readText()
                    val manifest = slurper.parseText(manifestText) as Map<*, *>
                    val versionsList =
                        manifest["versions"] as? List<Map<*, *>>
                            ?: throw GradleException("Invalid Mojang manifest format: missing 'versions' list.")
                    val versionEntry =
                        versionsList.firstOrNull { it["id"] == version }
                            ?: throw GradleException("Minecraft version '$version' not found in Mojang manifest.")
                    val versionUrl = versionEntry["url"] as String
                    val versionMetaText = URI(versionUrl).toURL().readText()
                    val versionMeta = slurper.parseText(versionMetaText) as Map<*, *>
                    val downloads = versionMeta["downloads"] as Map<*, *>
                    val serverInfo = downloads["server"] as Map<*, *>
                    val serverUrl = serverInfo["url"] as String
                    val serverSha1 = serverInfo["sha1"] as String

                    if (vanillaCacheFile.exists()) {
                        val existingSha1 = sha1(vanillaCacheFile)
                        if (existingSha1.equals(serverSha1, ignoreCase = true)) {
                            return@doLast
                        }
                    } else {
                        vanillaCacheFile.parentFile.mkdirs()
                    }

                    val tmpFile = Files.createTempFile("mc-server-$version-", ".jar").toFile()
                    URI(serverUrl).toURL().openStream().use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val downloadedSha1 = sha1(tmpFile)
                    if (!downloadedSha1.equals(serverSha1, ignoreCase = true)) {
                        tmpFile.delete()
                        throw GradleException(
                            "Downloaded Minecraft server jar hash mismatch for $version. Expected $serverSha1, got $downloadedSha1.",
                        )
                    }
                    tmpFile.copyTo(vanillaCacheFile, overwrite = true)
                    tmpFile.delete()
                }
            }
        } else {
            null
        }

    val runServerTask =
        tasks.register<RunServer>("runServer$suffix") {
            dependsOn(writePropsTask)
            downloadVanillaTask?.let { dependsOn(it) }
            runDirectory.set(runDir)
            minecraftVersion(version)
            jvmArgs("-Dcom.mojang.eula.agree=true")
            kotestSpecFilterProvider.orNull?.takeIf { it.isNotBlank() }?.let {
                jvmArgs("-Dkotest.filter.specs=$it")
            }
            kotestTestFilterProvider.orNull?.takeIf { it.isNotBlank() }?.let {
                jvmArgs("-Dkotest.filter.tests=$it")
            }
            if (needsLegacyVanillaJar(version)) {
                // Skip the legacy Paper "outdated build" startup sleep.
                jvmArgs("-DIReallyKnowWhatIAmDoingISwear=true")
            }
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(requiredJavaVersion(version)))
                },
            )

            pluginJars.from(shadowJarTask.flatMap { it.archiveFile })
            pluginJars.from(integrationTestJarTask.flatMap { it.archiveFile })
            pluginJars.from(configurations["integrationTestServerPlugins"])

            doFirst {
                val log = logFile.get().asFile
                log.parentFile.mkdirs()
                val stream = log.outputStream()
                standardOutput = stream
                errorOutput = stream
            }

            doLast {
                (standardOutput as? Closeable)?.close()
            }

            doFirst {
                if (resultFile.exists()) {
                    resultFile.delete()
                }
                if (failuresFile.exists()) {
                    failuresFile.delete()
                }
                val ocmConfigFile = runDir.resolve("plugins/OldCombatMechanics/config.yml")
                if (ocmConfigFile.exists()) {
                    ocmConfigFile.delete()
                }
            }
        }

    val checkTask =
        tasks.register("checkTestResults$suffix") {
            doLast {
                if (!resultFile.exists()) {
                    throw GradleException("Test results file not found for $version. Tests may not have run correctly.")
                }
                val result = resultFile.readText().trim()
                val log = logFile.get().asFile
                val summary = parseKotestSummary(log)
                summary?.let {
                    val parts = mutableListOf<String>()
                    if (it.specsTotal != null) {
                        parts.add("Specs: ${it.specsPassed ?: "?"} passed, ${it.specsFailed ?: "?"} failed, ${it.specsTotal} total")
                    }
                    if (it.testsTotal != null) {
                        parts.add(
                            "Tests: ${it.testsPassed ?: "?"} passed, ${it.testsFailed ?: "?"} failed, ${it.testsIgnored ?: 0} ignored, ${it.testsTotal} total",
                        )
                    }
                    if (it.failures.isNotEmpty()) {
                        parts.add("Failures: ${it.failures.joinToString(", ")}")
                    }
                    if (it.failureDetails.isNotEmpty()) {
                        parts.add("Reasons: ${it.failureDetails.take(2).joinToString("; ")}")
                    }
                    if (parts.isNotEmpty()) {
                        logger.lifecycle("[$version] ${parts.joinToString(" | ")}")
                    }
                } ?: run {
                    val rel = log.relativeToOrNull(project.layout.projectDirectory.asFile)?.path ?: log.absolutePath
                    logger.lifecycle("[$version] No Kotest summary parsed. Full log: $rel")
                }
                run {
                    val rel = log.relativeToOrNull(project.layout.projectDirectory.asFile)?.path ?: log.absolutePath
                    logger.lifecycle("[$version] Log: $rel")
                }
                if (failuresFile.exists()) {
                    val lines = failuresFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isNotEmpty()) {
                        logger.lifecycle("[$version] Failure details: ${lines.take(5).joinToString(" | ")}")
                    }
                }
                if (result == "FAIL") {
                    throw GradleException("Integration tests failed for $version.")
                } else if (result != "PASS") {
                    throw GradleException("Unknown test result for $version: $result")
                }
                logger.lifecycle("Integration tests passed for $version.")
            }
        }

    val testTask =
        tasks.register("integrationTest$suffix") {
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

val gitShortHashProvider =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText
        .map { it.trim() }

val gitChangelogProvider =
    providers
        .exec {
            commandLine("git", "log", "-1", "--pretty=%B")
        }.standardOutput.asText
        .map { it.trim() }

val suffixedVersionProvider =
    providers.provider {
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
