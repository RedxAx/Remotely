plugins {
    id("dev.isxander.modstitch.base") version "0.5.12"
}

fun prop(name: String, consumer: (prop: String) -> Unit) {
    (findProperty(name) as? String?)
        ?.let(consumer)
}

val minecraft = property("deps.minecraft") as String

modstitch {
    minecraftVersion = minecraft

    // Alternatively use stonecutter.eval if you have a lot of versions to target.
    // https://stonecutter.kikugie.dev/stonecutter/guide/setup#checking-versions
    javaTarget = when (minecraft) {
        "1.20.1" -> 17
        "1.21.1", "1.21.2", "1.21.4", "1.21.5" -> 21
        else -> throw IllegalArgumentException("Please store the java version for ${property("deps.minecraft")} in build.gradle.kts!")
    }

    // If parchment doesn't exist for a version, yet you can safely
    // omit the "deps.parchment" property from your versioned gradle.properties
    parchment {
        prop("deps.parchment") { mappingsVersion = it }
    }

    // This metadata is used to fill out the information inside
    // the metadata files found in the templates folder.
    metadata {
        modId = "remotely"
        modName = "Remotely OS"
        modVersion = "2.0.0"
        modGroup = "redxax.oxy"
        modAuthor = "RedxAx"

        fun <K, V> MapProperty<K, V>.populate(block: MapProperty<K, V>.() -> Unit) {
            block()
        }

        replacementProperties.populate {
            // You can put any other replacement properties/metadata here that
            // modstitch doesn't initially support. Some examples below.
            put("mod_issue_tracker", "https://github.com/modunion/modstitch/issues")
        }
    }

    // Fabric Loom (Fabric)
    loom {
        // It's not recommended to store the Fabric Loader version in properties.
        // Make sure its up to date.
        fabricLoaderVersion = "0.16.3"

        // Configure loom like normal in this block.
        configureLoom {

        }
    }

    // ModDevGradle (NeoForge, Forge)
    moddevgradle {
        enable {
            prop("deps.forge") { forgeVersion = it }
            prop("deps.neoform") { neoFormVersion = it }
            prop("deps.neoforge") { neoForgeVersion = it }
            prop("deps.mcp") { mcpVersion = it }
        }

        // Configures client and server runs for MDG, it is not done by default
        defaultRuns()

        // This block configures the `neoforge` extension that MDG exposes by default,
        // you can configure MDG like normal from here
        configureNeoforge {
            runs.all {
                disableIdeRun()
            }
        }
    }

    mixin {
        // You do not need to specify mixins in any mods.json/toml file if this is set to
        // true, it will automatically be generated.
        addMixinsToModManifest = true

        configs.register("remotely")

        // Most of the time you won't ever need loader specific mixins.
        // If you do, simply make the mixin file and add it like so for the respective loader:
        // if (isLoom) configs.register("remotely-fabric")
        // if (isModDevGradleRegular) configs.register("remotely-neoforge")
        // if (isModDevGradleLegacy) configs.register("remotely-forge")
    }
}

// Stonecutter constants for mod loaders.
// See https://stonecutter.kikugie.dev/stonecutter/guide/comments#condition-constants
var constraint: String = name.split("-")[1]
stonecutter {
    consts(
        "fabric" to constraint.equals("fabric"),
        "neoforge" to constraint.equals("neoforge"),
        "forge" to constraint.equals("forge"),
        "vanilla" to constraint.equals("vanilla")
    )
}

// All dependencies should be specified through modstitch's proxy configuration.
// Wondering where the "repositories" block is? Go to "stonecutter.gradle.kts"
// If you want to create proxy configurations for more source sets, such as client source sets,
// use the modstitch.createProxyConfigurations(sourceSets["client"]) function.
val mineMarkVer = if (minecraft <= "1.20.6") "1.20.1" else if (minecraft <= "1.21.4") "1.21.1" else "1.21.5"
val mcefVer = if (minecraft == "1.21.5") "1.21.4" else minecraft

dependencies {
    modstitch.loom {
        val fapi = property("deps.fapi") as String
        modstitchModImplementation("net.fabricmc.fabric-api:fabric-api:$fapi")
    }
    modstitchImplementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    modstitchImplementation("com.jcraft:jsch:0.1.55")
    modstitchImplementation("com.vladsch.flexmark:flexmark:0.62.2")
    modstitchImplementation("org.jsoup:jsoup:1.15.3")
    modstitchImplementation("org.jline:jline:3.1.3")
    modstitchImplementation("org.fusesource.jansi:jansi:1.8")
    modstitchImplementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    modstitchModImplementation("dev.dediamondpro:minemark-minecraft-" + mineMarkVer + "-" + constraint + ":1.3.1")
    modstitchImplementation("dev.dediamondpro:minemark-core:1.3.1")

    modstitchJiJ("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    modstitchJiJ("com.jcraft:jsch:0.1.55")
    modstitchJiJ("com.vladsch.flexmark:flexmark:0.62.2")
    modstitchJiJ("org.jsoup:jsoup:1.15.3")
    modstitchJiJ("org.jline:jline:3.1.3")
    modstitchJiJ("org.fusesource.jansi:jansi:1.8")
    modstitchJiJ("com.fifesoft:rsyntaxtextarea:3.6.0")
    modstitchJiJ("dev.dediamondpro:minemark-core:1.3.1")
    modstitchJiJ("dev.dediamondpro:minemark-minecraft-" + mineMarkVer + "-" + constraint + ":1.3.1")

    modstitchModCompileOnly("com.cinemamod:mcef:2.1.6-$mcefVer")
    modstitchModImplementation("com.cinemamod:mcef-fabric:2.1.6-$mcefVer")
}

tasks.jar {
    from("src/main/resources")
}