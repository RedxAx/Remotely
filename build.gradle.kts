import groovy.json.JsonSlurper
import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("java-library")
    id("application")
}

group = "redxax.oxy"
version = property("remotely.version").toString()

application {
    mainClass.set("redxax.oxy.remotely.RemotelyInit")
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    api("dev.restudio:rescreen:1.0")
    api("dev.restudio:remodel:1.0.0")
    api("dev.restudio:rebase:1.0-SNAPSHOT")

    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")

    implementation("com.google.code.gson:gson:2.10.1")
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

    implementation("org.jetbrains.pty4j:pty4j:0.13.10-1")
    implementation("org.jetbrains.jediterm:jediterm-core:3.54")
    implementation("org.jetbrains.jediterm:jediterm-pty:2.69")

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
        val byteConstants = (root["byteConstants"] as List<*>).map { it.toString() }.toSet()
        val shortConstants = (root["shortConstants"] as List<*>).map { it.toString() }.toSet()
        val packageName = packageNames["remotely"].toString()
        val packageDir = generatedContractsDir.get().asFile.resolve(packageName.replace('.', '/'))
        packageDir.mkdirs()
        val output = packageDir.resolve("ReSyncProtocolContract.java")
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
