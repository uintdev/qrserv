// MIT License - Copyright (c) 2026 Simple Badminton Contributors
// (Kudos for this solution!)

// Verification of '.note.gnu.build-id' removal: readelf --wide --notes libdartjni.so

import org.gradle.api.file.DirectoryProperty
import org.gradle.process.ExecOperations
import javax.inject.Inject

interface InjectedExecOps {
    @get:Inject
    val execOps: ExecOperations
}

// The NDK directory can't be looked up from here directly -- AndroidComponentsExtension lives in
// AGP, which this plugin (applied from buildSrc, a separate classloader) can't safely depend on
// without risking a duplicate/mismatched AGP on the classpath. app/build.gradle.kts sets this via
// the `noBuildId { }` accessor after applying com.android.application, where that lookup is safe.
interface NoBuildIdExtension {
    val ndkDirectory: DirectoryProperty
}

val noBuildId = extensions.create("noBuildId", NoBuildIdExtension::class.java)

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("NativeLibs") }
    .configureEach task@{
        // Everything doLast below touches is captured as a genuinely local value right here,
        // rather than referenced as a top-level script member (a function or the noBuildId
        // property) -- referencing this script's own members from inside a task action captures
        // the whole script object, which the configuration cache can't serialize.
        val ndkDirectoryProvider = noBuildId.ndkDirectory
        val injectedExecOps = objects.newInstance(InjectedExecOps::class.java)

        doLast {
            val ndkDir = ndkDirectoryProvider.orNull
            val objcopy = ndkDir?.let { dir ->
                // Scans the NDK's LLVM toolchain for llvm-objcopy without hardcoding a host
                // platform folder name (linux-x86_64/darwin-x86_64/windows-x86_64), so this works
                // cross-platform.
                dir.dir("toolchains/llvm/prebuilt").asFile.takeIf { it.isDirectory }
                    ?.listFiles { f -> f.isDirectory }
                    ?.sortedBy { it.name }
                    ?.firstNotNullOfOrNull { platform ->
                        File(platform, "bin/llvm-objcopy").takeIf { it.exists() }?.absolutePath
                    }
            }
            if (objcopy == null) {
                println("[no-build-id] llvm-objcopy not found under ${ndkDir?.asFile} - Build ID strip skipped")
                return@doLast
            }
            this@task.outputs.files.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                dir.walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".so") }
                    .forEach { f ->
                        injectedExecOps.execOps.exec {
                            commandLine(objcopy, "--remove-section", ".note.gnu.build-id", f.absolutePath)
                        }
                        println("[no-build-id] stripped Build ID: ${f.name}")
                    }
            }
        }
    }
