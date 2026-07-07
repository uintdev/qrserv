import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.Base64

import java.util.Properties
import java.io.FileInputStream

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Parse --dart-define values from Flutter
val dartDefines = (project.findProperty("dart-defines") as String?)
    ?.split(",")
    ?.map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
    ?.mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }
    ?.toMap() ?: emptyMap()

val noDAM = dartDefines["NO_DAM"]?.toBoolean() ?: false

// Remove MANAGE_EXTERNAL_STORAGE permission task
abstract class StripPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun stripPermission() {
        val manifestFile = mergedManifest.get().asFile
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            val updated = content.replace(
                Regex(
                    """<uses-permission\s+android:name="android\.permission\.MANAGE_EXTERNAL_STORAGE"\s*/>""",
                    RegexOption.MULTILINE
                ),
                ""
            )
            updatedManifest.get().asFile.writeText(updated)
        }
    }
}

plugins {
    id("com.android.application")
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "dev.uint.qrserv"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "dev.uint.qrserv"
        minSdk = 24
        targetSdk = 37
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
            abiFilters.clear()
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = keystoreProperties["storeFile"]?.let { file(it) }
                storePassword = keystoreProperties["storePassword"] as String
            } else {
                println("Keystore properties file not found. No signing configuration will be applied.");
            }
        }
    }

    buildTypes {
        named("release") {
            ndk.abiFilters.clear()
            ndk.abiFilters.addAll(listOf("arm64-v8a"))
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

afterEvaluate {
    apply(from = "../no-build-id.gradle")
}

// Remove MANAGE_EXTERNAL_STORAGE only if NO_DAM=true ──
androidComponents {
    onVariants { variant ->
        if (noDAM) {
            println("Patching out MANAGE_EXTERNAL_STORAGE permission")
            val stripTask = tasks.register(
                "${variant.name}StripPermission",
                StripPermissionTask::class.java
            )
            variant.artifacts.use(stripTask)
                .wiredWithFiles(
                    StripPermissionTask::mergedManifest,
                    StripPermissionTask::updatedManifest
                )
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        } else {
            println("Using MANAGE_EXTERNAL_STORAGE permission -- to not use this permission, add `--dart-define=NO_DAM=true` to your build command")
        }
    }
}
