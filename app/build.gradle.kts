plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

val releaseStoreFile = providers.gradleProperty("NEBULA_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("NEBULA_RELEASE_STORE_FILE")).orNull
val releaseStorePassword = providers.gradleProperty("NEBULA_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("NEBULA_RELEASE_STORE_PASSWORD")).orNull
val releaseKeyAlias = providers.gradleProperty("NEBULA_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("NEBULA_RELEASE_KEY_ALIAS")).orNull
val releaseKeyPassword = providers.gradleProperty("NEBULA_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("NEBULA_RELEASE_KEY_PASSWORD")).orNull
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.nebula.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nebula.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.configureEach {
    val isReleasePackagingTask =
        (name.startsWith("assemble") || name.startsWith("bundle") || name.startsWith("package")) &&
            name.endsWith("Release")
    if (isReleasePackagingTask) {
        doFirst {
            check(hasReleaseSigning) {
                "Release signing is not configured. Supply NEBULA_RELEASE_STORE_FILE, " +
                    "NEBULA_RELEASE_STORE_PASSWORD, NEBULA_RELEASE_KEY_ALIAS, and " +
                    "NEBULA_RELEASE_KEY_PASSWORD through Gradle properties or environment variables."
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    // Firebase & Google Authentication
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.socket.io.client)

    // Direct media playback (HLS/MP4) for rooms that do not host on YouTube.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    // Instrumentation: Compose UI rules plus the AndroidX JUnit/Espresso runners.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    debugImplementation(libs.androidx.ui.tooling)

    // Pure JVM specs for the logic that decides behaviour (media routing, invite
    // parsing, drift correction) so they run without a device.
    testImplementation("junit:junit:4.13.2")
}

tasks.register("cleanStaleDuplicates") {
    doFirst {
        fileTree(layout.buildDirectory).matching {
            include("**/* 2*")
            include("**/* 3*")
        }.forEach { it.delete() }
    }
}
tasks.matching { it.name.startsWith("merge") || it.name.startsWith("process") || it.name.startsWith("package") }.configureEach {
    dependsOn("cleanStaleDuplicates")
}
