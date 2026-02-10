plugins {
    id("dev.deftu.gradle.multiversion-root")
}

preprocess {
    strictExtraMappings.set(true)

    "1.21.11-fabric"(1_21_11, "srg") {
        "1.21.10-fabric"(1_21_10, "srg") {
            "1.21.8-fabric"(1_21_08, "srg") {
                "1.21.6-fabric"(1_21_06, "srg") {
                    "1.21.5-fabric"(1_21_05, "srg") {
                        "1.21.4-fabric"(1_21_04, "srg") {
                            "1.21.1-fabric"(1_21_01, "srg")
                        }
                    }
                }
            }
        }
    }
}
