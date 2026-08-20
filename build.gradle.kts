// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Hilt is deliberately not declared here. Its Gradle plugin looks up the KSP task class, and
    // declaring the two in different scopes puts them on different class loaders - the lookup then
    // fails at configuration time (dagger#3965). KSP lives in :app, so Hilt does too.
}