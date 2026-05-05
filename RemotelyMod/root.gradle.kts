plugins {
    id("dev.deftu.gradle.multiversion-root")
}

val versionProjectPattern = Regex("""^\d+\.\d+(?:\.\d+)?(?:-(?:snapshot|pre|rc)-\d+)?-(?:fabric|neoforge|forge)$|^\d+\.\d+(?:-(?:snapshot|pre|rc)-\d+)?-(?:fabric|neoforge|forge)$""")

fun versionProjects(loader: String? = null) = subprojects
    .filter { versionProjectPattern.matches(it.name) }
    .filter { loader == null || it.name.endsWith("-$loader") }
    .sortedBy { it.name }

fun registerBuildAggregate(name: String, descriptionText: String, loader: String? = null) {
    tasks.register(name) {
        group = "build"
        description = descriptionText
        dependsOn(versionProjects(loader).map { "${it.path}:build" })
    }
}

fun registerPublishAggregate(name: String, descriptionText: String, taskName: String, loader: String? = null) {
    tasks.register(name) {
        group = "publishing"
        description = descriptionText
        dependsOn(versionProjects(loader).mapNotNull { project ->
            project.tasks.findByName(taskName)?.let { project.tasks.named(taskName) }
        })
    }
}

registerBuildAggregate("buildAllVersions", "Builds every enabled RemotelyMod version.")
registerBuildAggregate("buildAllFabric", "Builds every enabled Fabric RemotelyMod version.", "fabric")
registerBuildAggregate("buildAllNeoForge", "Builds every enabled NeoForge RemotelyMod version.", "neoforge")

gradle.projectsEvaluated {
    registerPublishAggregate("publishAllVersions", "Publishes every enabled RemotelyMod version.", "publishMod")
    registerPublishAggregate("publishAllFabric", "Publishes every enabled Fabric RemotelyMod version.", "publishMod", "fabric")
    registerPublishAggregate("publishAllNeoForge", "Publishes every enabled NeoForge RemotelyMod version.", "publishMod", "neoforge")
    registerPublishAggregate("publishAllVersionsToModrinth", "Publishes every enabled RemotelyMod version to Modrinth.", "publishModrinth")
    registerPublishAggregate("publishAllVersionsToCurseForge", "Publishes every enabled RemotelyMod version to CurseForge.", "publishCurseforge")
}

preprocess {
    strictExtraMappings.set(true)

    "1.21.11-fabric"(1_21_11, "srg") {
        "1.21.11-neoforge"(1_21_11, "srg") {
            "1.21.10-neoforge"(1_21_10, "srg") {
                "1.21.8-neoforge"(1_21_08, "srg") {
                    "1.21.6-neoforge"(1_21_06, "srg") {
                        "1.21.5-neoforge"(1_21_05, "srg") {
                            "1.21.4-neoforge"(1_21_04, "srg") {
                                "1.21.1-neoforge"(1_21_01, "srg") {
                                }
                            }
                        }
                    }
                }
            }
        }
        "1.21.10-fabric"(1_21_10, "srg") {
            "1.21.8-fabric"(1_21_08, "srg") {
                "1.21.6-fabric"(1_21_06, "srg") {
                    "1.21.5-fabric"(1_21_05, "srg") {
                        "1.21.4-fabric"(1_21_04, "srg") {
                            "1.21.1-fabric"(1_21_01, "srg") {
                                "1.20.1-fabric"(1_20_1, "srg") {
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
