/*
 * Módulo "main" do ics-openvpn (OpenVPN for Android, Arne Schwabe, GPLv2) compilado como
 * biblioteca para o RTecVPN. Baseado no main/build.gradle.kts original; só tira o que é
 * exclusivo de app (assinatura, splits, bundle) e fixa o sabor skeleton + OpenVPN 2.
 */
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

plugins {
    id("com.android.library")
}

android {
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    namespace = "de.blinkt.openvpn"
    compileSdk = 37
    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 23
        // O RTecVPN compila com um SDK menor; as APIs novas são usadas só com checagem de versão
        aarMetadata { minCompileSdk = 24 }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("build/ovpnassets")
        }
        create("skeleton") {}
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    flavorDimensions += listOf("implementation", "ovpnimpl")

    productFlavors {
        create("skeleton") {
            dimension = "implementation"
        }
        create("ovpn2") {
            dimension = "ovpnimpl"
            buildConfigField("boolean", "openvpn3", "false")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
}

// O código Java do OpenVPN 3 (gerado pelo swig) entra em todos os sabores, como no original
var swigcmd = "swig"
if (file("/opt/homebrew/bin/swig").exists()) swigcmd = "/opt/homebrew/bin/swig"
else if (file("/usr/local/bin/swig").exists()) swigcmd = "/usr/local/bin/swig"

abstract class GenerateSwigTask : Exec() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

fun registerGenSwigTask(variantName: String, variantDirName: String): TaskProvider<GenerateSwigTask> {
    val baseDir = layout.buildDirectory.dir("generated/source/ovpn3swig/${variantDirName}")
    return tasks.register<GenerateSwigTask>("generateOpenVPN3Swig${variantName}") {
        val genDir = baseDir.get().asFile.resolve("net/openvpn/ovpn3")
        outputDir.set(baseDir)
        doFirst { mkdir(genDir) }
        commandLine(
            listOf(
                swigcmd, "-outdir", genDir.absolutePath, "-outcurrentdir", "-c++", "-java",
                "-package", "net.openvpn.ovpn3",
                "-Isrc/main/cpp/openvpn3/client", "-Isrc/main/cpp/openvpn3/",
                "-DOPENVPN_PLATFORM_ANDROID",
                "-o", "${genDir}/ovpncli_wrap.cxx", "-oh", "${genDir}/ovpncli_wrap.h",
                "src/main/cpp/openvpn3/client/ovpncli.i"
            )
        )
        inputs.files("src/main/cpp/openvpn3/client/ovpncli.i")
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val execTask = registerGenSwigTask(variant.name, variant.name.replace("-", "/"))
        variant.sources.java?.addGeneratedSourceDirectory(execTask, GenerateSwigTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
}
