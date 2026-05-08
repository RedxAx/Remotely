package redxax.remotelymod.buildlogic

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.hypherionmc.modpublisher.plugin.ModPublisherGradleExtension
import net.neoforged.moddevgradle.dsl.ModModel
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.dsl.RunModel
import net.neoforged.moddevgradle.boot.ModDevPlugin
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.tasks.Jar as JvmJar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.jar.Manifest

class RemotelyModCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java")
        project.configureRepositories()
        project.configureJava()
        project.configureResources()
        project.configurePublishingGuard()
    }
}

class RemotelyModFabricLoomPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("dev.deftu.gradle.multiversion")
        project.pluginManager.apply("dev.deftu.gradle.tools")
        project.pluginManager.apply("dev.deftu.gradle.tools.resources")
        project.pluginManager.apply("dev.deftu.gradle.tools.bloom")
        project.pluginManager.apply("dev.deftu.gradle.tools.shadow")
        project.pluginManager.apply("dev.deftu.gradle.tools.minecraft.loom")
        project.configureSharedConfigurations()
        project.configureSharedDependencies()
        configureToolkitLoom(project, dropFabric = false)

        project.afterEvaluate {
            configureFabricApi(project, dropFabric = false)
            configureFabricJar(project, dropFabric = false)
        }
    }
}

class RemotelyModFabricDropPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.extraProperties["fabric.loom.disableObfuscation"] = "true"
        project.extensions.extraProperties["dgt.loom.mappings.use"] = "false"
        project.extensions.extraProperties["dgt.fabric.loader.version"] = "0.18.4"

        project.pluginManager.apply("dev.deftu.gradle.multiversion")
        project.pluginManager.apply("dev.deftu.gradle.tools")
        project.pluginManager.apply("dev.deftu.gradle.tools.resources")
        project.pluginManager.apply("dev.deftu.gradle.tools.bloom")
        project.pluginManager.apply("dev.deftu.gradle.tools.shadow")
        project.pluginManager.apply("dev.deftu.gradle.tools.minecraft.loom")
        project.configureSharedConfigurations()
        project.configureSharedDependencies()
        configureToolkitLoom(project, dropFabric = true)

        project.afterEvaluate {
            configureFabricApi(project, dropFabric = true)
            configureFabricJar(project, dropFabric = true)
        }
    }
}

class RemotelyModNeoForgeModDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(ModDevPlugin::class.java)
        project.configureNeoForgePreprocessing()
        project.configureSharedConfigurations()
        project.configureSharedDependencies()

        project.configureNeoForgeModDev()
    }
}

class RemotelyModForgeLegacyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("dev.deftu.gradle.multiversion")
        project.pluginManager.apply("dev.deftu.gradle.tools")
        project.pluginManager.apply("dev.deftu.gradle.tools.resources")
        project.pluginManager.apply("dev.deftu.gradle.tools.bloom")
        project.pluginManager.apply("dev.deftu.gradle.tools.shadow")
        project.pluginManager.apply("dev.deftu.gradle.tools.minecraft.loom")
        project.configureSharedConfigurations()
        project.configureSharedDependencies()
        configureToolkitLoom(project, dropFabric = false)

        project.afterEvaluate {}
    }
}

private val dropVersionPattern = Regex("""\d{2,}\..*""")

private fun Project.isDropVersion() = dropVersionPattern.matches(name.substringBeforeLast("-"))

private fun Project.loader() = name.substringAfterLast("-")

private fun Project.modId() = stringProperty("mod.id")

private fun Project.modVersion() = stringProperty("mod.version")

private fun Project.modName() = stringProperty("mod.name")

private fun Project.modGroup() = stringProperty("mod.group")

private fun Project.minecraftVersion() = name.substringBeforeLast("-")

private fun Project.stringProperty(name: String): String {
    return findProperty(name)?.toString()
        ?: rootProject.findProperty(name)?.toString()
        ?: error("$name has not been set.")
}

private fun Project.configureRepositories() {
    repositories.mavenCentral()
    repositories.gradlePluginPortal()
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://maven.neoforged.net/releases/") })
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://maven.fabricmc.net") })
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://maven.terraformersmc.com/") })
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://maven.nucleoid.xyz/") })
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") })
    repositories.maven(action<MavenArtifactRepository> { repo -> repo.url = uri("https://maven.firstdark.dev/releases") })
    repositories.ivy(action<IvyArtifactRepository> { repo ->
        repo.name = "remotelyBuildLibs"
        repo.url = rootProject.file("../build/libs").toURI()
        repo.patternLayout {
            artifact("[artifact].[ext]")
        }
        repo.metadataSources {
            artifact()
        }
    })
    repositories.flatDir {
        dirs(rootProject.file("libs"))
    }
}

private fun Project.configureJava() {
    val javaVersion = if (isDropVersion()) 25 else 21
    extensions.configure(JavaPluginExtension::class.java, action<JavaPluginExtension> { extension ->
        extension.toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
        extension.modularity.inferModulePath.set(false)
    })
    configurations.configureEach {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaVersion)
    }
    tasks.withType(JavaCompile::class.java).configureEach(action<JavaCompile> { task ->
        task.options.release.set(javaVersion)
        task.modularity.inferModulePath.set(false)
    })
    tasks.withType(JavaExec::class.java).configureEach(action<JavaExec> { task ->
        val toolchains = extensions.getByType(JavaToolchainService::class.java)
        val launcher = toolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(javaVersion))
        }
        task.setExecutable(launcher.get().executablePath.asFile.absolutePath)
    })
}

private fun Project.configureSharedConfigurations() {
    configurations.maybeCreate("bundledTransitives").apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        exclude(mapOf("group" to "com.mojang"))
        exclude(mapOf("group" to "net.fabricmc"))
        exclude(mapOf("group" to "net.minecraft"))
        exclude(mapOf("group" to "com.google.code.gson", "module" to "gson"))
        exclude(mapOf("group" to "com.google.guava", "module" to "guava"))
        exclude(mapOf("group" to "com.ibm.icu", "module" to "icu4j"))
        exclude(mapOf("group" to "com.googlecode.soundlibs", "module" to "jorbis"))
        exclude(mapOf("group" to "commons-codec", "module" to "commons-codec"))
        exclude(mapOf("group" to "commons-io", "module" to "commons-io"))
        exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna"))
        exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna-platform"))
        exclude(mapOf("group" to "org.apache.commons", "module" to "commons-compress"))
        exclude(mapOf("group" to "org.apache.commons", "module" to "commons-lang3"))
        exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
        exclude(mapOf("group" to "org.joml"))
        exclude(mapOf("group" to "org.jetbrains.jediterm"))
        exclude(mapOf("group" to "org.jetbrains.pty4j"))
        exclude(mapOf("group" to "org.lwjgl"))
        exclude(mapOf("group" to "commons-logging", "module" to "commons-logging"))
        exclude(mapOf("group" to "xml-apis", "module" to "xml-apis"))
    }
    configurations.maybeCreate("nestedRuntimeJars").apply {
        isCanBeResolved = true
        isCanBeConsumed = false
        exclude(mapOf("group" to "com.mojang"))
        exclude(mapOf("group" to "net.fabricmc"))
        exclude(mapOf("group" to "net.minecraft"))
        exclude(mapOf("group" to "com.google.code.gson", "module" to "gson"))
        exclude(mapOf("group" to "com.google.guava", "module" to "guava"))
        exclude(mapOf("group" to "com.ibm.icu", "module" to "icu4j"))
        exclude(mapOf("group" to "com.googlecode.soundlibs", "module" to "jorbis"))
        exclude(mapOf("group" to "commons-codec", "module" to "commons-codec"))
        exclude(mapOf("group" to "commons-io", "module" to "commons-io"))
        exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna"))
        exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna-platform"))
        exclude(mapOf("group" to "org.apache.commons", "module" to "commons-compress"))
        exclude(mapOf("group" to "org.apache.commons", "module" to "commons-lang3"))
        exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
        exclude(mapOf("group" to "org.joml"))
        exclude(mapOf("group" to "org.lwjgl"))
        exclude(mapOf("group" to "commons-logging", "module" to "commons-logging"))
        exclude(mapOf("group" to "xml-apis", "module" to "xml-apis"))
    }
}

private fun Project.configureSharedDependencies() {
    val remotelyAppBuild = gradle.includedBuild("RemotelyApp").task(":jar")
    val remotelyAppJar = files(rootProject.file("../build/libs/Remotely-App.jar")).builtBy(remotelyAppBuild)
    val isNeoForge = loader() == "neoforge"

    fun isPlatformProvided(dependency: String): Boolean {
        return dependency.startsWith("com.google.code.gson:gson:")
                || dependency.startsWith("com.google.guava:guava:")
                || dependency.startsWith("com.ibm.icu:icu4j:")
                || dependency.startsWith("commons-codec:commons-codec:")
                || dependency.startsWith("commons-io:commons-io:")
                || dependency.startsWith("net.java.dev.jna:jna:")
                || dependency.startsWith("net.java.dev.jna:jna-platform:")
                || dependency.startsWith("org.apache.commons:commons-compress:")
                || dependency.startsWith("org.apache.commons:commons-lang3:")
                || dependency.startsWith("org.slf4j:slf4j-api:")
                || dependency.startsWith("org.joml:joml:")
                || dependency.startsWith("org.lwjgl:")
    }

    fun bundled(dependency: String) {
        val implementationDependency = dependencies.create(dependency) as ExternalModuleDependency
        if (isNeoForge) {
            implementationDependency.excludeNeoForgeProvidedTransitives()
        }
        dependencies.add("implementation", implementationDependency)
        if (!isPlatformProvided(dependency)) {
            val bundledDependency = dependencies.create(dependency) as ExternalModuleDependency
            if (isNeoForge) {
                bundledDependency.excludeNeoForgeProvidedTransitives()
            }
            dependencies.add("bundledTransitives", bundledDependency)
            dependencies.add("nestedRuntimeJars", bundledDependency)
        }
    }

    fun bundledRuntime(dependency: String) {
        val runtimeDependency = dependencies.create(dependency) as ExternalModuleDependency
        if (isNeoForge) {
            runtimeDependency.excludeNeoForgeProvidedTransitives()
        }
        dependencies.add("runtimeOnly", runtimeDependency)
        if (!isPlatformProvided(dependency)) {
            val bundledDependency = dependencies.create(dependency) as ExternalModuleDependency
            if (isNeoForge) {
                bundledDependency.excludeNeoForgeProvidedTransitives()
            }
            dependencies.add("bundledTransitives", bundledDependency)
            dependencies.add("nestedRuntimeJars", bundledDependency)
        }
    }

    fun bundledFile(fileCollection: FileCollection, nestedDependency: ExternalModuleDependency? = null) {
        dependencies.add("implementation", fileCollection)
        dependencies.add("nestedRuntimeJars", fileCollection)
    }

    val remotelyAppNested = dependencies.create("dev.restudio:remotely-app:${modVersion()}") as ExternalModuleDependency
    remotelyAppNested.isTransitive = false
    remotelyAppNested.artifact(action<DependencyArtifact> { artifact ->
        artifact.name = "Remotely-App"
        artifact.type = "jar"
        artifact.extension = "jar"
    })

    bundledFile(remotelyAppJar, remotelyAppNested)
    bundled("dev.restudio:rescreen:1.0")
    bundled("dev.restudio:remodel:1.0.0")
    bundled("dev.restudio:rebase:1.0-SNAPSHOT")
    bundled("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    bundled("com.hierynomus:sshj:0.40.0")
    if (isNeoForge) {
        bundledRuntime("com.github.javakeyring:java-keyring:1.0.4")
    } else {
        bundled("com.github.javakeyring:java-keyring:1.0.4")
    }
    bundled("net.java.dev.jna:jna-platform:5.13.0")
    bundled("com.vladsch.flexmark:flexmark-all:0.64.8")
    bundled("org.apache.xmlgraphics:batik-transcoder:1.19")
    bundled("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    bundled("org.java-websocket:Java-WebSocket:1.5.7")
    bundled("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    bundled("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")

    tasks.matching { it.name == "processIncludeJars" }.configureEach {
        dependsOn(remotelyAppBuild)
    }
}

private fun ExternalModuleDependency.excludeNeoForgeProvidedTransitives() {
    exclude(mapOf("group" to "com.google.code.gson", "module" to "gson"))
    exclude(mapOf("group" to "com.google.guava", "module" to "guava"))
    exclude(mapOf("group" to "com.ibm.icu", "module" to "icu4j"))
    exclude(mapOf("group" to "commons-codec", "module" to "commons-codec"))
    exclude(mapOf("group" to "commons-io", "module" to "commons-io"))
    exclude(mapOf("group" to "commons-logging", "module" to "commons-logging"))
    exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna"))
    exclude(mapOf("group" to "net.java.dev.jna", "module" to "jna-platform"))
    exclude(mapOf("group" to "org.apache.commons", "module" to "commons-compress"))
    exclude(mapOf("group" to "org.apache.commons", "module" to "commons-lang3"))
    exclude(mapOf("group" to "org.slf4j", "module" to "slf4j-api"))
    exclude(mapOf("group" to "org.joml"))
    exclude(mapOf("group" to "org.lwjgl"))
}

private fun Project.configureResources() {
    val fabricLoaderVersion = findProperty("dgt.fabric.loader.version")?.toString()
        ?: findProperty("fabric.loader.version")?.toString()
        ?: "0.17.2"
    val values = mapOf(
        "mod_id" to modId(),
        "mod_version" to modVersion(),
        "mod_name" to modName(),
        "mod_description" to (findProperty("mod.description")?.toString() ?: "Minecraft IDE"),
        "fabric_mc_version" to minecraftVersion(),
        "minor_mc_version" to minecraftVersion(),
        "fabric_loader_version" to fabricLoaderVersion
    )
    tasks.withType(ProcessResources::class.java).configureEach(action<ProcessResources> { task ->
        values.forEach { (key, value) -> task.inputs.property(key, value) }
        task.filesMatching(listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta"), action<FileCopyDetails> { details ->
            details.expand(values)
        })
    })
}

private fun Project.configurePublishingGuard() {
    val requestedTasks = gradle.startParameter.taskNames.map { it.lowercase() }
    val publishingRequested = requestedTasks.any {
        val taskName = it.lowercase()
        taskName.contains("publish") || taskName.contains("modrinth") || taskName.contains("curse") || taskName.contains("github") || taskName.contains("nightbloom")
    }
    if (!publishingRequested) {
        return
    }
    val publishingAllowed = rootProject.findProperty("remotely.allowPublish")?.toString()?.toBooleanStrictOrNull()
        ?: findProperty("remotely.allowPublish")?.toString()?.toBooleanStrictOrNull()
        ?: false
    if (!publishingAllowed) {
        throw GradleException("Publishing is disabled. Re-run with -Premotely.allowPublish=true to enable publishing tasks.")
    }
    pluginManager.apply("com.hypherionmc.modutils.modpublisher")
    pluginManager.withPlugin("com.hypherionmc.modutils.modpublisher") {
        extensions.configure(ModPublisherGradleExtension::class.java, action<ModPublisherGradleExtension> { publisher ->
            val properties = publishingProperties()
            val modrinthToken = properties.getProperty("MODRINTH_TOKEN", System.getenv("MODRINTH_TOKEN"))
            val curseForgeToken = properties.getProperty("CURSEFORGE_TOKEN", System.getenv("CURSEFORGE_TOKEN"))
            val needsModrinth = requestedTasks.any { it.contains("modrinth") || it == "publishmod" || it.endsWith(":publishmod") }
            val needsCurseForge = requestedTasks.any { it.contains("curse") || it == "publishmod" || it.endsWith(":publishmod") }
            if (needsModrinth && modrinthToken.isNullOrBlank()) {
                throw GradleException("Modrinth publishing was requested but MODRINTH_TOKEN is not set.")
            }
            if (needsCurseForge && curseForgeToken.isNullOrBlank()) {
                throw GradleException("CurseForge publishing was requested but CURSEFORGE_TOKEN is not set.")
            }
            publisher.apiKeys(action { keys ->
                if (!modrinthToken.isNullOrBlank()) {
                    keys.modrinth(modrinthToken)
                }
                if (!curseForgeToken.isNullOrBlank()) {
                    keys.curseforge(curseForgeToken)
                }
            })
            publisher.curseID.set("1224352")
            publisher.modrinthID.set("remotely")
            publisher.projectVersion.set(modVersion())
            publisher.displayName.set("Remotely ${modVersion()} (${loaderDisplayName()} ${minecraftVersion()})")
            publisher.gameVersions.set(publishingGameVersions())
            publisher.loaders.set(listOf(loader()))
            publisher.versionType.set(findProperty("remotely.publish.versionType")?.toString() ?: "release")
            publisher.artifact.set(publishingArtifact())
            publisher.changelog.set(findProperty("remotely.publish.changelog")?.toString() ?: "Remotely ${modVersion()} for ${minecraftVersion()}.")
            publisher.modrinthDepends(action { dependencies ->
                if (loader() == "fabric") {
                    dependencies.required("fabric-api")
                }
                dependencies.incompatible("essential")
            })
        })
    }
}

private fun Project.publishingProperties(): Properties {
    return Properties().apply {
        val envFile = rootProject.file("env.properties")
        if (envFile.exists()) {
            envFile.inputStream().use(::load)
        }
    }
}

private fun Project.publishingArtifact() = tasks.named(publishingArtifactTaskName(), AbstractArchiveTask::class.java)
    .flatMap { task -> task.archiveFile }

private fun Project.publishingArtifactTaskName(): String {
    return when {
        loader() == "fabric" && isDropVersion() -> "jar"
        loader() == "neoforge" -> "jar"
        else -> "remapJar"
    }
}

private fun Project.loaderDisplayName(): String {
    return when (loader()) {
        "fabric" -> "Fabric"
        "neoforge" -> "NeoForge"
        "forge" -> "Forge"
        else -> loader()
    }
}

private fun Project.publishingGameVersions(): List<String> {
    return when (minecraftVersion()) {
        "26.1" -> listOf("26.1")
        "26.1-pre-1" -> listOf("26.1", "26.1.1", "26w14a")
        "1.21.11" -> listOf("1.21.11")
        "1.21.10" -> listOf("1.21.10", "1.21.9")
        "1.21.8" -> listOf("1.21.8", "1.21.7", "1.21.6")
        "1.21.6" -> listOf("1.21.8", "1.21.7", "1.21.6")
        "1.21.5" -> listOf("1.21.5")
        "1.21.4" -> listOf("1.21.4", "1.21.3", "1.21.2")
        "1.21.1" -> listOf("1.21.1", "1.21")
        "1.20.1" -> listOf("1.20.1", "1.20")
        else -> listOf(minecraftVersion())
    }
}

private fun configureToolkitLoom(project: Project, dropFabric: Boolean) {
    val helper = project.extensions.findByName("toolkitLoomHelper") ?: return
        if (!dropFabric && project.loader() != "neoforge") {
            invoke(helper, "useDevAuth", "1.2.2")
            invoke(helper, "useMixinExtras", "0.5.0")
            invoke(helper, "useMixinRefMap", project.modId())
        }
        if (project.loader() == "forge") {
            invoke(helper, "useForgeMixin", project.modId())
        }
}

private fun configureFabricApi(project: Project, dropFabric: Boolean) {
    val fabricApiVersion = project.findProperty("dgt.fabric.api.version")?.toString()
        ?: project.findProperty("fabric.api.version")?.toString()
        ?: if (dropFabric) null else project.findProperty("fabric_api_version")?.toString()
    if (fabricApiVersion != null) {
        val dependency = "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion"
        project.dependencies.add(if (dropFabric) "implementation" else "modImplementation", dependency)
    }
}

private fun configureFabricJar(project: Project, dropFabric: Boolean) {
    val taskName = if (dropFabric) "jar" else "remapJar"
    project.tasks.named(taskName, AbstractArchiveTask::class.java).configure(action<AbstractArchiveTask> { task ->
        task.doLast {
            val runtimeJars = project.configurations.getByName("nestedRuntimeJars").files
            injectNestedJars(task.archiveFile.get().asFile, "fabric", project.modVersion(), runtimeJars)
        }
    })
}

private fun Project.configureNeoForgeModDev() {
    val neoForgeVersion = findProperty("dgt.neoforge.version")?.toString()
        ?: findProperty("neoforge.version")?.toString()
        ?: error("dgt.neoforge.version has not been set for $name")
    val embeddedRuntimeJars = listOf(
        gradle.includedBuild("RemotelyApp").task(":jar") to rootProject.file("../build/libs/Remotely-App.jar"),
        gradle.includedBuild("ReScreen").task(":jar") to rootProject.file("../../ReScreen/build/libs/ReScreen-1.0.jar"),
        gradle.includedBuild("Remodel").task(":jar") to rootProject.file("../../Remodel/build/libs/Remodel-1.0.0.jar"),
        gradle.includedBuild("Rebase").task(":jar") to rootProject.file("../../Rebase/build/libs/Rebase-1.0-SNAPSHOT.jar")
    )
    val mergeNeoForgeEmbeddedRuntime = tasks.register("mergeNeoForgeEmbeddedRuntime", Copy::class.java, action<Copy> { task ->
        embeddedRuntimeJars.forEach { (buildTask, jarFile) ->
            task.dependsOn(buildTask)
            task.from({ zipTree(jarFile) })
        }
        task.dependsOn(tasks.named("compileJava"))
        task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        task.into(layout.buildDirectory.dir("classes/java/main"))
        task.exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    })

    extensions.configure(NeoForgeExtension::class.java, action<NeoForgeExtension> { extension ->
        extension.setVersion(neoForgeVersion)
        extension.runs(action { runs ->
            runs.create("client", action<RunModel> { run -> run.client() })
            runs.create("server", action<RunModel> { run -> run.server() })
        })
        extension.mods(action { mods ->
            mods.create(modId(), action<ModModel> { mod ->
                val sourceSets = extensions.getByType(SourceSetContainer::class.java)
                mod.sourceSet(sourceSets.getByName("main"))
            })
        })
    })

    tasks.withType(JavaExec::class.java).configureEach(action<JavaExec> { task ->
        task.dependsOn(mergeNeoForgeEmbeddedRuntime)
    })
    tasks.withType(JvmJar::class.java).configureEach(action<JvmJar> { task ->
        task.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        task.archiveBaseName.set(modName())
        task.archiveVersion.set("${modVersion()}+${minecraftVersion()}-${loader()}")
        task.archiveClassifier.set("")
        task.doFirst {
            project.configurations.getByName("nestedRuntimeJars").files.forEach { file ->
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .filter { !it.isDirectory }
                        .map { it.name }
                        .filter { !it.startsWith("META-INF/") }
                        .forEach { entryName -> task.exclude(entryName) }
                }
            }
        }
        task.doLast {
            injectNestedJars(task.archiveFile.get().asFile, "neoforge", project.modVersion(), project.configurations.getByName("nestedRuntimeJars").files)
        }
    })
}

private fun Project.configureNeoForgePreprocessing() {
    val generatedJava = layout.buildDirectory.dir("generated/remotelymod/neoforge/main/java")
    val generatedResources = layout.buildDirectory.dir("generated/remotelymod/neoforge/main/resources")
    val preprocessTask = tasks.register("preprocessNeoForgeSources", action<Task> { task ->
        task.inputs.dir(rootProject.file("src/main/java"))
        task.inputs.dir(rootProject.file("src/main/resources"))
        task.outputs.dir(generatedJava)
        task.outputs.dir(generatedResources)
        task.doLast {
            val javaOutput = generatedJava.get().asFile
            val resourcesOutput = generatedResources.get().asFile
            javaOutput.deleteRecursively()
            resourcesOutput.deleteRecursively()
            preprocessTree(rootProject.file("src/main/java"), javaOutput, minecraftVersion(), emptySet(), templateValues())
            preprocessTree(
                rootProject.file("src/main/resources"),
                resourcesOutput,
                minecraftVersion(),
                setOf("fabric.mod.json", "mcmod.info", "remotely.accesswidener", "META-INF/mods.toml"),
                templateValues()
            )
        }
    })
    extensions.configure(SourceSetContainer::class.java, action<SourceSetContainer> { sourceSets ->
        val main = sourceSets.getByName("main")
        main.java.srcDir(generatedJava)
        main.resources.srcDir(generatedResources)
    })
    tasks.withType(JavaCompile::class.java).configureEach(action<JavaCompile> { task ->
        task.dependsOn(preprocessTask)
    })
    tasks.withType(ProcessResources::class.java).configureEach(action<ProcessResources> { task ->
        task.dependsOn(preprocessTask)
    })
}

private data class PreprocessBranch(val parentActive: Boolean, val active: Boolean, val matched: Boolean)

private fun Project.templateValues(): Map<String, String> {
    return mapOf(
        "@MOD_ID@" to modId(),
        "@MOD_NAME@" to modName(),
        "@MOD_VERSION@" to modVersion()
    )
}

private fun preprocessTree(input: File, output: File, minecraftVersion: String, excludedPaths: Set<String>, replacements: Map<String, String>) {
    if (!input.exists()) {
        return
    }
    input.walkTopDown()
        .filter { it.isFile }
        .forEach { file ->
            val relativePath = file.relativeTo(input).invariantSeparatorsPath
            if (excludedPaths.contains(relativePath)) {
                return@forEach
            }
            val target = output.resolve(relativePath)
            target.parentFile.mkdirs()
            if (file.extension.equals("png", ignoreCase = true)) {
                file.copyTo(target, overwrite = true)
            } else {
                val text = replacements.entries.fold(preprocessText(file.readText(), minecraftVersion)) { value, replacement ->
                    value.replace(replacement.key, replacement.value)
                }
                target.writeText(text)
            }
        }
}

private fun preprocessText(text: String, minecraftVersion: String): String {
    val branches = mutableListOf<PreprocessBranch>()
    val output = mutableListOf<String>()
    fun active() = branches.all { it.active }
    text.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("//#if ") -> {
                val parentActive = active()
                val condition = evaluatePreprocessCondition(trimmed.removePrefix("//#if ").trim(), minecraftVersion)
                branches.add(PreprocessBranch(parentActive, parentActive && condition, condition))
            }
            trimmed.startsWith("//#elseif ") -> {
                val current = branches.removeLast()
                val condition = evaluatePreprocessCondition(trimmed.removePrefix("//#elseif ").trim(), minecraftVersion)
                branches.add(PreprocessBranch(current.parentActive, current.parentActive && !current.matched && condition, current.matched || condition))
            }
            trimmed.startsWith("//#else") -> {
                val current = branches.removeLast()
                branches.add(PreprocessBranch(current.parentActive, current.parentActive && !current.matched, true))
            }
            trimmed.startsWith("//#endif") -> {
                branches.removeLast()
            }
            active() -> {
                val markerIndex = line.indexOf("//$$")
                if (markerIndex >= 0) {
                    val uncommented = line.substring(markerIndex + 4).removePrefix(" ")
                    output.add(line.substring(0, markerIndex) + uncommented)
                } else {
                    output.add(line)
                }
            }
        }
    }
    return output.joinToString(System.lineSeparator())
}

private fun evaluatePreprocessCondition(condition: String, minecraftVersion: String): Boolean {
    return condition.split("||").any { orPart ->
        orPart.split("&&").all { andPart ->
            evaluatePreprocessTerm(andPart.trim(), minecraftVersion)
        }
    }
}

private fun evaluatePreprocessTerm(term: String, minecraftVersion: String): Boolean {
    return when (term) {
        "FABRIC" -> false
        "FORGE" -> false
        "NEOFORGE" -> true
        "FORGE-LIKE" -> true
        else -> evaluateMinecraftTerm(term, minecraftVersion)
    }
}

private fun evaluateMinecraftTerm(term: String, minecraftVersion: String): Boolean {
    val parts = term.split(Regex("""\s+"""))
    if (parts.size != 3 || parts[0] != "MC") {
        error("Unsupported preprocess condition: $term")
    }
    val comparison = compareMinecraftVersions(minecraftVersion, parts[2])
    return when (parts[1]) {
        ">=" -> comparison >= 0
        ">" -> comparison > 0
        "<=" -> comparison <= 0
        "<" -> comparison < 0
        "==" -> comparison == 0
        "!=" -> comparison != 0
        else -> error("Unsupported preprocess operator: ${parts[1]}")
    }
}

private fun compareMinecraftVersions(left: String, right: String): Int {
    val leftParts = minecraftVersionParts(left)
    val rightParts = minecraftVersionParts(right)
    val max = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until max) {
        val leftValue = leftParts.getOrElse(index) { 0 }
        val rightValue = rightParts.getOrElse(index) { 0 }
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

private fun minecraftVersionParts(version: String): List<Int> {
    return version.substringBefore("-")
        .split(".")
        .map { it.toIntOrNull() ?: 0 }
}

private fun injectNestedJars(archive: File, loader: String, modVersion: String, nestedJars: Set<File>) {
    val jars = nestedJars
        .filter { it.isFile && it.extension == "jar" }
        .distinctBy { it.name }
        .sortedBy { it.name }
    if (jars.isEmpty()) {
        return
    }
    val nestedRoot = if (loader == "neoforge") "META-INF/jarjar" else "META-INF/jars"
    val temp = archive.resolveSibling("${archive.name}.tmp")
    val written = linkedSetOf<String>()
    val outerPackages = linkedSetOf<String>()
    val fabricOuterJars = if (loader == "fabric") jars.filter(::isReStudioNestedJar) else emptyList()
    val injectedJars = if (loader == "fabric") jars.filterNot(::isReStudioNestedJar) else jars
    ZipInputStream(archive.inputStream().buffered()).use { input ->
        ZipOutputStream(temp.outputStream().buffered()).use { output ->
            generateSequence { input.nextEntry }.forEach { entry ->
                val name = entry.name
                if (name.startsWith("$nestedRoot/") || name == "META-INF/jarjar/metadata.json" || isRemotelyMonoAsset(name)) {
                    input.closeEntry()
                    return@forEach
                }
                classPackage(name)?.let { outerPackages.add(it) }
                val bytes = input.readBytes()
                val nextBytes = if (loader == "fabric" && name == "fabric.mod.json") {
                    injectFabricJarMetadata(bytes.toString(Charsets.UTF_8), injectedJars).toByteArray(Charsets.UTF_8)
                } else {
                    bytes
                }
                writeZipEntry(output, entry, name, nextBytes, written)
                input.closeEntry()
            }
            if (loader == "fabric") {
                fabricOuterJars.forEach { jar ->
                    writeFabricOuterJarEntries(output, jar, written)
                }
            }
            if (loader == "neoforge") {
                injectedJars.filter(::isReStudioNestedJar).forEach { jar ->
                    writeNeoForgeCoalescedClasses(output, jar, outerPackages, written)
                }
            }
            writeOuterRemotelyMonoAssets(output, jars, written)
            injectedJars.forEachIndexed { index, jar ->
                val bytes = when (loader) {
                    "fabric" -> fabricNestedJarBytes(jar, index, modVersion)
                    "neoforge" -> neoForgeNestedJarBytes(jar, outerPackages)
                    else -> jar.readBytes()
                }
                writeZipEntry(output, ZipEntry("$nestedRoot/${jar.name}"), "$nestedRoot/${jar.name}", bytes, written)
            }
            if (loader == "neoforge") {
                writeZipEntry(output, ZipEntry("META-INF/jarjar/metadata.json"), "META-INF/jarjar/metadata.json", neoForgeJarJarMetadata(injectedJars, modVersion).toByteArray(Charsets.UTF_8), written)
            }
        }
    }
    archive.delete()
    temp.renameTo(archive)
}

private fun writeFabricOuterJarEntries(output: ZipOutputStream, jar: File, written: MutableSet<String>) {
    ZipInputStream(jar.inputStream().buffered()).use { input ->
        generateSequence { input.nextEntry }.forEach { entry ->
            if (entry.isDirectory || entry.name == "fabric.mod.json" || entry.name == "META-INF/MANIFEST.MF" || isSignatureEntry(entry.name) || shouldDropNestedJarEntry(jar, entry.name)) {
                input.closeEntry()
                return@forEach
            }
            val bytes = input.readBytes()
            writeZipEntry(output, entry, entry.name, bytes, written)
            input.closeEntry()
        }
    }
}

private fun writeOuterRemotelyMonoAssets(output: ZipOutputStream, jars: List<File>, written: MutableSet<String>) {
    val remotelyApp = jars.firstOrNull { it.name == "Remotely-App.jar" } ?: return
    ZipInputStream(remotelyApp.inputStream().buffered()).use { input ->
        generateSequence { input.nextEntry }.forEach { entry ->
            if (!entry.isDirectory && isRemotelyMonoAsset(entry.name)) {
                val bytes = input.readBytes()
                writeZipEntry(output, entry, entry.name, bytes, written)
            }
            input.closeEntry()
        }
    }
}

private fun writeZipEntry(output: ZipOutputStream, source: ZipEntry, name: String, bytes: ByteArray, written: MutableSet<String>) {
    if (!written.add(name)) {
        return
    }
    val entry = ZipEntry(name)
    entry.time = source.time
    output.putNextEntry(entry)
    output.write(bytes)
    output.closeEntry()
}

private fun fabricNestedJarBytes(jar: File, index: Int, modVersion: String): ByteArray {
    val outputBytes = ByteArrayOutputStream()
    val written = linkedSetOf<String>()
    val hasFabricMetadata = ZipFile(jar).use { zip -> zip.getEntry("fabric.mod.json") != null }
    ZipInputStream(jar.inputStream().buffered()).use { input ->
        ZipOutputStream(outputBytes.buffered()).use { output ->
            generateSequence { input.nextEntry }.forEach { entry ->
                if (isSignatureEntry(entry.name) || shouldDropNestedJarEntry(jar, entry.name)) {
                    input.closeEntry()
                    return@forEach
                }
                val bytes = input.readBytes()
                writeZipEntry(output, entry, entry.name, bytes, written)
                input.closeEntry()
            }
            if (!hasFabricMetadata) {
                val metadata = fabricNestedJarMetadata(jar, index, modVersion).toByteArray(Charsets.UTF_8)
                writeZipEntry(output, ZipEntry("fabric.mod.json"), "fabric.mod.json", metadata, written)
            }
        }
    }
    return outputBytes.toByteArray()
}

private fun neoForgeNestedJarBytes(jar: File, outerPackages: Set<String>): ByteArray {
    val outputBytes = ByteArrayOutputStream()
    val written = linkedSetOf<String>()
    val manifest = ZipFile(jar).use { zip ->
        zip.getEntry("META-INF/MANIFEST.MF")?.let { entry ->
            Manifest(zip.getInputStream(entry))
        } ?: Manifest()
    }
    manifest.mainAttributes.putValue("Manifest-Version", manifest.mainAttributes.getValue("Manifest-Version") ?: "1.0")
    manifest.mainAttributes.putValue("FMLModType", if (isReStudioNestedJar(jar)) "GAMELIBRARY" else "LIBRARY")
    ZipInputStream(jar.inputStream().buffered()).use { input ->
        ZipOutputStream(outputBytes.buffered()).use { output ->
            generateSequence { input.nextEntry }.forEach { entry ->
                if (entry.name == "META-INF/MANIFEST.MF" || isSignatureEntry(entry.name) || shouldDropNestedJarEntry(jar, entry.name) || shouldCoalesceNeoForgeClass(jar, entry.name, outerPackages)) {
                    input.closeEntry()
                    return@forEach
                }
                val bytes = input.readBytes()
                writeZipEntry(output, entry, entry.name, bytes, written)
                input.closeEntry()
            }
            val manifestBytes = ByteArrayOutputStream()
            manifest.write(manifestBytes)
            writeZipEntry(output, ZipEntry("META-INF/MANIFEST.MF"), "META-INF/MANIFEST.MF", manifestBytes.toByteArray(), written)
        }
    }
    return outputBytes.toByteArray()
}

private fun writeNeoForgeCoalescedClasses(output: ZipOutputStream, jar: File, outerPackages: Set<String>, written: MutableSet<String>) {
    ZipInputStream(jar.inputStream().buffered()).use { input ->
        generateSequence { input.nextEntry }.forEach { entry ->
            if (!shouldCoalesceNeoForgeClass(jar, entry.name, outerPackages)) {
                input.closeEntry()
                return@forEach
            }
            val bytes = input.readBytes()
            writeZipEntry(output, entry, entry.name, bytes, written)
            input.closeEntry()
        }
    }
}

private fun shouldCoalesceNeoForgeClass(jar: File, name: String, outerPackages: Set<String>): Boolean {
    if (!isReStudioNestedJar(jar)) {
        return false
    }
    val classPackage = classPackage(name) ?: return false
    return classPackage in outerPackages
}

private fun shouldDropNestedJarEntry(jar: File, name: String): Boolean {
    if (jar.name == "ReScreen-1.0.jar" && isReScreenConflictingResource(name)) {
        return true
    }
    return jar.name == "Remotely-App.jar" && isRemotelyMonoAsset(name)
}

private fun isReScreenConflictingResource(name: String): Boolean {
    return name.startsWith("assets/minecraft/")
            || name.startsWith("assets/twemoji/")
            || name.startsWith("assets/emoji_categories/")
            || name.startsWith("assets/emoji_remappings/")
            || name.startsWith("assets/emoji_shortcodes/")
            || name.startsWith("assets/emoji_shortcodes_reverse/")
}

private fun isRemotelyMonoAsset(name: String): Boolean {
    return name == "assets/remotely/font/mono.json"
            || name == "assets/remotely/font/"
            || name == "assets/remotely/font/include/"
            || name == "assets/remotely/textures/font/"
            || name == "assets/remotely/font/include/mono.json"
            || name == "assets/remotely/font/include/space.json"
            || (name.startsWith("assets/remotely/textures/font/") && name.endsWith("_mono.png"))
}

private fun classPackage(name: String): String? {
    if (!name.endsWith(".class") || !name.contains("/")) {
        return null
    }
    return name.substringBeforeLast("/")
}

private fun isReStudioNestedJar(jar: File): Boolean {
    return jar.name in setOf("Remotely-App.jar", "ReScreen-1.0.jar", "Remodel-1.0.0.jar", "Rebase-1.0-SNAPSHOT.jar")
}

private fun isSignatureEntry(name: String): Boolean {
    val upper = name.uppercase()
    return upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA"))
}

private fun fabricNestedJarMetadata(jar: File, index: Int, modVersion: String): String {
    val coordinate = jarCoordinate(jar, modVersion)
    val id = "remotely_${coordinate.artifact.replace(Regex("""[^a-z0-9_]"""), "_")}_$index"
    val name = coordinate.artifact.split("-", "_", ".")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    return """
{
  "schemaVersion": 1,
  "id": "$id",
  "version": "${coordinate.version}",
  "name": "$name",
  "environment": "*"
}
""".trimStart()
}

private fun injectFabricJarMetadata(json: String, jars: List<File>): String {
    val withoutJars = Regex("""(?s),?\s*"jars"\s*:\s*\[[^\]]*]\s*""").replace(json) { match ->
        if (match.value.trimStart().startsWith(",")) "" else ""
    }
    val prefix = withoutJars.substringBeforeLast("}").trimEnd().trimEnd(',')
    val entries = jars.joinToString(",\n") { jar ->
        """    { "file": "META-INF/jars/${jar.name}" }"""
    }
    return "$prefix,\n  \"jars\": [\n$entries\n  ]\n}\n"
}

private fun neoForgeJarJarMetadata(jars: List<File>, modVersion: String): String {
    val entries = jars.joinToString(",\n") { jar ->
        val coordinate = jarCoordinate(jar, modVersion)
        """
    {
      "identifier": {
        "group": "${coordinate.group}",
        "artifact": "${coordinate.artifact}"
      },
      "version": {
        "range": "[${coordinate.version},)",
        "artifactVersion": "${coordinate.version}"
      },
      "path": "META-INF/jarjar/${jar.name}",
      "isObfuscated": false
    }""".trimEnd()
    }
    return "{\n  \"jars\": [\n$entries\n  ]\n}\n"
}

private data class NestedJarCoordinate(val group: String, val artifact: String, val version: String)

private fun jarCoordinate(jar: File, modVersion: String): NestedJarCoordinate {
    return when (jar.name) {
        "Remotely-App.jar" -> NestedJarCoordinate("dev.restudio", "remotely-app", modVersion)
        "ReScreen-1.0.jar" -> NestedJarCoordinate("dev.restudio", "rescreen", "1.0")
        "Remodel-1.0.0.jar" -> NestedJarCoordinate("dev.restudio", "remodel", "1.0.0")
        "Rebase-1.0-SNAPSHOT.jar" -> NestedJarCoordinate("dev.restudio", "rebase", "1.0-SNAPSHOT")
        else -> {
            val name = jar.nameWithoutExtension
            val split = Regex("""^(.+)-([0-9][A-Za-z0-9_.+-]*)$""").matchEntire(name)
            val artifact = (split?.groupValues?.get(1) ?: name).lowercase().replace(Regex("""[^a-z0-9_.-]"""), ".")
            val version = split?.groupValues?.get(2) ?: "1.0.0"
            NestedJarCoordinate("remotely.embedded", artifact, version)
        }
    }
}

private fun <T : Any> action(block: (T) -> Unit): Action<T> {
    return object : Action<T> {
        override fun execute(t: T) {
            block(t)
        }
    }
}

private fun invoke(target: Any, method: String, vararg args: Any) {
    val found = target.javaClass.methods.firstOrNull { it.name == method && it.parameterCount == args.size }
    if (found != null) {
        found.invoke(target, *args)
    }
}
