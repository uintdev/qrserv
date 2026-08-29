// MIT License - Copyright (c) 2026 Simple Badminton Contributors
// (Kudos for this solution!)

// Verification of '.note.gnu.build-id' removal: readelf --wide --notes libdartjni.so

import org.gradle.process.ExecOperations
import javax.inject.Inject

interface InjectedExecOps {
    @get:Inject
    val execOps: ExecOperations
}

fun findObjcopy(home: String): String? {
    if (home.isEmpty()) return null
    val ndkRoot = File("$home/ndk")
    if (!ndkRoot.isDirectory) return null
    ndkRoot.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { ver ->
        println("[no-build-id] first path to check for llvm-objcopy: $ver/toolchains/llvm/prebuilt")
        val prebuilt = File(ver, "toolchains/llvm/prebuilt")
        if (!prebuilt.isDirectory) return@forEach
        prebuilt.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach { platform ->
            println("[no-build-id] second path to check for llvm-objcopy: $platform/bin/llvm-objcopy")
            val c = File(platform, "bin/llvm-objcopy")
            if (c.exists()) return c.absolutePath
        }
    }
    return null
}

val home = System.getenv("ANDROID_HOME") ?: ""
println("[no-build-id] ANDROID_HOME environment variable value: $home")

val objcopy = findObjcopy(home)
if (objcopy == null) {
    println("[no-build-id] llvm-objcopy not found - Build ID strip skipped")
} else {
    tasks.matching { it.name.startsWith("merge") && it.name.endsWith("NativeLibs") }
        .configureEach task@{
            doLast {
                val injected = project.objects.newInstance(InjectedExecOps::class.java)
                this@task.outputs.files.forEach { dir ->
                    if (!dir.isDirectory) return@forEach
                    dir.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".so") }
                        .forEach { f ->
                            injected.execOps.exec {
                                commandLine(objcopy, "--remove-section", ".note.gnu.build-id", f.absolutePath)
                            }
                            println("[no-build-id] stripped Build ID: ${f.name}")
                        }
                }
            }
        }
}
