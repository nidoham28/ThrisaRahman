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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "me.rerere.terminal"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    testOptions {
        // 上游单测会碰到 android.util.Base64 等 Android 类, 沿用上游的设置让它们返回默认值
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
