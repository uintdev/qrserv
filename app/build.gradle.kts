import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import java.io.FileInputStream

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// Pass with -PNO_MES=true on the Gradle command line.
val noMES = (project.findProperty("NO_MES") as String?)?.toBoolean() ?: false

// Remove MANAGE_EXTERNAL_STORAGE permission task
abstract class StripPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun stripPermission() {
        val content = mergedManifest.get().asFile.readText()
        // Tolerant of whitespace/newlines anywhere in the tag, and of other attributes
        // (e.g. tools:ignore="ScopedStorage", present in the source manifest -- attribute
        // order and count can vary between the source manifest and however the merger
        // ultimately formats it) appearing before or after android:name.
        val attr = """\s+[\w:.-]+\s*=\s*"[^"]*""""
        val updated = content.replace(
            Regex(
                """<uses-permission\b($attr)*?\s+android:name\s*=\s*"android\.permission\.MANAGE_EXTERNAL_STORAGE"($attr)*\s*/>""",
                RegexOption.DOT_MATCHES_ALL
            ),
            ""
        )
        // Otherwise a format change the pattern misses would only surface as a rejected Play upload.
        if ("android.permission.MANAGE_EXTERNAL_STORAGE" in updated) {
            throw GradleException("MANAGE_EXTERNAL_STORAGE is still in the merged manifest; update StripPermissionTask's pattern.")
        }
        updatedManifest.get().asFile.writeText(updated)
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("no-build-id")
}

android {
    namespace = "dev.uint.qrserv"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "dev.uint.qrserv"
        minSdk = 26
        targetSdk = 37
        versionCode = 2075
        versionName = "4.2.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = keystoreProperties["storeFile"]?.let { file(it) }
                storePassword = keystoreProperties["storePassword"] as String
            } else {
                println("Keystore properties file not found. No signing configuration will be applied.")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


// Remove MANAGE_EXTERNAL_STORAGE only if NO_MES=true
androidComponents {
    noBuildId.ndkDirectory.set(sdkComponents.ndkDirectory)
    onVariants { variant ->
        if (noMES) {
            println("Patching out MANAGE_EXTERNAL_STORAGE permission")
            val stripTask = tasks.register(
                "${variant.name}StripPermission",
                StripPermissionTask::class.java
            ) {
                group = "build"
                description = "Removes the MANAGE_EXTERNAL_STORAGE permission from the merged " +
                    "manifest for the ${variant.name} variant (NO_MES=true)."
            }
            variant.artifacts.use(stripTask)
                .wiredWithFiles(
                    StripPermissionTask::mergedManifest,
                    StripPermissionTask::updatedManifest
                )
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        } else {
            println("Using MANAGE_EXTERNAL_STORAGE permission -- to not use this permission, add `-PNO_MES=true` to your build command")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.window:window-core:1.5.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Embedded HTTP server used to serve the selected file
    implementation("io.ktor:ktor-server-core:3.6.0")
    implementation("io.ktor:ktor-server-cio:3.6.0")

    // QR code generation
    implementation("com.google.zxing:core:3.5.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is a stub in local unit tests. This is Android's implementation, from AOSP, rather
    // than the reference org.json, which differs in places (e.g. optString on a JSON null).
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
