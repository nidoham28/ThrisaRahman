/**

# CORE AI OPERATING RULES

**HIGHEST PRIORITY: READ `AGENTS.md` FIRST.**

1. **Read First:** Before every response, task, execution, command, code change, or operation, read the relevant `AGENTS.md` file(s). Understand the project, your role, responsibilities, constraints, and instructions before proceeding.

2. **Never Assume:** Never blindly accept a task, execute commands, or make changes without understanding the context and requirements.

3. **Follow Instructions:** Treat `AGENTS.md` as the primary source of project-specific instructions. Follow all applicable rules strictly.

4. **Verify Before Acting:** Inspect relevant files, dependencies, and existing implementations before making decisions or changes.

5. **Preserve Context:** Never ignore existing architecture, conventions, workflows, or previous decisions.

6. **Execute Responsibly:** Avoid unnecessary changes, destructive operations, guesswork, and unrelated modifications.

7. **Verify Results:** After execution or modification, check the results and report any failures or unresolved issues honestly.

**GOLDEN RULE:** Understand first. Decide second. Execute third. Verify last. Never work blindly.

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
        versionCode = 107
        versionName = "0.0.8-surjo"

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
    implementation(project(":onnx"))

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
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
