rootProject.name = "Remotely"

apply(from = file("../gradle/restudio-workspace.settings.gradle"))

if (extra["reStudioSourceDependencies"] as Boolean) {
    includeBuild("../ReScreen") {
        dependencySubstitution {
            substitute(module("dev.restudio:rescreen")).using(project(":"))
        }
    }

    includeBuild("../Remodel") {
        dependencySubstitution {
            substitute(module("dev.restudio:remodel")).using(project(":"))
        }
    }

    includeBuild("../Rebase") {
        dependencySubstitution {
            substitute(module("dev.restudio:rebase")).using(project(":"))
        }
    }

    includeBuild("../Recast") {
        dependencySubstitution {
            substitute(module("dev.restudio.recast:recast-bridge")).using(project(":recast-bridge"))
        }
    }

    includeBuild("../ReSync") {
        dependencySubstitution {
            substitute(module("restudio.resync:ReSyncCore")).using(project(":ReSyncCore"))
        }
    }
}
