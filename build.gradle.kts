import dev.deftu.gradle.utils.version.MinecraftVersions
import dev.deftu.gradle.utils.includeOrShade

plugins {
    java
    kotlin("jvm")
    id("dev.deftu.gradle.multiversion") // Applies preprocessing for multiple versions of Minecraft and/or multiple mod loaders.
    id("dev.deftu.gradle.tools") // Applies several configurations to things such as the Java version, project name/version, etc.
    id("dev.deftu.gradle.tools.resources") // Applies resource processing so that we can replace tokens, such as our mod name/version, in our resources.
    id("dev.deftu.gradle.tools.bloom") // Applies the Bloom plugin, which allows us to replace tokens in our source files, such as being able to use `@MOD_VERSION` in our source files.
    id("dev.deftu.gradle.tools.shadow") // Applies the Shadow plugin, which allows us to shade our dependencies into our mod JAR. This is NOT recommended for Fabric mods, but we have an *additional* configuration for those!
    id("dev.deftu.gradle.tools.minecraft.loom") // Applies the Loom plugin, which automagically configures Essential's Architectury Loom plugin for you.
    id("dev.deftu.gradle.tools.minecraft.releases-v2") // Applies the Minecraft auto-releasing plugin, which allows you to automatically release your mod to CurseForge and Modrinth.
}

toolkitMultiversion {
    moveBuildsToRootProject.set(true)
}

toolkitLoomHelper {
    useDevAuth("1.2.1")
    useMixinExtras("0.5.0")

    if (!mcData.isNeoForge) {
        useMixinRefMap(modData.id)
    }

    if (mcData.isForge) {
        useForgeMixin(modData.id)
    }

    if (mcData.isForgeLike && mcData.version >= MinecraftVersions.VERSION_1_16_5) {
        useKotlinForForge()
    }
}

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.nucleoid.xyz/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {

    implementation(files(rootProject.files("libs/ReScreen-1.0.jar")))
    implementation(files(rootProject.files("libs/Remodel-1.0.0.jar")))
    implementation(files(rootProject.files("libs/Rebase-1.0-SNAPSHOT.jar")))

    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("com.github.javakeyring:java-keyring:1.0.4")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.slf4j:slf4j-simple:2.0.9")

    implementation("org.jetbrains.pty4j:pty4j:0.13.10-1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.54")
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

    includeOrShade(files(rootProject.files("libs/ReScreen-1.0.jar")))
    includeOrShade(files(rootProject.files("libs/Remodel-1.0.0.jar")))
    includeOrShade(files(rootProject.files("libs/Rebase-1.0-SNAPSHOT.jar")))

    includeOrShade("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    includeOrShade("com.hierynomus:sshj:0.40.0")
    includeOrShade("com.github.javakeyring:java-keyring:1.0.4")
    includeOrShade("com.vladsch.flexmark:flexmark-all:0.64.8")
    includeOrShade("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    includeOrShade("org.jetbrains.pty4j:pty4j:0.13.10-1")
    includeOrShade("org.jetbrains.jediterm:jediterm-core:3.54")
    includeOrShade("org.jetbrains.jediterm:jediterm-pty:2.69")

    // Add Textile and OmniCore
    with(libs.textile.get()) {
        implementation(this)
        val modDep = modImplementation("${this.group}:${this.name}-$mcData:${this.version}")
        if (mcData.isLegacyForge) {
            includeOrShade(this)
            modDep?.let { includeOrShade(it) }
        }
    }

    with(libs.omnicore.get()) {
        val modDep = modImplementation("${this.group}:${this.name}-$mcData:${this.version}")
        if (mcData.isLegacyForge) {
            modDep?.let { includeOrShade(it) }
        }
    }

    // Add (Legacy) Fabric API (these are both optional but are particularly useful).
    if (mcData.isFabric) {
        modImplementation("net.fabricmc:fabric-language-kotlin:${mcData.dependencies.fabric.fabricLanguageKotlinVersion}")

        if (mcData.isLegacyFabric) {
            // 1.8.9 - 1.13
            modImplementation("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${mcData.dependencies.legacyFabric.legacyFabricApiVersion}")
        } else {
            // 1.16.5+
            modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.dependencies.fabric.fabricApiVersion}")
        }
    }

    // Add Kotlin and Mixin in Legacy Forge
    if (mcData.version <= MinecraftVersions.VERSION_1_12_2) {
        implementation(includeOrShade(kotlin("stdlib-jdk8"))!!)
        implementation(includeOrShade("org.jetbrains.kotlin:kotlin-reflect:1.6.10")!!)

        modImplementation(includeOrShade("org.spongepowered:mixin:0.7.11-SNAPSHOT")!!)
    }
}

tasks {
    fatJar {
        if (mcData.isLegacyForge) {
            relocate("dev.deftu.textile", "${modData.group}.dependencies.textile")
            relocate("dev.deftu.omnicore", "${modData.group}.dependencies.omnicore")
        }
    }
}
