plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.eventease"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eventease"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        renderscriptTargetApi = 26
        renderscriptSupportModeEnabled = true
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.navigation.ui)
    implementation(libs.firebase.firestore)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.android.gms:play-services-location:21.1.0")
}

afterEvaluate {
    tasks.register<Javadoc>("generateJavadoc") {
        source = fileTree("src/main/java")
        destinationDir = file(project.rootDir.parentFile.parentFile.resolve("doc"))
        val androidJar = "${android.sdkDirectory}/platforms/${android.compileSdk}/android.jar"
        val variant = android.applicationVariants.first()
        classpath = files(androidJar) + variant.javaCompileProvider.get().classpath
        isFailOnError = false
        options {
            encoding = "UTF-8"
            (this as StandardJavadocDocletOptions).apply {
                windowTitle = "EventEase API Documentation"
                docTitle = "EventEase API Documentation"
                bottom = "Copyright © 2025"
                addStringOption("Xdoclint:none", "-quiet")
                addStringOption("charset", "UTF-8")
                addStringOption("docencoding", "UTF-8")
                addStringOption("Xmaxwarns", "0")
            }
        }
    }
}