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
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.search"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
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

// Use desktop native binaries when executing local JVM tests.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("io.github.dokar3:quickjs-kt-android"))
            .using(module("io.github.dokar3:quickjs-kt-jvm:${libs.versions.quickjs.get()}"))
    }
}

dependencies {
    implementation(project(":ai"))
    implementation(project(":common"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    api(libs.jsoup)
    implementation(libs.quickjs)
    testImplementation(libs.junit)
}
