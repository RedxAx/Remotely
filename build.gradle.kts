import groovy.json.JsonSlurper
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("java-library")
    id("application")
    id("maven-publish")
}

group = "redxax.oxy"
version = property("remotely.version").toString()
val useReStudioSourceDependencies = extra["reStudioSourceDependencies"] as Boolean

application {
    mainClass.set("redxax.oxy.remotely.RemotelyInit")
}

tasks.named<JavaExec>("run") {
    if (useReStudioSourceDependencies) {
        dependsOn(
            tasks.named("classes"),
            gradle.includedBuild("ReScreen").task(":classes"),
            gradle.includedBuild("Rebase").task(":classes"),
            gradle.includedBuild("Remodel").task(":classes"),
            gradle.includedBuild("Recast").task(":recast-api:classes"),
            gradle.includedBuild("Recast").task(":recast-bridge:classes"),
            gradle.includedBuild("ReSync").task(":ReSyncCore:classes")
        )
        classpath = files()
        doFirst {
            val localProjectOutputs = files(
                "../ReScreen/build/classes/java/main",
                "../ReScreen/build/resources/main",
                "../Rebase/build/classes/java/main",
                "../Rebase/build/resources/main",
                "../Remodel/build/classes/java/main",
                "../Remodel/build/resources/main",
                "../Recast/recast-api/build/classes/java/main",
                "../Recast/recast-api/build/resources/main",
                "../Recast/recast-bridge/build/classes/java/main",
                "../Recast/recast-bridge/build/resources/main",
                "../ReSync/ReSyncCore/build/classes/java/main",
                "../ReSync/ReSyncCore/build/resources/main"
            )
            val externalRuntime = configurations.runtimeClasspath.get().files.filter {
                val path = it.absolutePath.replace('\\', '/')
                !path.contains("/ReScreen/build/libs/") &&
                    !path.contains("/Rebase/build/libs/") &&
                    !path.contains("/Remodel/build/libs/") &&
                    !path.contains("/Recast/recast-api/build/libs/") &&
                    !path.contains("/Recast/recast-bridge/build/libs/") &&
                    !path.contains("/ReSync/ReSyncCore/build/libs/")
            }
            classpath = files(sourceSets.main.get().output, localProjectOutputs, externalRuntime)
        }
    }
}

tasks.register<JavaExec>("webHost") {
    group = "application"
    description = "Runs the ReScreen web host for this application."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("restudio.rescreen.platform.web.WebReScreenHost")
    if (useReStudioSourceDependencies) {
        dependsOn(
            tasks.named("classes"),
            gradle.includedBuild("ReScreen").task(":classes"),
            gradle.includedBuild("Rebase").task(":classes"),
            gradle.includedBuild("Remodel").task(":classes"),
            gradle.includedBuild("Recast").task(":recast-api:classes"),
            gradle.includedBuild("Recast").task(":recast-bridge:classes"),
            gradle.includedBuild("ReSync").task(":ReSyncCore:classes")
        )
        classpath = files()
        doFirst {
            val localProjectOutputs = files(
                "../ReScreen/build/classes/java/main",
                "../ReScreen/build/resources/main",
                "../Rebase/build/classes/java/main",
                "../Rebase/build/resources/main",
                "../Remodel/build/classes/java/main",
                "../Remodel/build/resources/main",
                "../Recast/recast-api/build/classes/java/main",
                "../Recast/recast-api/build/resources/main",
                "../Recast/recast-bridge/build/classes/java/main",
                "../Recast/recast-bridge/build/resources/main",
                "../ReSync/ReSyncCore/build/classes/java/main",
                "../ReSync/ReSyncCore/build/resources/main"
            )
            val externalRuntime = configurations.runtimeClasspath.get().files.filter {
                val path = it.absolutePath.replace('\\', '/')
                !path.contains("/ReScreen/build/libs/") &&
                    !path.contains("/Rebase/build/libs/") &&
                    !path.contains("/Remodel/build/libs/") &&
                    !path.contains("/Recast/recast-api/build/libs/") &&
                    !path.contains("/Recast/recast-bridge/build/libs/") &&
                    !path.contains("/ReSync/ReSyncCore/build/libs/")
            }
            classpath = files(sourceSets.main.get().output, localProjectOutputs, externalRuntime)
        }
    }
}

repositories {
    mavenLocal {
        content {
            includeGroup("dev.restudio")
            includeGroup("dev.restudio.recast")
            includeGroup("restudio.resync")
        }
    }
    mavenCentral()
    maven("https://repo.gradle.org/gradle/libs-releases")
    maven("https://maven.scijava.org/content/repositories/public/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    maven("https://jitpack.io/")
}

publishing {
    publications {
        create<MavenPublication>("restudio") {
            from(components["java"])
            groupId = "dev.restudio"
            artifactId = "remotely-app"
        }
    }
}

dependencies {
    api("dev.restudio:rescreen:1.0")
    api("dev.restudio:remodel:1.0.0")
    api("dev.restudio:rebase:1.0-SNAPSHOT")
    implementation("dev.restudio.recast:recast-bridge:1.0.0-SNAPSHOT")
    implementation("restudio.resync:ReSyncCore:1.3.0")

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.github.canary-prism:querz-nbt:6.2.1")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.yaml:snakeyaml:2.6")

    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-ins:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("org.jsoup:jsoup:1.15.4")

    implementation("org.apache.xmlgraphics:batik-transcoder:1.19")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    implementation("com.github.javakeyring:java-keyring:1.0.4")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
    implementation("com.hierynomus:sshj:0.40.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("com.github.JnCrMx:discord-game-sdk4j:1.0.0")

    implementation("org.jetbrains.pty4j:pty4j:0.13.10-1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.54")
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")

    val lwjglVersion = "3.3.6"
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.joml:joml:1.9.25")

    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")

    runtimeOnly("org.lwjgl:lwjgl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux")

    runtimeOnly("org.lwjgl:lwjgl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos")

    runtimeOnly("org.lwjgl:lwjgl::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-stb::natives-macos-arm64")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos-arm64")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    test {
        useJUnitPlatform()
    }
}

if (useReStudioSourceDependencies) {
    tasks.register("publishSourceDependenciesToMavenLocal") {
        group = "publishing"
        dependsOn(
            gradle.includedBuild("Remodel").task(":publishToMavenLocal"),
            gradle.includedBuild("ReScreen").task(":publishToMavenLocal"),
            gradle.includedBuild("Rebase").task(":publishToMavenLocal"),
            gradle.includedBuild("Recast").task(":recast-api:publishToMavenLocal"),
            gradle.includedBuild("Recast").task(":recast-bridge:publishToMavenLocal"),
            gradle.includedBuild("ReSync").task(":ReSyncCore:publishToMavenLocal")
        )
    }
}

tasks.register<JavaExec>("reSyncProductionAcceptance") {
    group = "verification"
    description = "Runs the production ReSync client against a live server or its offline registry cache."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("redxax.oxy.remotely.data.flow.ReSyncProductionAcceptanceMain")
    systemProperty("user.home", providers.gradleProperty("acceptanceHome").orElse(layout.buildDirectory.dir("resync-acceptance-home").map { it.asFile.absolutePath }).get())
}

val generatedContractsDir = layout.buildDirectory.dir("generated/sources/resyncContracts/java")
val protocolContractFile = layout.projectDirectory.file("contracts/resync-protocol.json")

sourceSets {
    main {
        java.srcDir(generatedContractsDir)
    }
}

val generateReSyncProtocolContract by tasks.registering {
    inputs.file(protocolContractFile)
    outputs.dir(generatedContractsDir)
    doLast {
        val root = JsonSlurper().parse(protocolContractFile.asFile) as Map<*, *>
        val packageNames = root["packageNames"] as Map<*, *>
        val constants = root["constants"] as Map<*, *>
        val resources = (root["resources"] as? List<*>) ?: emptyList<Any>()
        val byteConstants = (root["byteConstants"] as List<*>).map { it.toString() }.toSet()
        val shortConstants = (root["shortConstants"] as List<*>).map { it.toString() }.toSet()
        val packageName = packageNames["remotely"].toString()
        val packageDir = generatedContractsDir.get().asFile.resolve(packageName.replace('.', '/'))
        packageDir.mkdirs()
        val output = packageDir.resolve("ReSyncProtocolContract.java")
        fun quoted(value: Any?) = "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
        output.writeText(buildString {
            appendLine("package $packageName;")
            appendLine()
            appendLine("public final class ReSyncProtocolContract {")
            constants.forEach { (rawName, rawValue) ->
                val name = rawName.toString()
                val value = rawValue ?: return@forEach
                val line = when {
                    value is String -> "    public static final String $name = \"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\";"
                    byteConstants.contains(name) -> "    public static final byte $name = (byte) 0x${(value as Number).toInt().toString(16).uppercase().padStart(2, '0')};"
                    shortConstants.contains(name) -> "    public static final short $name = ${(value as Number).toInt()};"
                    else -> "    public static final int $name = ${(value as Number).toInt()};"
                }
                appendLine(line)
            }
            appendLine()
            appendLine("    public record ResourceFlowPackets(byte request, byte listRequest, byte data, byte list, byte save, byte delete, byte saveAck) {")
            appendLine("    }")
            appendLine()
            appendLine("    public record ResourceContract(String typeId, String displayName, String defaultFolder, boolean jsonStorageSupported, ResourceFlowPackets flowPackets) {")
            appendLine("    }")
            appendLine()
            appendLine("    public static final ResourceContract[] RESOURCE_CONTRACTS = new ResourceContract[] {")
            resources.forEachIndexed { index, rawResource ->
                val resource = rawResource as Map<*, *>
                val flowPackets = resource["flowPackets"] as? Map<*, *>
                val packetText = if (flowPackets == null) {
                    "null"
                } else {
                    "new ResourceFlowPackets((byte) 0x${(flowPackets["request"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["listRequest"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["data"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["list"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["save"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["delete"] as Number).toInt().toString(16).uppercase().padStart(2, '0')}, (byte) 0x${(flowPackets["saveAck"] as Number).toInt().toString(16).uppercase().padStart(2, '0')})"
                }
                val suffix = if (index == resources.lastIndex) "" else ","
                appendLine("        new ResourceContract(${quoted(resource["typeId"])}, ${quoted(resource["displayName"])}, ${quoted(resource["defaultFolder"])}, ${resource["jsonStorageSupported"] == true}, $packetText)$suffix")
            }
            appendLine("    };")
            appendLine()
            appendLine("    public static ResourceContract resource(String typeId) {")
            appendLine("        for (ResourceContract resource : RESOURCE_CONTRACTS) {")
            appendLine("            if (resource.typeId().equals(typeId)) {")
            appendLine("                return resource;")
            appendLine("            }")
            appendLine("        }")
            appendLine("        return null;")
            appendLine("    }")
            appendLine()
            appendLine("    public static DialogResource dialogResource(com.google.gson.JsonObject json, String fallbackId) {")
            appendLine("        return new DialogResource(json, fallbackId);")
            appendLine("    }")
            appendLine()
            appendLine("    public static final class DialogResource {")
            appendLine("        private final com.google.gson.JsonObject json;")
            appendLine("        private final String fallbackId;")
            appendLine()
            appendLine("        private DialogResource(com.google.gson.JsonObject json, String fallbackId) {")
            appendLine("            this.json = json != null ? json : new com.google.gson.JsonObject();")
            appendLine("            this.fallbackId = fallbackId == null || fallbackId.isBlank() ? \"dialog\" : fallbackId;")
            appendLine("        }")
            appendLine()
            appendLine("        public com.google.gson.JsonObject json() {")
            appendLine("            return json;")
            appendLine("        }")
            appendLine()
            appendLine("        public void applyDefaults(String defaultFolder) {")
            appendLine("            if (!json.has(\"id\") || text(\"id\", \"\").isBlank()) json.addProperty(\"id\", fallbackId);")
            appendLine("            if (!json.has(\"displayName\")) json.addProperty(\"displayName\", text(\"id\", fallbackId));")
            appendLine("            if (!json.has(\"folder\")) json.addProperty(\"folder\", defaultFolder == null ? \"Content/Dialogs\" : defaultFolder);")
            appendLine("            if (!json.has(\"enabled\")) json.addProperty(\"enabled\", true);")
            appendLine("            if (!json.has(\"type\")) json.addProperty(\"type\", \"minecraft:multi_action\");")
            appendLine("            if (!json.has(\"title\")) json.addProperty(\"title\", displayName());")
            appendLine("            ensureArray(\"body\");")
            appendLine("            ensureArray(\"inputs\");")
            appendLine("            ensureArray(\"actions\");")
            appendLine("            if (!json.has(\"can_close_with_escape\")) json.addProperty(\"can_close_with_escape\", true);")
            appendLine("            if (!json.has(\"after_action\")) json.addProperty(\"after_action\", \"close\");")
            appendLine("            if (!json.has(\"columns\")) json.addProperty(\"columns\", 1);")
            appendLine("        }")
            appendLine()
            appendLine("        public String displayName() {")
            appendLine("            return text(\"displayName\", text(\"id\", fallbackId));")
            appendLine("        }")
            appendLine()
            appendLine("        public String title() {")
            appendLine("            return text(\"title\", displayName());")
            appendLine("        }")
            appendLine()
            appendLine("        public String externalTitle() {")
            appendLine("            return text(\"external_title\", displayName());")
            appendLine("        }")
            appendLine()
            appendLine("        public String type() {")
            appendLine("            return text(\"type\", \"minecraft:multi_action\");")
            appendLine("        }")
            appendLine()
            appendLine("        public boolean canCloseWithEscape() {")
            appendLine("            return bool(\"can_close_with_escape\", true);")
            appendLine("        }")
            appendLine()
            appendLine("        public boolean pause() {")
            appendLine("            return bool(\"pause\", true);")
            appendLine("        }")
            appendLine()
            appendLine("        public String afterAction() {")
            appendLine("            return text(\"after_action\", \"close\");")
            appendLine("        }")
            appendLine()
            appendLine("        public int columns() {")
            appendLine("            return integer(\"columns\", 1);")
            appendLine("        }")
            appendLine()
            appendLine("        public java.util.List<com.google.gson.JsonObject> body() {")
            appendLine("            return objectArray(\"body\");")
            appendLine("        }")
            appendLine()
            appendLine("        public java.util.List<com.google.gson.JsonObject> inputs() {")
            appendLine("            return objectArray(\"inputs\");")
            appendLine("        }")
            appendLine()
            appendLine("        public java.util.List<com.google.gson.JsonObject> actions() {")
            appendLine("            return objectArray(\"actions\");")
            appendLine("        }")
            appendLine()
            appendLine("        private void ensureArray(String key) {")
            appendLine("            if (!json.has(key) || !json.get(key).isJsonArray()) json.add(key, new com.google.gson.JsonArray());")
            appendLine("        }")
            appendLine()
            appendLine("        private java.util.List<com.google.gson.JsonObject> objectArray(String key) {")
            appendLine("            java.util.List<com.google.gson.JsonObject> values = new java.util.ArrayList<>();")
            appendLine("            com.google.gson.JsonArray array = json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new com.google.gson.JsonArray();")
            appendLine("            for (com.google.gson.JsonElement element : array) if (element != null && element.isJsonObject()) values.add(element.getAsJsonObject());")
            appendLine("            return values;")
            appendLine("        }")
            appendLine()
            appendLine("        private String text(String key, String fallback) {")
            appendLine("            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;")
            appendLine("        }")
            appendLine()
            appendLine("        private boolean bool(String key, boolean fallback) {")
            appendLine("            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;")
            appendLine("        }")
            appendLine()
            appendLine("        private int integer(String key, int fallback) {")
            appendLine("            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    private ReSyncProtocolContract() {")
            appendLine("    }")
            appendLine("}")
        })
    }
}

tasks.compileJava {
    dependsOn(generateReSyncProtocolContract)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "redxax.oxy.remotely.RemotelyInit"
        attributes["Implementation-Version"] = project.version.toString()
        attributes["Automatic-Module-Name"] = "dev.restudio.remotely.app"
    }
    doFirst {
        manifest {
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name }
        }
    }
    archiveFileName.set("Remotely-App.jar")
}

tasks.register<Exec>("createInstaller") {
    dependsOn("clean", "jar")

    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    val javaLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.get()

    val jdkHome = javaLauncher.metadata.installationPath.asFile
    val jpackagePath = File(jdkHome, "bin/jpackage.exe").absolutePath

    val stagingDir = layout.buildDirectory.dir("staging").get().asFile.absolutePath
    val inputDir = layout.buildDirectory.dir("libs").get().asFile.absolutePath
    val outputDir = layout.buildDirectory.dir("dist").get().asFile.absolutePath
    val jarName = "Remotely-App.jar"
    val iconPath = "C:/Users/redxa/Downloads/Remotely.ico"
    val cleanVersion = version.toString().split("-")[0].replace(Regex("[^0-9.]"), "")

    doFirst {
        println("--------------------------------------------------")
        println("Using jpackage:  $jpackagePath")
        println("--------------------------------------------------")

        file(outputDir).deleteRecursively()
        file(stagingDir).deleteRecursively()
        file(stagingDir).mkdirs()

        copy {
            from(inputDir)
            into(stagingDir)
            include(jarName)
        }

        copy {
            from(configurations.runtimeClasspath)
            into(stagingDir)
        }

        println("Staging directory contents:")
        file(stagingDir).listFiles()?.forEach { println(it.name) }
    }

    commandLine(
        jpackagePath,
        "--type", "exe",
        "--dest", outputDir,
        "--input", stagingDir,
        "--name", "Remotely",
        "--main-jar", jarName,
        "--main-class", application.mainClass.get(),
        "--app-version", cleanVersion,
        "--icon", iconPath,
        "--win-shortcut",
        "--win-menu",
        "--win-menu-group", "ReStudio",
        "--win-dir-chooser",
//        "--win-console",
        "--java-options", "-Dfile.encoding=UTF-8 -Xmx4G"
    )
}

tasks.register<Jar>("fatJar") {
    group = "build"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("Remotely-Fat.jar")
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.exists() }.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest {
        attributes["Main-Class"] = "redxax.oxy.remotely.RemotelyInit"
        attributes["Implementation-Version"] = project.version.toString()
        attributes["Automatic-Module-Name"] = "dev.restudio.remotely.app"
    }
}
