plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.21.2-fabric"

stonecutter registerChiseled tasks.register("chiseledBuild", stonecutter.chiseled) {
    group = "project"
    ofTask("build")
}

allprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven {
            url = uri("https://maven.dediamondpro.dev/releases/")
            name = "DeDiamondPro"
            content {
                includeGroup("dev.dediamondpro")
            }
        }
        maven {
            url = uri("https://mcef-download.cinemamod.com/repositories/releases")
            name = "CinemaMod"
            content {
                excludeGroup("dev.dediamondpro")
            }
        }
    }
}