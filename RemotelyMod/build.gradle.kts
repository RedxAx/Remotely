import dev.deftu.gradle.utils.ModLoader
import dev.deftu.gradle.utils.version.MinecraftVersions
import dev.deftu.gradle.utils.includeOrShade
import org.gradle.jvm.tasks.Jar
import java.util.Properties

plugins {
    java
    kotlin("jvm")
    id("dev.deftu.gradle.multiversion")
    id("dev.deftu.gradle.tools")
    id("dev.deftu.gradle.tools.resources")
    id("dev.deftu.gradle.tools.bloom")
    id("dev.deftu.gradle.tools.shadow")
    id("dev.deftu.gradle.tools.minecraft.loom")
    id("com.hypherionmc.modutils.modpublisher") version "2.1.8"
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
    gradlePluginPortal()
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.nucleoid.xyz/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://maven.firstdark.dev/releases")

    flatDir {
        dirs(rootProject.file("libs"))
    }
}

dependencies {

    val remotely = "dev.restudio:Remotely-App:1.0"
    val reScreen = "dev.restudio:ReScreen:1.0"
    val remodel = "dev.restudio:Remodel:1.0.0"
    val rebase = "dev.restudio:Rebase:1.0-SNAPSHOT"

    implementation(remotely)
    implementation(reScreen)
    implementation(remodel)
    implementation(rebase)

    shade(remotely)
    shade(reScreen)
    shade(remodel)
    shade(rebase)

    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("com.github.javakeyring:java-keyring:1.0.4")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    implementation("org.jetbrains.pty4j:pty4j:0.13.10-1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.54")
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

    shade("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    shade("com.hierynomus:sshj:0.40.0")
    shade("com.github.javakeyring:java-keyring:1.0.4")
    shade("net.java.dev.jna:jna-platform:5.13.0")
    shade("com.vladsch.flexmark:flexmark-all:0.64.8")
    shade("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    shade("org.java-websocket:Java-WebSocket:1.5.7")
    shade("org.jetbrains.pty4j:pty4j:0.13.10-1")
    shade("org.jetbrains.jediterm:jediterm-core:3.54")
    shade("org.jetbrains.jediterm:jediterm-pty:2.69")

    with(libs.textile.get()) {
        implementation(this)
        val modDep = modImplementation("${this.group}:${this.name}-$mcData:${this.version}")

        includeOrShade(this)
        modDep?.let { includeOrShade(it) }
    }

    with(libs.omnicore.get()) {
        val modDep = modImplementation("${this.group}:${this.name}-$mcData:${this.version}")

        modDep?.let { includeOrShade(it) }
    }

    if (mcData.isFabric) {
        modImplementation("net.fabricmc:fabric-language-kotlin:${mcData.dependencies.fabric.fabricLanguageKotlinVersion}")

        if (mcData.isLegacyFabric) {
            modImplementation("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${mcData.dependencies.legacyFabric.legacyFabricApiVersion}")
        } else {
            modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.dependencies.fabric.fabricApiVersion}")
        }
    }

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

    configureEach {
        val taskName = name.lowercase()
        if (taskName.contains("publish") && (taskName.contains("modrinth") || taskName.contains("curse") || taskName.contains("github"))) {
            mustRunAfter(project.tasks.named("build"))
        }
    }
}


publisher {

    apiKeys {
        val properties = Properties().apply {
            val envFile = rootProject.file("env.properties")
            if (envFile.exists()) {
                envFile.inputStream().use { fis ->
                    load(fis)
                }
            }
        }

        val modrinthToken = properties.getProperty("MODRINTH_TOKEN", System.getenv("MODRINTH_TOKEN"))
        val curseToken = properties.getProperty("CURSEFORGE_TOKEN", System.getenv("CURSEFORGE_TOKEN"))

        fun configurePublishing(token: String?, serviceName: String, config: (String) -> Unit) {
            if (token != null) {
                config(token)
                if (token.isNotBlank()) {
                    println("$serviceName publishing enabled.")
                } else {
                    println("$serviceName publishing disabled. No API key found (blank).")
                }
            } else {
                println("$serviceName publishing disabled. No API key found (null)")
            }
        }

        configurePublishing(modrinthToken, "Modrinth") { modrinth(it) }
        configurePublishing(curseToken, "CurseForge") { curseforge(it) }

        curseID.set("1224352")
        modrinthID.set("remotely")

        projectVersion.set(modData.version)

        displayName.set("Remotely ${modData.version} (${if (mcData.isFabric) "Fabric" else "NeoForge"} ${mcData.version})")

        gameVersions.set(listOf(mcData.version.toString()))

        val currentLoader = when {
            mcData.isFabric -> "fabric"
            mcData.isNeoForge -> "neoforge"
            else -> "forge"
        }
        loaders.set(listOf(currentLoader))

        versionType.set("beta")

        val targetTask = tasks.named<Jar>("remapJar")

        artifact.set(targetTask.flatMap { it.archiveFile })

        changelog.set("First Beta Release of Remotely 2.0.0!")
        disableEmptyJarCheck.set(true)
    }
}
