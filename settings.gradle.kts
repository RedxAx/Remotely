rootProject.name = "Remotely"

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
