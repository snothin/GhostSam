import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.snothin.ghostsam.companion"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.snothin.ghostsam.companion"
        minSdk = 33
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
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

    packaging {
        dex {
            useLegacyPackaging = true
        }
        resources.excludes += "/META-INF/**/{LICENSE*,NOTICE*,README*}"
        resources.excludes += "/META-INF/**/{AL2.0,LGPL2.1}"
        resources.excludes += "kotlin/**/*.kotlin_builtins"
        resources.excludes += "kotlin/*.kotlin_builtins"
        resources.excludes += "META-INF/*.version"
        resources.excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
}

abstract class SyncDfrAssetsTask : DefaultTask() {
    @get:InputDirectory
    abstract val payloadsDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val src = payloadsDir.get().asFile
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        src.resolve("dirtyfrag").listFiles()
            ?.filter { it.isFile && it.name.startsWith("dfr_lkm-") && it.name.endsWith(".ko") }
            ?.forEach { ko ->
                ko.copyTo(
                    out.resolve("dfr/${ko.name}").apply { parentFile?.mkdirs() },
                    overwrite = true,
                )
            }
    }
}

val syncDfrAssets by tasks.registering(SyncDfrAssetsTask::class) {
    payloadsDir.set(layout.projectDirectory.dir("../app/src/main/assets/payloads"))
    outputDir.set(layout.buildDirectory.dir("dfr-assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncDfrAssets) { it.outputDir }
    }
}
