import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
}

// ── KCam fork configuration (see gradle.properties) ──
fun kcamProp(name: String, default: String = ""): String =
    (project.findProperty("kcam.$name") as String?)?.trim() ?: default
fun quoted(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val kcamAppName = kcamProp("appName", "KCam")
val kcamApplicationId = kcamProp("applicationId", "io.github.kauansilvestre.kcam")
val kcamEdition = kcamProp("edition", "personal").lowercase()
val kcamForkVersion = kcamProp("forkVersion", "1")

android {
    namespace = "com.fadcam"
    compileSdk = 36

    val isBundle = gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
    val isProBuild = gradle.startParameter.taskNames.any { it.lowercase().contains("pro") }

    splits {
        abi {
            // For pro builds: enable splits but only arm64-v8a (no universal)
            // For main builds: arm64-v8a + armeabi-v7a with universal APK
            isEnable = !isBundle
            reset()
            if (isProBuild) {
                include("arm64-v8a")
                isUniversalApk = false
            } else {
                include("armeabi-v7a", "arm64-v8a")
                isUniversalApk = true
            }
        }
    }

    defaultConfig {
        applicationId = kcamApplicationId
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 52
        versionName = "4.0.0"
        vectorDrawables.useSupportLibrary = true

        resValue("string", "app_name", kcamAppName)
        buildConfigField("String", "FORK_NAME", quoted(kcamAppName))
        buildConfigField("String", "FORK_VERSION", quoted(kcamForkVersion))
        buildConfigField("String", "UPSTREAM_COMMIT", quoted("e59f58edefdf622079f79c340c72fb30244eea9f"))
        buildConfigField("String", "EDITION", quoted(kcamEdition))
        buildConfigField("String", "UPDATE_REPO", quoted(kcamProp("updateRepo")))
        buildConfigField("String", "CHANGELOG_URL", quoted(kcamProp("changelogUrl")))
        buildConfigField("String", "REMOTE_NOTIFICATION_URL", quoted(kcamProp("remoteNotificationUrl")))
        buildConfigField("String", "CLOUD_SUPABASE_URL", quoted(kcamProp("cloud.supabaseUrl")))
        buildConfigField("String", "CLOUD_SUPABASE_KEY", quoted(kcamProp("cloud.supabaseKey")))
        buildConfigField("String", "CLOUD_RELAY_URL", quoted(kcamProp("cloud.relayUrl")))
        buildConfigField("String", "CLOUD_AUTH_URL", quoted(kcamProp("cloud.authUrl")))
        buildConfigField("String", "CLOUD_DASHBOARD_URL", quoted(kcamProp("cloud.dashboardUrl")))
        
        // Fix 16KB native library alignment for Android 15
        // Generate full native debug symbols so they can be uploaded to Play Console
        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream().use { stream ->
                stream?.let { props.load(it) }
            }
            val keystoreFile = props.getProperty("KEYSTORE_FILE", "")
            // Only set storeFile if keystore file path is provided and exists
            if (keystoreFile.isNotEmpty() && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = props.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = props.getProperty("KEY_ALIAS", "")
                keyPassword = props.getProperty("KEY_PASSWORD", "")
            }
        }
    }
    
    // Helper: check if release signing config is valid
    val releaseSigningConfigValid = signingConfigs.getByName("release").storeFile != null

    buildTypes {
        debug {
            applicationIdSuffix = ".beta"
            isDebuggable = true
            versionNameSuffix = "-beta10.6" // Increment the beta version suffix for each release. Use `beta1` for the first beta release, then `beta2`, etc.
            resValue("string", "app_name", "$kcamAppName Beta")
        }
        
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
            // KCam: sign only when a keystore is configured (CI secrets / local.properties);
            // otherwise produce an unsigned APK instead of failing the build.
            if (releaseSigningConfigValid) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        
        create("pro") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".pro"
            isDebuggable = false
            if (releaseSigningConfigValid) {
                signingConfig = signingConfigs.getByName("release")
            }
            versionNameSuffix = "-Pro"
        }
        
        create("proPlus") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".proplus"
            isDebuggable = false
            if (releaseSigningConfigValid) {
                signingConfig = signingConfigs.getByName("release")
            }
            versionNameSuffix = "-Pro+"
            // Custom app name via gradle property
            val customAppName = project.findProperty("customAppName")?.toString() ?: "$kcamAppName Pro+"
            resValue("string", "app_name", customAppName)
        }
    }

    flavorDimensions += "pro"

    // Sentinela ships a single flavor. The disguised "Notes/Calculator/Weather"
    // variants from upstream were removed on purpose: a personal safety recorder
    // has no use for an app that hides as something else, and it kept the app
    // minimal. The "default" flavor is kept (not removed) so the Gradle task
    // names stay assembleDefault*/testDefault* that the CI workflow calls.
    productFlavors {
        create("default") {
            dimension = "pro"
        }
    }

// ./gradlew assembleDefaultProPlusRelease -PcustomAppName="Custom Name" - Pro+ custom build (standalone)

    // Variant filter: the only flavor is "default"; drop its plain "pro" build
    // type (we ship debug, release and proPlus). Modern API — the old
    // variantFilter{} is deprecated since AGP 8.x.
    androidComponents {
        beforeVariants { variant ->
            if (variant.name.endsWith("Pro") && !variant.name.endsWith("ProPlus")) {
                variant.enable = false
            }
        }
    }

    // Dynamic APK output names: Sentinela_v<versionName><suffix>-<abi>.apk
    // (universal APK gets the literal "-universal")
    applicationVariants.all {
        val versionName = "${defaultConfig.versionName}${buildType.versionNameSuffix.orEmpty()}"
        val flavor = if (flavorName != "default") "${flavorName}_" else ""
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiType = output.filters
                .firstOrNull { it.filterType == "ABI" }
                ?.identifier
                ?: "universal"
            output.outputFileName =
                "${kcamAppName}_${flavor}v${versionName}-${abiType}.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Robolectric provides Android framework classes on the JVM.
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets {
        getByName("main") {
            java.srcDir("libs/AppLockLibrary/src/main/java")
            res.srcDir("libs/AppLockLibrary/src/main/res")
        }
        // NOTE: Removed setSrcDirs(emptyList()) to enable test source detection
        // getByName("test").java.setSrcDirs(emptyList<String>())
        // getByName("androidTest").java.setSrcDirs(emptyList<String>())
        
    }

    packaging {
        jniLibs {
            excludes += listOf("**/x86/**", "**/x86_64/**", "**/mips/**", "**/mips64/**")
            // OpenCV and ffmpeg-kit both bundle libc++_shared.so. Keep one copy.
            pickFirsts += listOf("**/libc++_shared.so")
            // Enable 16KB page size alignment for Android 15 compatibility
            useLegacyPackaging = false
        }
        resources {
            excludes += listOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "**/*.kotlin_metadata",
                "**/*.kotlin_builtins",
                "**/*.proto",
                "assets/PSDs/**"  // Exclude PSD source files from release APK
            )
        }
    }

    androidResources {
        noCompress.add("xml")
        additionalParameters.add("--no-version-vectors")
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appintro.v631)
    implementation(libs.appcompat)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.core)
    implementation(libs.camerax.extensions)
    implementation(libs.camerax.view)
    implementation(libs.zxing.android.embedded)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.constraintlayout)
    implementation(libs.gridlayout)
    implementation(libs.core.ktx)
    // Media3 ExoPlayer for playback (replacing deprecated exoplayer2)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    // Media3 Transformer + Effect for Faditor Mini video editing
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    // AndroidX Media for MediaStyle notifications
    implementation(libs.media)
    implementation(libs.glide)
    implementation(libs.gson)
    implementation(libs.lottie)
    implementation(libs.material)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.okhttp)
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.task.vision) {
        exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
    }
    implementation(libs.opencv.android)
    implementation(libs.osmdroid.android)
    implementation(libs.osmdroid.wms)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.core.splashscreen)
    implementation(libs.documentfile)
    implementation(libs.localbroadcastmanager)
    implementation(libs.room.runtime)
    
    // Media3 for fragmented MP4 muxing (patched for live streaming via composite build)
    implementation(libs.media3.muxer)
    implementation(libs.media3.common)
    implementation(libs.media3.container)
    
    // NanoHTTPD for HTTP streaming server
    implementation(libs.nanohttpd.core)
    
    // MP4Parser for reliable MP4 box structure parsing
    implementation("com.googlecode.mp4parser:isoparser:1.1.22")

    annotationProcessor(libs.compiler)
    annotationProcessor(libs.room.compiler)

    implementation(mapOf("name" to "ffmpeg-kit-full-6.0-2.LTS", "ext" to "aar"))
    implementation(libs.smart.exception.java)
    implementation(fileTree(mapOf("dir" to "libs/aar", "include" to listOf("*.aar"))))

    // Unit Testing Dependencies (Local JVM tests - fast, no device needed)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.json:json:20240303")

    // Android Instrumented Testing (runs on device/emulator)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
