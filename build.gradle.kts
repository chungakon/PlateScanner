// Top-level build file. Plugin versions are declared in gradle/libs.versions.toml
// and applied to sub-projects via the `plugins { ... }` block.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
