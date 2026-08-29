import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

abstract class StageDictionaryPackAssets @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packFiles: ConfigurableFileCollection

    @get:Input
    abstract val expectedPackCount: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val inputs = packFiles.files.filter { it.isFile && it.extension == "dictpack" }
        val expected = expectedPackCount.get()
        check(inputs.size == expected) {
            "Expected $expected debug dictionary packs, found ${inputs.size}: " +
                inputs.joinToString { it.absolutePath }
        }
        fileSystemOperations.sync {
            from(inputs) {
                into("bundled-dictionary-packs")
            }
            duplicatesStrategy = DuplicatesStrategy.FAIL
            into(outputDirectory)
        }
    }

}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val bundleDictionaryPacksInDebug =
    localProperties.getProperty("bundleDictionaryPacksInDebug")?.toBooleanStrictOrNull() == true
val dictionaryDatasetRoot = System.getenv("LANG_DATABASE_DIR")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: localProperties.getProperty("dictionaryDataDir")?.trim()?.takeIf(String::isNotEmpty)
    ?: rootProject.file(".local/dictionary-data").absolutePath
val debugKaikkiPackLanguages = localProperties.getProperty("debugDictionaryPackLanguages")
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.distinct()
    .orEmpty()
val debugDictionaryPackAssets = layout.buildDirectory.dir("generated/debugDictionaryPackAssets")
val dictionaryDatasets = listOf("cc-cedict", "korean-basic", "panlex", "jmdict")
val debugKaikkiMorphologyPackCount = if (debugKaikkiPackLanguages.isEmpty()) 0 else 1

val buildDebugDictionaryPacks by tasks.registering(Exec::class) {
    onlyIf { bundleDictionaryPacksInDebug }
    group = "dictionary packs"
    description = "Builds local dictionary packs for the developer debug APK."
    workingDir(rootProject.projectDir)
    commandLine(buildList {
        val pythonCommand = if (
            System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        ) {
            "python"
        } else {
            "python3"
        }
        add(pythonCommand)
        addAll(listOf("-m", "tools.build_dictionary_packs"))
        listOf("cc-cedict", "korean-basic", "panlex", "jmdict").forEach { dataset ->
            addAll(listOf("--pack", dataset))
        }
        if (debugKaikkiPackLanguages.isNotEmpty()) {
            addAll(listOf("--pack", "kaikki"))
            debugKaikkiPackLanguages.forEach { language ->
                addAll(listOf("--kaikki-language", language))
            }
        }
    })
}

val stageDebugDictionaryPackAssets by tasks.registering(StageDictionaryPackAssets::class) {
    group = "dictionary packs"
    description = "Stages configured local dictionary packs as debug-only APK assets."
    if (bundleDictionaryPacksInDebug) {
        dependsOn(buildDebugDictionaryPacks)
        expectedPackCount.set(4 + debugKaikkiPackLanguages.size + debugKaikkiMorphologyPackCount)
        dictionaryDatasets.forEach { dataset ->
            packFiles.from(
                rootProject.fileTree("$dictionaryDatasetRoot/$dataset/packs") {
                    include("*.dictpack")
                },
            )
        }
        debugKaikkiPackLanguages.forEach { language ->
            packFiles.from(
                rootProject.fileTree("$dictionaryDatasetRoot/kaikki/packs") {
                    include("kaikki.$language-en-*.dictpack")
                },
            )
        }
        if (debugKaikkiPackLanguages.isNotEmpty()) {
            packFiles.from(
                rootProject.fileTree("$dictionaryDatasetRoot/kaikki/packs") {
                    include("kaikki.en-morphology-*.dictpack")
                },
            )
        }
    } else {
        expectedPackCount.set(0)
    }
    outputDirectory.set(debugDictionaryPackAssets)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.localvocabulary"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.localvocabulary"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        // Runtime datasets are installed as dictionary packs, never bundled in the base APK.
        getByName("main").assets.setSrcDirs(listOf("src/main/pack-metadata"))
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        if (bundleDictionaryPacksInDebug) {
            variant.sources.assets?.addGeneratedSourceDirectory(
                stageDebugDictionaryPackAssets,
                StageDictionaryPackAssets::outputDirectory,
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mlkit.digital.ink.recognition)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.compose.ui.tooling)
}
