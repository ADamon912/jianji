import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseCredentialsFile = rootProject.file("keystore.properties")
val releaseCredentials = Properties().apply {
    if (releaseCredentialsFile.isFile) releaseCredentialsFile.inputStream().use { load(it) }
}
val validateReleaseCredentials = tasks.register("validateReleaseCredentials") {
    doLast {
        check(releaseCredentialsFile.isFile) { "Release signing requires local keystore.properties. See docs/发布说明-1.0.0.md." }
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword").forEach {
            check(!releaseCredentials.getProperty(it).isNullOrBlank()) { "Missing release signing property: $it" }
        }
        check(rootProject.file(releaseCredentials.getProperty("storeFile")).isFile) { "Release keystore does not exist." }
    }
}

android {
    namespace = "com.example.jianji"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.jianji"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.0"
    }

    signingConfigs {
        create("release") {
            if (releaseCredentialsFile.isFile) {
                storeFile = releaseCredentials.getProperty("storeFile")?.let { rootProject.file(it) }
                storePassword = releaseCredentials.getProperty("storePassword")
                keyAlias = releaseCredentials.getProperty("keyAlias")
                keyPassword = releaseCredentials.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("maven.repo.local", rootProject.file(".tools/robolectric-maven").absolutePath)
        }
    }
    lint {
        abortOnError = true
        warningsAsErrors = true
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseCredentials)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.androidx.room.compiler)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

