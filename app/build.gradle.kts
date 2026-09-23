import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Signing material lives outside the repository. local.properties is the primary source: it is
// already git-ignored, Gradle already reads it for sdk.dir, and Android Studio and the command line
// see the same values without a shell profile. Environment variables are the fallback so a future
// CI machine, which has no local.properties, can supply the same four values unchanged.
val signingProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

/** Reads one piece of signing material from local.properties, falling back to the environment. */
fun signingSecret(propertyKey: String, environmentKey: String): String? =
    signingProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentKey)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.elinacn.subtrack"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.elinacn.subtrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Created only when all four values are present and the keystore really exists. Without it
        // the release build stays unsigned instead of failing, and the debug build is untouched
        // because it keeps using the SDK's own debug key.
        val storeFilePath = signingSecret("subtrack.storeFile", "SUBTRACK_STORE_FILE")
        val storePasswordValue = signingSecret("subtrack.storePassword", "SUBTRACK_STORE_PASSWORD")
        val keyAliasValue = signingSecret("subtrack.keyAlias", "SUBTRACK_KEY_ALIAS")
        val keyPasswordValue = signingSecret("subtrack.keyPassword", "SUBTRACK_KEY_PASSWORD")
        val keystore = storeFilePath?.let(::file)
        if (keystore?.exists() == true &&
            storePasswordValue != null &&
            keyAliasValue != null &&
            keyPasswordValue != null
        ) {
            create("release") {
                storeFile = keystore
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            // Null when the keystore is absent; the APK then comes out unsigned rather than signed
            // with the debug key, so an unsigned artifact can never be mistaken for a shippable one.
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time arrived in API 26 and minSdk is 24; without this the app compiles cleanly and
        // then throws NoClassDefFoundError on Android 7.x. See ARCHITECTURE section 17.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // The app speaks two languages; its libraries speak dozens. Material3's date picker and
        // the AndroidX strings it pulls in ship translations for every locale Google supports, so
        // a German device used to get a German date picker inside an otherwise English app. This
        // drops every library locale that is not one of ours, which also shrinks the bundle.
        // `defaultConfig.resourceConfigurations` and `resConfigs()` are the old spelling and are
        // deprecated in AGP 9 in favour of this one; CLAUDE.md section 4 bans deprecated APIs.
        localeFilters += listOf("en", "tr")
    }
    bundle {
        language {
            // Once the filter above has run, the only locale-qualified resources left are our own
            // Turkish strings - tens of kilobytes, not megabytes. Splitting them off buys almost
            // nothing and costs correctness: a language split is delivered for the locales the
            // device had at install time, so a user who adds Turkish afterwards keeps seeing
            // English until Play sends the extra split. Packaging both languages in the base means
            // the device's own resource resolution picks the right one the moment the setting
            // changes, with nothing to download. See ARCHITECTURE section 28.
            enableSplit = false
        }
    }
}

ksp {
    // Where Room writes the JSON description of each schema version. Needed because the database
    // sets exportSchema = true; see SubTrackDatabase for why.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    // Generates the worker factory entries. Dagger's own hilt-android-compiler stays: this one
    // only knows about the androidx integrations, not about @HiltAndroidApp or @HiltViewModel.
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}