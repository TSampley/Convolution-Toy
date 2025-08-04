
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

android {
    compileSdk = 36
    defaultConfig {
        namespace =  "com.taushsampley.convolution"
        minSdk = 24
//        versionCode = 1
//        versionName = "1.0"
//        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
//            minifyEnabled = true
//            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
        debug {
//            applicationIdSuffix = ".debug"
//            debuggable = true
//            testCoverageEnabled true
        }
    }
    testOptions {
//        unitTests.returnDefaultValues = true
    }
    dataBinding {
//        enabled = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")

    implementation("androidx.constraintlayout:constraintlayout:1.1.3")

    implementation("com.google.android.material:material:1.0.0")

    implementation("androidx.core:core-ktx:1.1.0")

    testImplementation("junit:junit:4.12")
    testImplementation("androidx.core:core:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test:rules:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}
