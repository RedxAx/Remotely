import groovy.json.JsonSlurper
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.util.UUID
import java.util.zip.ZipFile

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

val reStudioReleaseJarTasks: List<Any> = if (useReStudioSourceDependencies) {
    listOf(
        gradle.includedBuild("ReScreen").task(":jar"),
        gradle.includedBuild("Remodel").task(":jar"),
        gradle.includedBuild("Rebase").task(":jar"),
        gradle.includedBuild("Recast").task(":recast-api:jar"),
        gradle.includedBuild("Recast").task(":recast-bridge:jar"),
        gradle.includedBuild("ReSync").task(":ReSyncCore:jar")
    )
} else {
    emptyList()
}

val releaseRequiredClasses = listOf(
    "restudio/rescreen/config/UiConfigStore.class",
    "restudio/rebase/Rebase.class",
    "redxax/restudio/Remodel/Main.class",
    "dev/restudio/recast/api/FeatureDescriptor.class",
    "dev/restudio/recast/bridge/BridgeEntry.class",
    "restudio/resync/network/NetworkFrame.class"
)

fun requireReleaseClasses(archives: Collection<File>, artifactName: String) {
    val jarFiles = archives.filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
    val missingClasses = releaseRequiredClasses.filter { className ->
        jarFiles.none { archive -> ZipFile(archive).use { it.getEntry(className) != null } }
    }
    require(missingClasses.isEmpty()) {
        "$artifactName Is Missing Required Classes: ${missingClasses.joinToString()}"
    }
}

val sourceRuntimeInputs = linkedMapOf(
    "Remotely" to listOf("build/classes/java/main", "build/resources/main"),
    "ReScreen" to listOf("../ReScreen/build/classes/java/main", "../ReScreen/build/resources/main"),
    "Rebase" to listOf("../Rebase/build/classes/java/main", "../Rebase/build/resources/main"),
    "Remodel" to listOf("../Remodel/build/classes/java/main", "../Remodel/build/resources/main"),
    "RecastApi" to listOf("../Recast/recast-api/build/classes/java/main", "../Recast/recast-api/build/resources/main"),
    "RecastBridge" to listOf("../Recast/recast-bridge/build/classes/java/main", "../Recast/recast-bridge/build/resources/main"),
    "ReSyncCore" to listOf("../ReSync/ReSyncCore/build/classes/java/main", "../ReSync/ReSyncCore/build/resources/main")
)
val sourceRuntimeSnapshot = layout.projectDirectory.dir(".gradle/run-classpath/${UUID.randomUUID()}")
val stageSourceRuntime = tasks.register<Sync>("stageSourceRuntime") {
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
        sourceRuntimeInputs.forEach { (module, paths) ->
            paths.forEach { path ->
                from(path) {
                    into(module)
                }
            }
        }
        into(sourceRuntimeSnapshot)
    }
}

tasks.named<JavaExec>("run") {
    if (useReStudioSourceDependencies) {
        dependsOn(stageSourceRuntime)
        classpath = files()
        doFirst {
            val localProjectOutputs = files(sourceRuntimeInputs.keys.map { sourceRuntimeSnapshot.dir(it) })
            val externalRuntime = configurations.runtimeClasspath.get().files.filter {
                val path = it.absolutePath.replace('\\', '/')
                !path.contains("/ReScreen/build/libs/") &&
                    !path.contains("/Rebase/build/libs/") &&
                    !path.contains("/Remodel/build/libs/") &&
                    !path.contains("/Recast/recast-api/build/libs/") &&
                    !path.contains("/Recast/recast-bridge/build/libs/") &&
                    !path.contains("/ReSync/ReSyncCore/build/libs/")
            }
            classpath = files(localProjectOutputs, externalRuntime)
        }
    }
}

if (useReStudioSourceDependencies) {
    tasks.register<JavaExec>("runLive") {
        val launchAgent = layout.buildDirectory.file("live-refresh/rescreen-live-agent-${UUID.randomUUID()}.jar")
        group = "development"
        description = "Runs Remotely with ReScreen live refresh."
        mainClass.set(application.mainClass)
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.JETBRAINS)
        })
        dependsOn(
            tasks.named("classes"),
            gradle.includedBuild("ReScreen").task(":classes"),
            gradle.includedBuild("ReScreen").task(":liveAgentJar"),
            gradle.includedBuild("Rebase").task(":classes"),
            gradle.includedBuild("Remodel").task(":classes"),
            gradle.includedBuild("Recast").task(":recast-api:classes"),
            gradle.includedBuild("Recast").task(":recast-bridge:classes"),
            gradle.includedBuild("ReSync").task(":ReSyncCore:classes")
        )
        classpath = files()
        doFirst {
            val localProjectOutputs = files(sourceRuntimeInputs.values.flatten())
            val externalRuntime = configurations.runtimeClasspath.get().files.filter {
                val path = it.absolutePath.replace('\\', '/')
                !path.contains("/ReScreen/build/libs/") &&
                    !path.contains("/Rebase/build/libs/") &&
                    !path.contains("/Remodel/build/libs/") &&
                    !path.contains("/Recast/recast-api/build/libs/") &&
                    !path.contains("/Recast/recast-bridge/build/libs/") &&
                    !path.contains("/ReSync/ReSyncCore/build/libs/")
            }
            val agent = launchAgent.get().asFile
            agent.parentFile.mkdirs()
            layout.projectDirectory.file("../ReScreen/build/libs/rescreen-live-agent-build.jar").asFile.copyTo(agent, overwrite = true)
            val livePaths = localProjectOutputs.files.joinToString(File.pathSeparator) { it.absolutePath }
            classpath = files(localProjectOutputs, externalRuntime)
            jvmArgs(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition",
                "-javaagent:${agent.absolutePath}",
                "-Drescreen.live.paths=$livePaths"
            )
        }
        doLast {
            launchAgent.get().asFile.delete()
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
        dependsOn(stageSourceRuntime)
        classpath = files()
        doFirst {
            val localProjectOutputs = files(sourceRuntimeInputs.keys.map { sourceRuntimeSnapshot.dir(it) })
            val externalRuntime = configurations.runtimeClasspath.get().files.filter {
                val path = it.absolutePath.replace('\\', '/')
                !path.contains("/ReScreen/build/libs/") &&
                    !path.contains("/Rebase/build/libs/") &&
                    !path.contains("/Remodel/build/libs/") &&
                    !path.contains("/Recast/recast-api/build/libs/") &&
                    !path.contains("/Recast/recast-bridge/build/libs/") &&
                    !path.contains("/ReSync/ReSyncCore/build/libs/")
            }
            classpath = files(localProjectOutputs, externalRuntime)
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
    implementation("org.jetbrains.jediterm:jediterm-core:3.66")
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
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
    doFirst {
        manifest {
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name }
        }
    }
    archiveFileName.set("Remotely-App.jar")
}

tasks.register<Jar>("fatJar") {
    group = "build"
    dependsOn(reStudioReleaseJarTasks)
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
        attributes["Enable-Native-Access"] = "ALL-UNNAMED"
    }
    doLast {
        requireReleaseClasses(listOf(archiveFile.get().asFile), "Remotely Fat Jar")
    }
}

val packageJarName = "Remotely-App.jar"
val cleanVersion = version.toString().substringBefore('-').replace(Regex("[^0-9.]"), "")
val javaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
val jpackageExecutable = javaLauncher.map {
    val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "jpackage.exe" else "jpackage"
    File(it.metadata.installationPath.asFile, "bin/$executable").absolutePath
}

fun preparePackageInput(stagingDir: File, outputDir: File, artifactName: String) {
    outputDir.deleteRecursively()
    stagingDir.deleteRecursively()
    stagingDir.mkdirs()
    copy {
        from(tasks.named<Jar>("fatJar").flatMap { it.archiveFile })
        into(stagingDir)
        rename { packageJarName }
    }
    requireReleaseClasses(fileTree(stagingDir) { include("*.jar") }.files, artifactName)
}

tasks.register<Exec>("createInstaller") {
    dependsOn("fatJar")

    val stagingDir = layout.buildDirectory.dir("package-input/windows").get().asFile
    val outputDir = layout.buildDirectory.dir("dist").get().asFile
    val iconPath = layout.projectDirectory.file("packaging/Remotely.ico").asFile.absolutePath

    doFirst {
        preparePackageInput(stagingDir, outputDir, "Remotely Windows Installer")
    }

    commandLine(
        jpackageExecutable.get(),
        "--type", "exe",
        "--dest", outputDir.absolutePath,
        "--input", stagingDir.absolutePath,
        "--name", "Remotely",
        "--main-jar", packageJarName,
        "--main-class", application.mainClass.get(),
        "--app-version", cleanVersion,
        "--icon", iconPath,
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--win-shortcut",
        "--win-menu",
        "--win-menu-group", "ReStudio",
        "--win-dir-chooser",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx4G",
        "--java-options", "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.register<Exec>("createLinuxAppImage") {
    dependsOn("fatJar")

    val stagingDir = layout.buildDirectory.dir("package-input/linux").get().asFile
    val outputDir = layout.buildDirectory.dir("app-image").get().asFile
    val iconPath = layout.projectDirectory.file("packaging/Remotely.png").asFile.absolutePath

    doFirst {
        preparePackageInput(stagingDir, outputDir, "Remotely Linux AppImage")
    }

    commandLine(
        jpackageExecutable.get(),
        "--type", "app-image",
        "--dest", outputDir.absolutePath,
        "--input", stagingDir.absolutePath,
        "--name", "Remotely",
        "--main-jar", packageJarName,
        "--main-class", application.mainClass.get(),
        "--app-version", cleanVersion,
        "--icon", iconPath,
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx4G",
        "--java-options", "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.register<Exec>("createMacDmg") {
    dependsOn("fatJar")

    val stagingDir = layout.buildDirectory.dir("package-input/macos").get().asFile
    val outputDir = layout.buildDirectory.dir("dist-macos").get().asFile
    val iconPath = providers.gradleProperty("remotely.packageIcon").orElse(
        layout.projectDirectory.file("packaging/Remotely.icns").asFile.absolutePath
    )
    val macVersion = cleanVersion.split('.').take(3).joinToString(".")

    doFirst {
        preparePackageInput(stagingDir, outputDir, "Remotely macOS Disk Image")
    }

    commandLine(
        jpackageExecutable.get(),
        "--type", "dmg",
        "--dest", outputDir.absolutePath,
        "--input", stagingDir.absolutePath,
        "--name", "Remotely",
        "--main-jar", packageJarName,
        "--main-class", application.mainClass.get(),
        "--app-version", macVersion,
        "--icon", iconPath.get(),
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files",
        "--mac-package-identifier", "net.restudiomc.remotely",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xmx4G",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "-XstartOnFirstThread"
    )
}
