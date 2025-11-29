import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// Load API key from local.properties
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    FileInputStream(localPropertiesFile).use { stream ->
        localProperties.load(stream)
    }
}
val googleMapsApiKey = localProperties.getProperty("google.maps.api.key") ?: ""

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
        
        // Add Google Maps API key as a resource value (loaded from local.properties)
        resValue("string", "google_maps_api_key", googleMapsApiKey)
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
    
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = false
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Firebase BOM - must be declared before other Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    // Firebase dependencies (versions managed by BOM)
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
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