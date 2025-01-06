plugins {
    id("io.papermc.paperweight.userdev") version "1.7.7"
}

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.19.2-R0.1-SNAPSHOT")
    implementation(project(":nms"))
}

group = "kernitus.plugin.OldCombatMechanics.nms.v1_19_1"
version = "0.0.1"
