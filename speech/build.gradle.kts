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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.speech"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
            optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            optIn.add("androidx.compose.animation.ExperimentalAnimationApi")
            optIn.add("androidx.compose.animation.ExperimentalSharedTransitionApi")
            optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
            optIn.add("androidx.compose.foundation.layout.ExperimentalLayoutApi")
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
            optIn.add("kotlin.time.ExperimentalTime")
            optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
