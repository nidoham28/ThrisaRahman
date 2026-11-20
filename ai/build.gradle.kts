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
    namespace = "me.rerere.ai"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
        // 请求体日志只在调试构建里打印，靠 BuildConfig.DEBUG 判断
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.add("kotlin.uuid.ExperimentalUuidApi")
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }
}

dependencies {
    implementation(project(":common"))

    // Compose
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)

    // okhttp
    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.okhttp.logging)

    // kotlinx
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
