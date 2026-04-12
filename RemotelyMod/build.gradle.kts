import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.deftu.gradle.utils.version.MinecraftVersions
import dev.deftu.gradle.utils.includeOrShade
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
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

val isDropFabric = mcData.isFabric && mcData.version.isDrop
val isFabricApiRuntimeIncompatible = mcData.isFabric && mcData.version.toString() == "26.2-snapshot-1"
val fabricApiVersionOverride = (findProperty("dgt.fabric.api.version") as String?)
    ?: (findProperty("fabric.api.version") as String?)
val fabricLoaderVersion = (findProperty("dgt.fabric.loader.version") as String?)
    ?: (findProperty("fabric.loader.version") as String?)
    ?: "0.17.2"

toolkitLoomHelper {
    if (!isDropFabric) {
        useDevAuth("1.2.2")
        useMixinExtras("0.5.0")
    }

    if (!isDropFabric && !mcData.isNeoForge) {
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
    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")

    implementation("org.jetbrains.pty4j:pty4j:0.13.10-1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.54")
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

    shade("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    shade("com.hierynomus:sshj:0.40.0")
    shade("com.github.javakeyring:java-keyring:1.0.4")
    shade("net.java.dev.jna:jna-platform:5.13.0")
    shade("com.vladsch.flexmark:flexmark-all:0.64.8")
    shade("org.apache.xmlgraphics:batik-transcoder:1.19")
    shade("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    shade("org.java-websocket:Java-WebSocket:1.5.7")
    shade("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    shade("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")
    shade("org.jetbrains.pty4j:pty4j:0.13.10-1")
    shade("org.jetbrains.jediterm:jediterm-core:3.54")
    shade("org.jetbrains.jediterm:jediterm-pty:2.69")

    val fabricApiVersion = if (mcData.isFabric && !mcData.isLegacyFabric) {
        runCatching { mcData.dependencies.fabric.fabricApiVersion }.getOrNull()
            ?: fabricApiVersionOverride
            ?: error("No Fabric API version found for ${mcData.version}")
    } else {
        null
    }

    if (mcData.isFabric) {
        if (mcData.isLegacyFabric) {
            add("modImplementation", "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${mcData.dependencies.legacyFabric.legacyFabricApiVersion}")
        } else {
            val fabricApiDependency = "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion"
            if (isFabricApiRuntimeIncompatible) {
                logger.lifecycle("Skipping FabricApi For ${mcData.version} Due To Runtime Incompatibility")
            } else if (mcData.version.isDrop) {
                implementation(fabricApiDependency)
            } else {
                add("modImplementation", fabricApiDependency)
            }
        }
    }

    if (mcData.version <= MinecraftVersions.VERSION_1_12_2) {
        add("modImplementation", includeOrShade("org.spongepowered:mixin:0.7.11-SNAPSHOT")!!)
    }
}

tasks {
    val fatJar = named<ShadowJar>("fatJar")

    named<ProcessResources>("processResources") {
        inputs.property("fabric_loader_version", fabricLoaderVersion)
        filesMatching("fabric.mod.json") {
            filter { line ->
                line.replace("\${fabric_loader_version}", fabricLoaderVersion)
            }
        }
    }

    named<Jar>("jar") {
        if (isDropFabric) {
            dependsOn(fatJar)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            from(fatJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
        }
    }

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

        val publishGameVersions = mapOf(
            "26.1-pre-1" to listOf("26.1", "26.1.1", "26w14a"),
            "1.21.11" to listOf("1.21.11"),
            "1.21.10" to listOf("1.21.10", "1.21.9"),
            "1.21.8" to listOf("1.21.8", "1.21.7", "1.21.6"),
            "1.21.6" to listOf("1.21.8", "1.21.7", "1.21.6"),
            "1.21.5" to listOf("1.21.5"),
            "1.21.4" to listOf("1.21.4", "1.21.3", "1.21.2"),
            "1.21.1" to listOf("1.21.1", "1.21"),
            "1.20.1" to listOf("1.20.1", "1.20")
        )
        gameVersions.set(publishGameVersions[mcData.version.toString()] ?: listOf(mcData.version.toString()))

        val currentLoader = when {
            mcData.isFabric -> "fabric"
            mcData.isNeoForge -> "neoforge"
            else -> "forge"
        }
        loaders.set(listOf(currentLoader))

        versionType.set("release")

        val targetTask = tasks.named<Jar>(if (isDropFabric) "jar" else "remapJar")

        artifact.set(targetTask.flatMap { it.archiveFile })

        changelog.set(
            """
                ### Remotely Changes:
                - Fix: Import Server Visible In Reactor.
                - Feat: Better Reactor Plan Listing On Server Creation.
                - Feat: Proper SshBackend Server Starting.
                - Feat: Big ServerManagerScreen Improvements & Reactor For Everyone.
                - Fix: Application Update Manager Config.
                - Feat: Git-Like Server DevMode For Remote Servers (WIP).
                - Fix: Deleting Resource Doesn't Unlist It.
                - Fix: Stuck In Login After A Successful One.
                - Chore: Update License.
                - Feat: Backups Overhaul.
                - Feat: Remotely No Longer Forces Login.
                - Chore: Moved Minecraft Assets Logic To Rebase.
                - Fix: Freeze When Opening ServerConfigurationScreen.
                - Fix: Freezes When Switching Tabs.
                - Feat(HUGE): Full Minecraft Assets Management And Rendering Via ReScreen.
                - Fix: Opening ServerDetailsScreen Triggered Freezing Synchronous Slow Loading.
                - Feat: Server Deletion Feedback.
                - Fix: Inverted ServerExtraSettingsController View Flag.
                - Adapt: Rebase LSP Integration.
                - Feat: Messy 26.1-Pre-1 Support.
                - Feat(ReSync): Extend World Management: Inventory Groups, Signs, & More.
                - Feat(Huge): ReSync World Management Integration.
                - Feat: New Server Import Mode.
                - Feat(HUGE): New Modular PlayerManagementScreen, ReSync Adapt.
                - Fix: Settings Never Get Saved For ReStudio Servers.
                - Feat: ReScreen MouseCursor Visible In Normal Minecraft.
                - Adapt: MouseCursor Reactive Widgets.
                - Fix: Rematrix Suspended Screen.
                - Feat(HUGE): ReSync Scoreboards & Tabs.
                - Fix: Prevent ServerManagerScreen Reinit.
                - Fix(Mod): Opening ServerManagerScreen Resets it.
                - Fix(Mod): `ALT+X` Shortcut Leaks "x" Into Text Input.
                - Feat: ReSync Commands Integration.
                - Fix: FlowEditorScreen Opening Twice.
                - Fix: Wrong GuiDesigner Edit Button Placement.
                - Feat: GuiDesignerScreen Re/Undoability.
                - Feat: Player Inventory Manipulation.
                - Feat: WorldMap Player Data Integration.
                - Fix: Custom `PlayerAction`s Not Reaching Terminal Execution.
                - Feat: Actually Smart Player Entry Action Updates.
                - Feat: Instant PlayerAction Registration.
                - Fix: Improved SSH-Servers Icon Management.
                - Fix: Errors With StreamDataParser.
                - Fix: Better Caching Flow For Resources.
                - Chore: Removed Redundant DebugLogs.
                - Fix: Bad Looking Stats Entries.

                ### Rebase Changes:
                - Fix: File Explorer Refetches Win-Quick Access Every Time.
                - Adapt: ReScreen Instanced Loading Animation.
                - Feat: Modpack Reactors **Creation**.
                - Fix: Critical Huge Memory Leak When Browsing Resources.
                - Feat: Big Improvements For SshBackend Server Lifecycle Detection.
                - Feat: Redirect To Sign-In Feedback Pages.
                - Feat: ServerTwinManager.
                - Feat: 20x Faster Remote Resource Indexing.
                - Fix: Unregistered Resource Deletion.
                - Feat: Minecraft Assets Management.
                - Feat: Backups System Overhaul.
                - Fix: Inaccurate Filters.
                - Feat: LSP UX Improvements.
                - Feat: Quick-Access Improvements.
                - Feat: Async Instance Deletion.
                - Feat: Windows Quick-Access Pins For File Explorer.
                - Feat: Dynamic Instance Software.
                - Adapt: ReStudio Changes.
                - Feat: General Propose Auto LSP, Editor Integration.
                - Fix: Resource Browsing Filters Stuff.
                - Feat: GameVersion Assets Control.
                - Feat: ReSync World Map Optimizations.
                - Feat: Dynamic & User Friendly Explorer Import Mode.
                - Feat: Servers No Longer Hardcoded `server.jar`.
                - Feat: Faster ReStudio File Explorer.
                - Feat: Remote File External Opening Handling.
                - Feat: Configurable FileExplorer Click/DoubleClick.
                - Feat: Configurable FileExplorer Per-Tab Sort.
                - Fix: Unreliable Transfer Progress Reporting.
                - Adapt: MouseCursor Reactive Widgets.
                - Feat: Set/Get TextAreaWidget Placeholder.
                - Fix: CodeCompletionWidget Freaks Out In Desktop Mode.
                - Fix: Explorer Renaming Reliability.
                - Feat: Archive/Unarchive For ReStudioBackend.
                - Feat: Much More Reliable StandardUndoRedoPlugin.
                - Feat: File ExplorerDrag 'n Drop.
                - Fix: Gallery Tab Always Exists.
                - Feat: Implemented Multi +/- Filters.
                - Feat: ResourceBrowser Remembers Your Preference.
                - Feat: Overhauled Resources System.
                - Feat: WorldMap Player Data Integration.
                - Fix: Refactor Resource Indexing For SSH & ReStudio.
                - Feat: Improved SshBackend Resources Indexing.
                - Chore: Removed Redundant DebugLogs.
                - Feat: Manual Resource Installation Auto-Dep Download.

                ### ReScreen Changes:
                - Feat: LoadingAnimation No Longer Static.
                - Feat: Gif Decoding Optimizations.
                - Fix: TextRendererLwjgl `trimToWidget()` Appends "...".
                - Feat: Reactor & ReSync Icons, `server.png` Remake.
                - Feat: Unsaved Changes Access.
                - Fix: Popups Blocking Other Interactions.
                - Feat: Better SettingsScreen Category Loading Flow.
                - Fix: SidePanel Related Stuff.
                - Feat(HUGE): Abstracted Minecraft Assets & Rendering.
                - Feat: Popups Now Have Their Own Overlay.
                - Feat: Maybe CrossPlatform `openAssociated`.
                - Feat: Multi-Line Hints & Visual Upgrades To It.
                - Fix: Rare Unreliable SuperScreen Checking.
                - Fix: InfiniteScreen Scissors Logic.
                - Fix: Size Caching Causing Stale Sizes.
                - Feat: DropDown Single Negative Selection Display.
                - Feat: MouseCursor Highlighting Overhaul.
                - Feat: ContextMenuWidget Bounds Forcing.
                - Delete: TextAreaWidget.
                - Fix: DesktopMode `fileDrop` Targets Superscreen.
                - Feat: DropDownWIdget Multi-Select +/- Mode.
                - Fix: `MountableButtonWidget` Mounted Buttons Don't Render Hints.
                - Feat: ScreenWindow Dynamic Sizing.
                - Fix: SearchMode No Longer Effected By Button Count.
            """.trimIndent()
        )

        modrinthDepends.required.set(listOf("fabric-api"))
        modrinthDepends.incompatible.set(listOf("essential"))
    }
}
