plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("androidx.room")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val prepareInsightLicenses by tasks.registering(Copy::class) {
    from("src/main/cpp/LLAMA-LICENSE.txt", "../insight_model/QWEN-LICENSE.txt")
    into(layout.buildDirectory.dir("generated/insightLicenses"))
}
tasks.named("preBuild") { dependsOn(prepareInsightLicenses) }

android {
    namespace = "com.nagopy.android.foldlytics"
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    assetPacks += listOf(":insight_model")

    defaultConfig {
        applicationId = "com.nagopy.android.foldlytics"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Foldlytics (Debug)")
        }
        release {
            optimization {
                enable = true
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    // Standalone debug APKs may include the fixed model for offline device tests.
    // Store bundles deliver it in the install-time asset pack instead of the base APK.
    if (providers.gradleProperty("includeInsightModelInApk").orNull == "true") {
        sourceSets.getByName("debug").assets.srcDir("../insight_model/src/main/assets")
    }
    androidResources { noCompress += "gguf" }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/insightLicenses").get().asFile)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val roomVersion = "2.8.4"
    val workVersion = "2.11.2"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:$workVersion")
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.1")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    // Room testing 2.8.4 loads schemas with kotlinx.serialization 1.8.1 in the target process.
    debugImplementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
