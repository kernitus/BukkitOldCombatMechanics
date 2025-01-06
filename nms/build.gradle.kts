import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Define repositories common to all NMS subprojects
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

// Configure subprojects
subprojects {
    // Apply the Kotlin JVM plugin
    apply(plugin = "org.jetbrains.kotlin.jvm")

    // Set the group and version for subprojects
    group = "kernitus.plugin.OldCombatMechanics.nms"
    version = "0.0.1"

    // Set up common repositories for subprojects
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    // Now that the Kotlin plugin is applied, we can use its configurations
    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        // For reflection remapping
        implementation("xyz.jpenilla:reflection-remapper:0.1.1")
        // To use reflection utils in main package
        //implementation("kernitus.plugin.OldCombatMechanics")?????
    }

    // Configure the Kotlin compiler options
    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
