package fr.smolder.hytale.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.api.GradleException
import java.io.File
import org.gradle.process.CommandLineArgumentProvider

interface HytaleExtension {
    val hytalePath: Property<String>
    val patchLine: Property<String>
    val gameVersion: Property<String>
    val serverJar: RegularFileProperty
    val includesAssetPack: Property<Boolean>
    val earlyPlugin: Property<Boolean>
    val loadUserMods: Property<Boolean>
    val autoUpdateManifest: Property<Boolean>
    val jvmArgs: ListProperty<String>
    val serverArgs: ListProperty<String>
    val minMemory: Property<String>
    val maxMemory: Property<String>
    val vineflowerVersion: Property<String>
    val decompileFilter: ListProperty<String>
    val decompilerHeapSize: Property<String>
    val useAotCache: Property<Boolean>
    val includeDecompiledSources: Property<Boolean>
}

class HytaleDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<HytaleExtension>("hytale")

        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        
        val defaultHytaleHome = when {
            os.contains("win") -> "$userHome/AppData/Roaming/Hytale"
            os.contains("mac") -> "$userHome/Library/Application Support/Hytale"
            else -> {
                val xdgData = System.getenv("XDG_DATA_HOME")
                if (xdgData.isNullOrEmpty()) "$userHome/.local/share/Hytale" else "$xdgData/Hytale"
            }
        }
        
        extension.hytalePath.convention(defaultHytaleHome)
        extension.patchLine.convention("release")
        extension.gameVersion.convention("latest")
        extension.includesAssetPack.convention(true)
        extension.earlyPlugin.convention(false)
        extension.loadUserMods.convention(false)
        extension.autoUpdateManifest.convention(true)
        extension.minMemory.convention("1G")
        extension.maxMemory.convention("4G")
        extension.vineflowerVersion.convention("1.11.2")
        extension.decompileFilter.convention(listOf("com/hypixel/**"))
        extension.decompilerHeapSize.convention("6G")
        extension.useAotCache.convention(true)
        extension.includeDecompiledSources.convention(true)

        val resolvedServerJar = project.layout.file(project.provider {
            val home = extension.hytalePath.get()
            val patch = extension.patchLine.get()
            val version = extension.gameVersion.get()
            File("$home/install/$patch/package/game/$version/Server/HytaleServer.jar")
        })
        extension.serverJar.convention(resolvedServerJar)

        extension.jvmArgs.convention(project.provider {
            val argsList = mutableListOf<String>()

            if (extension.useAotCache.get()) {
                val serverJar = extension.serverJar.get().asFile
                val aotFile = File(serverJar.parentFile, "HytaleServer.aot")
                if (aotFile.exists()) {
                    argsList.add("-XX:AOTCache=${aotFile.absolutePath}")
                }
            }

            if (extension.earlyPlugin.get()) {
                val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                val mainSourceSet = javaExtension.sourceSets.getByName("main")

                argsList.add("-Dhyxin-target=${mainSourceSet.output.joinToString(",")}")
            }

            argsList
        })

        extension.serverArgs.convention(project.provider {
            val argsList = mutableListOf<String>()
            argsList.add("--allow-op")
            argsList.add("--disable-sentry")

            val home = extension.hytalePath.get()
            val patch = extension.patchLine.get()
            val version = extension.gameVersion.get()
            val assetsPath = "$home/install/$patch/package/game/$version/Assets.zip"
            argsList.add("--assets=$assetsPath")

            if (extension.includesAssetPack.get()) {
                 val srcMain = project.file("src/main")
                 argsList.add("--mods=${srcMain.absolutePath}")
            }

            if (extension.earlyPlugin.get()) {
                argsList.add("--accept-early-plugins")
            }

            if (extension.loadUserMods.get()) {
                argsList.add("--mods=$home/UserData/Mods")
            }
            argsList
        })

        val decompiler = project.configurations.create("decompiler")
        project.afterEvaluate {
            project.dependencies.add("decompiler", "org.vineflower:vineflower:${extension.vineflowerVersion.get()}")
            project.dependencies.add("implementation", project.files(extension.serverJar))
        }

        configureManifestUpdate(project, extension)
        configureDecompileTask(project, extension, decompiler)
        configureRunTask(project, extension)

        project.afterEvaluate {
            project.pluginManager.withPlugin("java") {
                if (extension.includeDecompiledSources.get()) {
                    val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                    val decompiledSourcesDir = project.layout.buildDirectory.dir("decompile/sources")
                    javaExtension.sourceSets.getByName("main").java.srcDir(decompiledSourcesDir)
                }
            }
        }
    }

    private fun configureManifestUpdate(project: Project, extension: HytaleExtension) {
        val updateTask = project.tasks.register("updatePluginManifest") {
            val manifestFile = project.file("src/main/resources/manifest.json")
            
            inputs.property("version", project.version)
            inputs.property("includes_pack", extension.includesAssetPack)
            outputs.file(manifestFile)

            onlyIf { extension.autoUpdateManifest.get() }

            doLast {
                if (!manifestFile.exists()) {
                    throw GradleException("Could not find manifest.json at ${manifestFile.path}!")
                }

                @Suppress("UNCHECKED_CAST")
                val manifestJson = JsonSlurper().parseText(manifestFile.readText()) as MutableMap<String, Any>
                manifestJson["Version"] = project.version.toString()
                manifestJson["IncludesAssetPack"] = extension.includesAssetPack.get()

                manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson)))
            }
        }
        
        project.tasks.named("processResources") {
            dependsOn(updateTask)
        }
    }

    private fun configureRunTask(project: Project, extension: HytaleExtension) {
        val serverRunDir = project.file("${project.projectDir}/run")
        if (!serverRunDir.exists()) {
            serverRunDir.mkdirs()
        }

        project.tasks.register<JavaExec>("runServer") {
            group = "hytale"
            description = "Runs the Hytale Server"

            dependsOn(project.tasks.named("build"))

            mainClass.set("com.hypixel.hytale.Main")
            
            val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
            val mainSourceSet = javaExtension.sourceSets.getByName("main")
            
            classpath(extension.serverJar)
            classpath(mainSourceSet.runtimeClasspath)

            doFirst {
                if(!extension.serverJar.get().asFile.exists()) {
                    throw GradleException("Hytale Server JAR not found at: ${extension.serverJar.get().asFile.absolutePath}")
                }
                minHeapSize = extension.minMemory.get()
                maxHeapSize = extension.maxMemory.get()

                val extraJvmArgs = extension.jvmArgs.get()
                jvmArgs(extraJvmArgs)
            }

            argumentProviders.add(CommandLineArgumentProvider {
                extension.serverArgs.get()
            })
            
            workingDir = serverRunDir
            standardInput = System.`in`
        }
    }

    private fun configureDecompileTask(
        project: Project,
        extension: HytaleExtension,
        decompiler: org.gradle.api.artifacts.Configuration
    ) {
        val tempClassesDir = project.layout.buildDirectory.dir("decompile/classes")
        val decompiledOutputDir = project.layout.buildDirectory.dir("decompile/sources")

        project.tasks.register<JavaExec>("decompileServer") {
            group = "hytale"
            description = "Decompiles the Hytale Server JAR using Vineflower"

            classpath = decompiler
            mainClass.set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler")

            maxHeapSize = extension.decompilerHeapSize.get()
            jvmArgs(listOf("-Xms2G") + extension.jvmArgs.get())

            doFirst {
                val serverJar = extension.serverJar.get().asFile
                if (!serverJar.exists()) {
                    throw GradleException("Hytale Server JAR not found at: ${serverJar.absolutePath}")
                }

                val classesDir = tempClassesDir.get().asFile
                val outputDir = decompiledOutputDir.get().asFile
                
                project.delete(classesDir)
                project.delete(outputDir)
                project.mkdir(classesDir)
                project.mkdir(outputDir)

                println("Extracting classes from ${serverJar.name}...")
                project.copy {
                    from(project.zipTree(serverJar))
                    into(classesDir)
                    extension.decompileFilter.get().forEach { pattern ->
                        include(pattern)
                    }
                }

                println("Starting decompilation...")

                args = listOf(
                    "-dgs=1", "-rsy=1", "-rbr=1", "-lit=1", "-jvn=1", "-log=ERROR",
                    classesDir.absolutePath,
                    outputDir.absolutePath
                )
            }

            doLast {
                val serverJar = extension.serverJar.get().asFile
                val sourcesJar = File(serverJar.parentFile, serverJar.nameWithoutExtension + "-sources.jar")
                val outputDir = decompiledOutputDir.get().asFile

                println("Creating sources jar: ${sourcesJar.name}...")
                project.ant.invokeMethod("zip", mapOf(
                    "destfile" to sourcesJar.absolutePath,
                    "basedir" to outputDir.absolutePath
                ))

                println("--------------------------------------------------")
                println("Done! Sources jar created at: ${sourcesJar.absolutePath}")
                println("--------------------------------------------------")
            }
        }
    }
}
