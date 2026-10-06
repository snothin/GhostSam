@file:Suppress("UnstableApiUsage")

import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
}

val versionPropsFile = rootProject.file("version.properties")

fun readVersionProps(): Properties {
    val props = Properties()
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { props.load(it) }
    }
    return props
}

fun currentVersionCode(): Int = readVersionProps().getProperty("versionCode")?.toIntOrNull() ?: 1
fun currentVersionName(): String = readVersionProps().getProperty("versionName") ?: "0.0.1"

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.snothin.ghostsam"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.snothin.ghostsam"
        minSdk = 33
        targetSdk = 36
        versionCode = currentVersionCode()
        versionName = currentVersionName()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        resConfigs("en", "zh-rCN")
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = false
            }
        }
    }

    buildTypes {
        debug {
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    androidResources {
        ignoreAssetsPatterns += "dexopt"
    }

    packaging {
        resources.excludes += "/META-INF/**/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/**/{LICENSE*,NOTICE*,README*}"
        resources.excludes += "org/bouncycastle/pqc/**"

        resources.excludes += "DebugProbesKt.bin"
        resources.excludes += "org/bouncycastle/x509/**"
        resources.excludes += "kotlin/**/*.kotlin_builtins"
        resources.excludes += "kotlin/*.kotlin_builtins"
        resources.excludes += "org/conscrypt/conscrypt.properties"
        resources.excludes += "META-INF/*.version"
        resources.excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

abstract class SyncCompanionApkTask : DefaultTask() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val dir = outputDir.get().asFile.resolve("companion")
        dir.mkdirs()
        dir.resolve("companion.apk").writeBytes(apk.get().asFile.readBytes())
    }
}

val syncCompanionApk by tasks.registering(SyncCompanionApkTask::class) {
    apk.set(layout.projectDirectory.file("../companion/build/outputs/apk/release/companion-release.apk"))
    outputDir.set(layout.buildDirectory.dir("companion-assets"))
    dependsOn(":companion:assembleRelease")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncCompanionApk) { it.outputDir }
    }
}

val verifyPayloads by tasks.registering {
    val payloadsDir = layout.projectDirectory.dir("src/main/assets/payloads").asFile
    val seriesTableFile = layout.projectDirectory
        .file("src/main/java/com/snothin/ghostsam/data/device/SeriesTable.kt").asFile

    doLast {
        val series = mutableListOf<Pair<String, String>>()   // id to kmi
        var id: String? = null
        var kmi: String? = null
        seriesTableFile.readLines().forEach { line ->
            val t = line.trim()
            when {
                t.startsWith("id = \"") -> id = t.substringAfter("id = \"").substringBefore("\"")
                t.startsWith("kmi = \"") -> kmi = t.substringAfter("kmi = \"").substringBefore("\"")
                t.startsWith("preloadPath = \"") -> {
                    val pid = id
                    val pkmi = kmi
                    if (pid != null && pkmi != null) series += pid to pkmi
                }
            }
        }
        if (series.isEmpty()) throw GradleException("verifyPayloads: no series parsed from SeriesTable.kt")

        val problems = mutableListOf<String>()
        val expected = mutableSetOf<String>()
        fun require(rel: String) {
            expected += rel
            val f = payloadsDir.resolve(rel)
            when {
                !f.isFile -> problems += "missing: $rel"
                f.length() == 0L -> problems += "empty file: $rel"
            }
        }
        series.forEach { (sid, _) ->
            require("$sid/preload.so")
            require("$sid/su_daemon")
        }
        require("ksud/ksud")
        require("ksud/ksud-next")
        listOf("ksud/ksud", "ksud/ksud-next").forEach { rel ->
            val f = payloadsDir.resolve(rel)
            if (f.isFile && f.length() >= 4) {
                val magic = f.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
                val elf = magic[0] == 0x7f.toByte() && magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
                if (!elf) problems += "$rel: not an ELF (placeholder text rejected)"
            }
        }

        val dfrDir = payloadsDir.resolve("dirtyfrag")
        val dfrKmiFile = Regex("dfr_lkm-(android\\d+-\\d+\\.\\d+)\\.ko")
        require("dirtyfrag/dfr_payload")
        val dfrPayload = dfrDir.resolve("dfr_payload")
        if (dfrPayload.isFile && dfrPayload.length() >= 4) {
            val magic = dfrPayload.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
            val elf = magic[0] == 0x7f.toByte() && magic[1] == 'E'.code.toByte() &&
                magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            if (!elf) problems += "dirtyfrag/dfr_payload: not an ELF (placeholder text rejected)"
        }
        val dfrKos = dfrDir.listFiles { f -> f.isFile && dfrKmiFile.matches(f.name) }?.sorted() ?: emptyList()
        if (dfrKos.isEmpty()) {
            problems += "missing: dirtyfrag/dfr_lkm-<kmi>.ko (dfr-scheme asset; at least one KMI, produced by the layer repo's pack-dirtyfrag.sh)"
        }
        dfrKos.forEach { ko ->
            expected += "dirtyfrag/${ko.name}"
            if (ko.length() == 0L) problems += "empty file: dirtyfrag/${ko.name}"
        }

        payloadsDir.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(payloadsDir).path.replace('\\', '/')
            if (rel !in expected) problems += "unexpected file: $rel (dir is the manifest; roles are fixed by PackRole)"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "verifyPayloads failed (${problems.size}):\n  - " + problems.joinToString("\n  - ") +
                    "\n  hint: preload/su_daemon come from the layer repo's `build/pack.sh`, ksud from the " +
                    "KernelSU repo's `build/build-kernelsu.sh`, ksud-next from the KernelSU-Next workspace " +
                    "`KernelSU-Next-snothin/out/`; strip after staging with build/scripts/strip-assets.ps1.",
            )
        }
        logger.lifecycle(
            "==> payload check passed: ${series.size} series (${series.joinToString { it.first }}), " +
                "${expected.size} artifacts (incl. shared ksud)",
        )
    }
}

configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

val bumpVersionCode by tasks.registering {
    val propsFile = project.rootProject.file("version.properties")
    doLast {
        val props = Properties()
        if (propsFile.exists()) {
            propsFile.inputStream().use { props.load(it) }
        }
        val next = (props.getProperty("versionCode")?.toIntOrNull() ?: 0) + 1
        props.setProperty("versionCode", next.toString())
        propsFile.outputStream().use { props.store(it, "auto-incremented by build") }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(bumpVersionCode, verifyPayloads)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.miuix.navigation3.ui)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.libadb.android)
    implementation(libs.conscrypt.android)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
