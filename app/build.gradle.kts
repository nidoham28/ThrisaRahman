/** MODULE ACCESS RULE (STRICT)

- EDITABLE: only the `app/` module. Every new file, fix, refactor, or any
change must happen inside `app/` and nowhere else.
- READ-ONLY: every other module — `:ai`, `:common`, `:speech`, `:terminal`,
and any module added in the future. You may read their code and call
their public APIs, but NEVER create, edit, rename, or delete any file
inside them, including build.gradle.kts, manifests, resources, or configs.
- If a task seems to require changing a read-only module, do NOT touch it.
Solve it inside `app/` only, or stop and ask for explicit permission.
- Before finishing, verify every changed file path starts with `app/`.
Revert anything outside it.

 **/

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.naira.assistant"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.naira.assistant"
        minSdk = 26
        targetSdk = 37
        versionCode = 103
        versionName = "0.0.3-meghdoot"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                // সাবধান: packageScope দিলে শুধু এই প্যাকেজগুলোই optimize হয়,
                // তোমার নিজের com.naira.** কোড বাদ পড়ে। পুরো অ্যাপ চাইলে লাইনটা সরাও।
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Ktor-এর META-INF ডুপ্লিকেট ক্ল্যাশ ঠেকায়
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ai"))
    implementation(project(":common"))
    implementation(project(":speech"))
    implementation(project(":terminal"))

    implementation(libs.newpipeextractor)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.google.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.kotlinx.coroutines.play.services)

    // এটা মিসিং ছিল — @Serializable চালাতে runtime লাগে
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}