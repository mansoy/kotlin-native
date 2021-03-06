package org.jetbrains.kotlin

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec

import org.jetbrains.kotlin.konan.target.*

import java.io.FileWriter
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Test task for -produce framework testing. Requires a framework to be built by the Konan plugin
 * with konanArtifacts { framework(frameworkName, targets: [ testTarget] ) } and a dependency set
 * according to a pattern "compileKonan${frameworkName}".
 *
 * @property swiftSources  Swift-language test sources that use a given framework
 * @property frameworkName a framework name
 */
open class FrameworkTest : DefaultTask() {
    @Input
    lateinit var swiftSources: List<String>

    @Input
    lateinit var frameworkName: String

    private val testOutput by lazy {
        ((project.findProperty("sourceSets") as SourceSetContainer)
                .getByName("testOutputFramework") as SourceSet).output.dirs.singleFile.absolutePath
                ?: throw RuntimeException("Empty sourceSet")
    }

    @TaskAction
    fun run() {
        val frameworkPath = "$testOutput/$frameworkName/${project.testTarget().name}"

        // Sign framework
        val (stdOut, stdErr, exitCode) = runProcess(executor = localExecutor, executable = "/usr/bin/codesign",
                args = listOf("--verbose", "-s", "-", Paths.get(frameworkPath, "$frameworkName.framework").toString()))
        check(exitCode == 0, { """
            |Codesign failed with exitCode: $exitCode
            |stdout: $stdOut
            |stderr: $stdErr
            """.trimMargin()
        })

        // create a test provider and get main entry point
        val provider = Paths.get(testOutput, frameworkName, "provider.swift")
        FileWriter(provider.toFile()).use {
            it.write("""
                |// THIS IS AUTOGENERATED FILE
                |// This method is invoked by the main routine to get a list of tests
                |func registerProvider() {
                |    // TODO: assuming this naming for now
                |    ${frameworkName}Tests()
                |}
                """.trimMargin())
        }
        val testHome = project.file("framework").toPath()
        val swiftMain = Paths.get(testHome.toString(), "main.swift").toString()

        // Compile swift sources
        val sources = swiftSources.map { Paths.get(it).toString() } +
                listOf(provider.toString(), swiftMain)
        val options = listOf("-g", "-Xlinker", "-rpath", "-Xlinker", frameworkPath, "-F", frameworkPath)
        val testExecutable = Paths.get(testOutput, frameworkName, "swiftTestExecutable")
        swiftc(sources, options, testExecutable)

        runTest(testExecutable)
    }

    private fun runTest(testExecutable: Path) {
        val executor = (project.convention.plugins["executor"] as? ExecutorService)
                ?: throw RuntimeException("Executor wasn't found")
        val (stdOut, stdErr, exitCode) = runProcess(executor = executor::execute,
                executable = testExecutable.toString())

        println("""
            |stdout: $stdOut
            |stderr: $stdErr
            """.trimMargin())
        check(exitCode == 0, { "Execution failed with exit code: $exitCode "})
    }

    private val localExecutor = { a: Action<in ExecSpec> -> project.exec(a) }

    private fun Project.platformManager() = rootProject.findProperty("platformManager") as PlatformManager

    private fun Project.testTarget() = platformManager()
            .targetManager(project.findProperty("testTarget") as String?).target

    private fun swiftc(sources: List<String>, options: List<String>, output: Path) {
        val target = project.testTarget()
        val platform = project.platformManager().platform(target)
        assert(platform.configurables is AppleConfigurables)
        val configs = platform.configurables as AppleConfigurables
        val compiler = configs.absoluteTargetToolchain + "/usr/bin/swiftc"

        val swiftTarget = when (target) {
            KonanTarget.IOS_X64   -> "x86_64-apple-ios" + configs.osVersionMin
            KonanTarget.IOS_ARM64 -> "arm64_64-apple-ios" + configs.osVersionMin
            KonanTarget.MACOS_X64 -> "x86_64-apple-macosx" + configs.osVersionMin
            else -> throw IllegalStateException("Test target $target is not supported")
        }

        val args = listOf("-sdk", configs.absoluteTargetSysRoot, "-target", swiftTarget) +
                options + "-o" + output.toString() + sources

        val (stdOut, stdErr, exitCode) = runProcess(executor = localExecutor, executable = compiler, args = args)

        println("""
            |$compiler finished with exit code: $exitCode
            |options: ${args.joinToString(separator = " ")}
            |stdout: $stdOut
            |stderr: $stdErr
            """.trimMargin())
        check(exitCode == 0, { "Compilation failed" })
        check(output.toFile().exists(), { "Compiler swiftc hasn't produced an output file: $output" })
    }
}
