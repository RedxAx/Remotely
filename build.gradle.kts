import java.util.*

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("me.modmuss50.mod-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

val minecraft = stonecutter.current.version
val loader = loom.platform.get().name.lowercase()

version = "${mod.version}+$minecraft"
group = mod.group
base {
    archivesName.set("${mod.id}-$loader")
}

architectury.common(stonecutter.tree.branches.mapNotNull {
    if (stonecutter.current.project !in it) null
    else it.prop("loom.platform")
})
repositories {
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.terraformersmc.com/")
    maven("https://maven.nucleoid.xyz/")

    maven {
        name = "dediamondproReleases"
        url = uri("https://maven.dediamondpro.dev/releases")
        content {
            includeGroup("dev.dediamondpro")
        }
    }

    maven {
        url = uri("https://mcef-download.cinemamod.com/repositories/releases")
        content {
            excludeGroup("dev.dediamondpro")
        }
    }
}


val mineMarkVer = if (minecraft <= "1.20.6") if (loader == "neoforge") "1.20.4" else "1.20.1" else if (minecraft <= "1.21.4") "1.21.1" else "1.21.5"
val mcefVer = if (minecraft == "1.21.5") "1.21.4" else minecraft

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")

    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("com.vladsch.flexmark:flexmark:0.62.2")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("org.jline:jline:3.1.3")
    implementation("org.fusesource.jansi:jansi:1.8")
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    modImplementation("dev.dediamondpro:minemark-minecraft-" + mineMarkVer + "-" + loader + (if (loader == "neoforge" && minecraft == "1.20.4") ":1.2.3" else ":1.3.1"))
    implementation("dev.dediamondpro:minemark-core:1.3.1")
    modCompileOnly("com.cinemamod:mcef:2.1.6-$mcefVer")
    modImplementation("com.cinemamod:mcef-fabric:2.1.6-$mcefVer")

    shadow("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    shadow("com.jcraft:jsch:0.1.55")
    shadow("com.vladsch.flexmark:flexmark:0.62.2")
    shadow("org.jsoup:jsoup:1.15.3")
    shadow("org.jline:jline:3.1.3")
    shadow("org.fusesource.jansi:jansi:1.8")
    shadow("com.fifesoft:rsyntaxtextarea:3.6.0")
    shadow("dev.dediamondpro:minemark-core:1.3.1")
    shadow("dev.dediamondpro:minemark-minecraft-" + mineMarkVer + "-" + loader + (if (loader == "neoforge" && minecraft == "1.20.4") ":1.2.3" else ":1.3.1"))
    if (loader == "fabric") {
        modImplementation("net.fabricmc:fabric-loader:${mod.dep("fabric_loader")}")
        mappings("net.fabricmc:yarn:$minecraft+build.${mod.dep("yarn_build")}:v2")
        modImplementation("com.terraformersmc:modmenu:${mod.dep("modmenu_version")}")

        //some features (like automatic resource loading from non vanilla namespaces) work only with fabric API installed
        //for example translations from assets/modid/lang/en_us.json won't be working, same stuff with textures
        //but we keep runtime only to not accidentally depend on fabric's api, because it doesn't exist in neo/forge
        modRuntimeOnly("net.fabricmc.fabric-api:fabric-api:${mod.dep("fabric_version")}")

    }
    if (loader == "forge") {
        "forge"("net.minecraftforge:forge:${minecraft}-${mod.dep("forge_loader")}")
        mappings("net.fabricmc:yarn:$minecraft+build.${mod.dep("yarn_build")}:v2")

        "io.github.llamalad7:mixinextras-forge:${mod.dep("mixin_extras")}".let {
            implementation(it)
            include(it)
        }
    }
    if (loader == "neoforge") {
        "neoForge"("net.neoforged:neoforge:${mod.dep("neoforge_loader")}")
        mappings(loom.layered {
            mappings("net.fabricmc:yarn:$minecraft+build.${mod.dep("yarn_build")}:v2")
            mod.dep("neoforge_patch").takeUnless { it.startsWith('[') }?.let {
                mappings("dev.architectury:yarn-mappings-patch-neoforge:$it")
            }
        })
    }
}

loom {
    accessWidenerPath = rootProject.file("src/main/resources/remotely.accesswidener")

    decompilers {
        get("vineflower").apply { // Adds names to lambdas - useful for mixins
            options.put("mark-corresponding-synthetics", "1")
        }
    }
    if (loader == "forge") {
        forge.mixinConfigs(
            "remotely-common.mixins.json",
        )
    }
}


val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
publishMods {
    val modrinthToken = localProperties.getProperty("publish.modrinthToken", "")
    val curseforgeToken = localProperties.getProperty("publish.curseforgeToken", "")


    file = project.tasks.remapJar.get().archiveFile
    dryRun = modrinthToken == null || curseforgeToken == null

    displayName = "${mod.name} ${loader.replaceFirstChar { it.uppercase() }} ${property("mod.mc_title")}-${mod.version}"
    version = mod.version
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = BETA

    modLoaders.add(loader)

    val targets = property("mod.mc_targets").toString().split(' ')
    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = modrinthToken
        targets.forEach(minecraftVersions::add)
        if (loader == "fabric") {
            requires("fabric-api")
            optional("modmenu")
        }
    }

    curseforge {
        projectId = property("publish.curseforge").toString()
        accessToken = curseforgeToken.toString()
        targets.forEach(minecraftVersions::add)
        if (loader == "fabric") {
            requires("fabric-api")
            optional("modmenu")
        }
    }
}

java {
    withSourcesJar()
    val java = if (stonecutter.eval(minecraft, ">=1.20.5")) JavaVersion.VERSION_21 else JavaVersion.VERSION_17
    targetCompatibility = java
    sourceCompatibility = java
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier = "dev-shadow"
}

tasks.remapJar {
    injectAccessWidener = true
    input = tasks.shadowJar.get().archiveFile
    archiveClassifier = null
    dependsOn(tasks.shadowJar)
}

tasks.jar {
    archiveClassifier = "dev"
}

val buildAndCollect = tasks.register<Copy>("buildAndCollect") {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
    from(tasks.remapJar.get().archiveFile, tasks.remapSourcesJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.file("libs/${mod.version}/$loader"))
    dependsOn("build")
}

if (stonecutter.current.isActive) {
    rootProject.tasks.register("buildActive") {
        group = "project"
        dependsOn(buildAndCollect)
    }

    rootProject.tasks.register("runActive") {
        group = "project"
        dependsOn(tasks.named("runClient"))
    }
}

tasks.processResources {
    properties(
        listOf("fabric.mod.json"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to mod.prop("mc_dep_fabric")
    )
    properties(
        listOf("META-INF/mods.toml", "pack.mcmeta"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to mod.prop("mc_dep_forgelike")
    )
    properties(
        listOf("META-INF/neoforge.mods.toml", "pack.mcmeta"),
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "minecraft" to mod.prop("mc_dep_forgelike")
    )
}

tasks.build {
    group = "versioned"
    description = "Must run through 'chiseledBuild'"
}
