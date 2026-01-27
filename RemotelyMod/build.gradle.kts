import dev.deftu.gradle.utils.ModLoader
import dev.deftu.gradle.utils.version.MinecraftVersions
import dev.deftu.gradle.utils.includeOrShade
import net.fabricmc.loom.task.RemapJarTask
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
    useDevAuth("1.2.2")
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

        changelog.set(
            """
                ### Remotely Changes:
                - Fix: ESC Not Handled For MC.
                - Fix: Used ClipboardHandler For MC.
                - Fix: Incorrect Button Positioning In TitleScreen.
                - Chore: Small Changes.
                - Feat: Server Tab Renames For Non-LocalBackend Servers.
                - Feat: Customize Icon For Non-LocalBackend Servers
                - Fix: Filters Widget Aren't Sticking To Their Tabs.
                - Feat: Launch Anyway Buttons.
                - ReCode: Server Sessions Logic.
                - Fix: Opening Non-Local Servers Doesn't Select Already Opened Ones.
                - Feat: More UX Features.
                - Feat: Server Ordering.
                - Chore: Moved Development Settings To ReScreen.
                - Feat: Completely Rewrote PlayerManagement.
                - Fix: ServerTerminal States.
                - Fix: Edit Button Now Visible In ServerManager.
                - Feat: Full Config Support For ReStudio.Host.
                - Feat: Integrated Java Management Into Remotely.
                - Fix: Update All Popup Toggles Don't Work.
                - Fix: Resource Duplicate On Toggle.
                - Feat: Integrated Auto-Updater.
                - Feat: 100x Faster Resource Loading.
                - Fix: Used New WatcherServiceManager.
                - Feat: Smart Server Backup Manager.

                ### Rebase Changes:
                - Fix: TextAreaWidget Didn't Handle Copy Correctly.
                - Feat: FileExplorer Remembers Any Remote Host.
                - Fix: `.tmp` Appearing As Resources.
                - Fix: Inaccurate Update Checking.
                - Fix: Set-To-Parent Breaks The ResourceBrowserScreen.
                - Fix: Right-Clicking On Files Doesn't Show Context Menu.
                - Feat: Resize Gallery & Auth Screen Handling.
                - Feat: File Transfer Progress Reporting & More.
                - Feat(Editor): Numbered Lines & Line Highlight.
                - Feat: Server Rename Endpoint.
                - Feat: Edit Paths In Tabs.
                - Fix: Static Ports That Might Be Bound Already.
                - Fix: Proper Resource Cache Handling.
                - Feat: Proper Handling Of "latest".
                - Feat: Reliable `equal()` And `hashCode` Impl.
                - Feat: Server Info Feature.
                - Fix: CredentialsManager, I Hate Windows.
                - Fix: CredentialsManager, FR THIS TIME.
                - Fix: CredentialsManager Not Resetting Corrupted Values.
                - Visual: Overhauled Update Popup.
                - Feat: TerminalWidget Respect Obfuscation Option.
                - Fix: Some Memory Improvements.
                - Fix: More `server.jar` Checks.
                - Feat: Proper New Versions Suppot (YY.x.x)
                - Fix: Role Doesn't Get Saved.
                - Fix: Importing Theme Named "default" No Longer Override.
                - Fix: Searching Resources Have Duplicates.
                - Fix: Disable TextScissors In IconButton For Comment Widget..
                - Fix: Comments Doesn't Update When Posting One.
                - Feat: Server Versions Controlling & More.
                - Fix: Much More Reliable CredentialsManager.
                - Feat: Import Server Type/Version.
                - Fix: SshBackend Mistake.
                - Feat: Added Java 25 To Java Manager.
                - Feat: Java Management For Servers.
                - Fix: Reliable `server.jar` Detection.
                - Fix: Folders Being Counted As Resources.
                - Feat: Auto-Updater.
                - WIP: 100x Faster Resource Loading.
                - Fix: Feedback Flow And UX.
                - Fix: Used Universal `openBrowser()`.
                - Feat: Way Smarter JRE Management.
                - Feat: Significant Backups Improvements.
                - Fix: Modrinth Images Now Load Raw Image.
                - Fix: Authentication Doesn't Provide Project.
                - fix: FileExplorer Navigation Loop.
                - Fix: Terminal Init Flow.

                ### ReScreen Changes:
                - Feat: ClipboardHandler (Platform Agnostic).
                - Fix: GalleryContainer Doesn't React To Resizing.
                - Feat: ReScreen State `preserveStateOnDisplay` Option.
                - Feat: More ItemSelectorWidget Features.
                - Feat: New Smart ItemSelectorWidget.
                - Feat: Notification Handling For InfiniteScreen.
                - Feat: More API For TabsManager.
                - Fix: Double Notification Spawning.
                - Fix: AutoSetWidth Will Ignore Icons In `IconButton`.
                - Feat: Imported DevelopmentSettingsController.
                - Fix: Critical Memory Leaks.
                - Fix: DropDownWidget Visual Bugs.
                - Fix: Theme Switching Issues.
                - Fix: Text Can Overflow In IconButtons.
                - Fix: 2 Widgets Visible When Renaming Tabs.
                - Feat: ClipboardHandler (Platform Agnostic).
                - Fix: GalleryContainer Doesn't React To Resizing.
                - Feat: ReScreen State `preserveStateOnDisplay` Option.
                - Feat: More ItemSelectorWidget Features.
                - Feat: New Smart ItemSelectorWidget.
                - Feat: Notification Handling For InfiniteScreen.
                - Feat: More API For TabsManager.
                - Fix: Double Notification Spawning.
                - Fix: AutoSetWidth Will Ignore Icons In `IconButton`.
                - Feat: Imported DevelopmentSettingsController.
                - Fix: Critical Memory Leaks.
                - Fix: DropDownWidget Visual Bugs.
                - Fix: Theme Switching Issues.
                - Fix: Text Can Overflow In IconButtons.
                - Fix: 2 Widgets Visible When Renaming Tabs.
                - Fix: WatchServiceManager Improvements.
                - Feat: Containers Ability To Insert Widgets Smoothly.
                - Feat: New WatchServiceManager (Files).
                - Feat: BrowsingUtils.
                - Fix: Popups Now Use zLayer & Priority.
                - Feat: GalleryContainer Flexible Image Loading.
            """.trimIndent()
        )

        modrinthDepends.required.set(listOf("fabric-api", "fabric-language-kotlin"))
    }
}
