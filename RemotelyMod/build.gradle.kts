import dev.deftu.gradle.utils.ModLoader
import dev.deftu.gradle.utils.version.MinecraftVersions
import dev.deftu.gradle.utils.includeOrShade
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.jvm.tasks.Jar
import java.util.Properties

plugins {
    java
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
    useDevAuth("1.2.2")
    useMixinExtras("0.5.0")

    if (!mcData.isNeoForge) {
        useMixinRefMap(modData.id)
    }

    if (mcData.isForge) {
        useForgeMixin(modData.id)
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


    if (mcData.isFabric) {
        if (mcData.isLegacyFabric) {
            modImplementation("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${mcData.dependencies.legacyFabric.legacyFabricApiVersion}")
        } else {
            modImplementation("net.fabricmc.fabric-api:fabric-api:${mcData.dependencies.fabric.fabricApiVersion}")
        }
    }

    if (mcData.version <= MinecraftVersions.VERSION_1_12_2) {
        modImplementation(includeOrShade("org.spongepowered:mixin:0.7.11-SNAPSHOT")!!)
    }
}

tasks {
    configureEach {
        val taskName = name.lowercase()
        if (taskName.contains("publish") && (taskName.contains("modrinth") || taskName.contains("curse") || taskName.contains("github"))) {
            mustRunAfter(project.tasks.named("build"))
        }
    }

    register("runAllFabric") {
        group = "run"
        description = "Launches runClient for all *-fabric versions in parallel."
        doLast {
            val fabricProjects = rootProject.subprojects.filter { it.name.endsWith("-fabric") }
            if (fabricProjects.isEmpty()) return@doLast
            val gradleCmd = if (System.getProperty("os.name").lowercase().contains("win")) {
                rootProject.file("gradlew.bat").absolutePath
            } else {
                rootProject.file("gradlew").absolutePath
            }
            val processes = fabricProjects.map { project ->
                ProcessBuilder(gradleCmd, "${project.path}:runClient")
                    .directory(rootProject.projectDir)
                    .inheritIO()
                    .start()
            }
            val exitCodes = processes.map { it.waitFor() }
            val failed = exitCodes.withIndex().filter { it.value != 0 }
            if (failed.isNotEmpty()) {
                throw GradleException("runAllFabric failed for ${failed.size} project(s)")
            }
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

        changelog.set(
            """
                ### Remotely Changes:
                - Fix: Remote Server Importing Opening.
                - Chore: Removed Unused Debs & Cleanup.
                - Fix: ServerDetailsScreen Opening Behavior In Desktop Mode.
                - Fix: Minimal Button Position On TitleScreen.
                - Adapt/Feat: SSH-Keys Support | Better Connection Feedback.
                - Adapt: Rebase User System Overhaul.
                - Feat: ServerDetailsScreen Isolated Tabs For Desktop Mode.
                - Fix: Desktop Screen Opening Fixes.
                - Fix: Black/White Text When Should Be Invisible (HotFix).
                - Chore: Class Names Refactor For Rematrix.
                - Feat(Rematrix): 1.20.1 Support.
                - Feat(Rematrix): 1.21.[1, 4] Support.
                - Feat(Rematrix): 1.21.[8, 6, 5] Support.
                - ReWrite/Feat: Rematrix Init.
                - Fix: Proper ReStudio Host Status Management.
                - Adapt: ReScreen's Desktop Apps API.
                - Adapt: ReScreen Desktop Mode Changes.
                - Chore: Updated License To Remove Ambiguity.
                - Feat: Status Bar For Resource Tracking & Server Info.
                - Feat(Huge): Desktop Mode.
                - Adapt: ReStudio Modpack Installation.
                - Feat: Hide/Unhide Servers.
                - Visual: Updated The Server Creation Popup Look.
                - Fix: Asyncing Stuff To Prevent Many Freezes.
                - Fix: Remote Host Popup Improvements.
                - Fix: Some FlowEditor Bugs.
                - Feat: `ALT+X` To Toggle Game/Remotely.

                ### Rebase Changes:
                - Fix: Dynamic Width For StatusBar In FileEditorScreen.
                - Fix: Incorrect Tab Opening For Importing.
                - Fix: External Path Getting Deleted On Scan.
                - Fix: Importing Remote Servers Goes To Local.
                - Fix: Critical Resource Downloading Issues.
                - Fix: Small Changes To Settings.
                - Fix: Tab Closing Behavior In Desktop Mode.
                - Feat: SSH-Keys Support.
                - Feat: Overhauled User System.
                - Feat: Isolated Tabs For Desktop Mode.
                - Feat: Many PixelArtEditor Improvements.
                - Feat: Dynamic Context Menu Actions.
                - Feat: Pixel Art Editor.
                - Fix: ReStudio Servers Status Stuff.
                - Feat: FileEditor Status Bar Impl.
                - Adapt: ReScreen's Desktop Apps API.
                - Adapt: ReScreen's Desktop Mode.
                - Fix: Modpack Installation Is Now Reliable.
                - Feat: ResourceUsageFeature Full Implementation.
                - Feat(Huge): Desktop Mode.
                - Feat: Status Bar For FileExplorer.
                - Feat/Fix: ResourceInstallationService And ReStudio Modpack Handling.
                - Feat: Hide Flag For Instances.
                - Feat: Made TextArea Line Highlight Toggleable.
                - Fix: Duplicate Posting Possibility.
                - Feat: Replaced Binary Agent With Shell Script.
                - Feat: Terminal Optimizations.
                - Feat: Folia Support.
                - Fix: "New Issue" Button Disappear When Set-To-Parent.
                - Fix: Start Script For Servers.

                ### ReScreen Changes:
                - Feat: Width Bounds For StatusBarBuilder.
                - Fix: Entrance Delay Calculation For Desktop Mode.
                - Feat: More ScreenWindowWidget UX Improvements.
                - Fix: Scissors Now Animated With Elevation In IconButton.
                - Fix: Thumbnails For Minimized Windows.
                - Fix: Tab Grouping Behaviors.
                - Fix: Incorrect TabsManager Width In Desktop Mode.
                - Feat: Native pickImageFileAsync.
                - Fix: Screens Rescales When Applying Settings.
                - Fix: More Polishments For Desktop Mode.
                - Feat: Defensive Layering For Desktop Mode.
                - Feat: Smoooooooooooooooooth Desktop Mode.
                - Fix: More Desktop Mode Polishments.
                - Feat: Desktop Mode Overhaul.
                - Feat/Fixes: More Desktop Mode Features & Improvements.
                - Visual: Small Adjustments.
                - Feat(Huge): Desktop Mode.
                - Fix: All Custom MouseCursor Issues.
                - Feat: New `BreadcrumbWidget`.
                - Feat: StatusBar API For ReScreen.
                - Feat: Text Batching.
            """.trimIndent()
        )

        modrinthDepends.required.set(listOf("fabric-api"))
    }
}
