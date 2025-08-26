plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.2.10" // Use your Kotlin version
}

android {
    namespace = "com.ities45.orion_navigation_bars"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.17.0")

    // AppCompat
    implementation("androidx.appcompat:appcompat:1.7.1")

    // Material Components
    implementation("com.google.android.material:material:1.12.0")

    // Activity KTX
    implementation("androidx.activity:activity-ktx:1.10.1")

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")

    // Android Instrumented Testing
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.github.Ali-Elmansoury"
            artifactId = "orion-navigation-bars"
            version = "1.0.9"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
