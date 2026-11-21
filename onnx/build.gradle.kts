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
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.k2fsa.sherpa.onnx"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

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

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}