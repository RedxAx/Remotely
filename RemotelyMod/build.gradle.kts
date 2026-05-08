plugins {
    id("remotelymod.common")
}

val projectLoader = name.substringAfterLast("-")
val isDropProject = Regex("""\d{2,}\..*""").matches(name.substringBeforeLast("-"))

when (projectLoader) {
    "fabric" -> plugins.apply(if (isDropProject) "remotelymod.fabric-drop" else "remotelymod.fabric-loom")
    "neoforge" -> plugins.apply("remotelymod.neoforge-moddev")
    "forge" -> plugins.apply("remotelymod.forge-legacy")
    else -> error("Unsupported RemotelyMod loader project: $name")
}
