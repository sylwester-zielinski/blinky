plugins {
    alias(libs.plugins.nordic.application.compose)
    alias(libs.plugins.nordic.hilt)
}

group = "no.nordicsemi.android.kotlin.ble"

android {
    namespace = "no.nordicsemi.android.kotlin.ble.app.mock"
}

dependencies {
    implementation(project(":shared"))

    kapt(libs.hilt.compiler)
    implementation(libs.hilt.android)

    implementation(libs.nordic.blek.scanner)

    implementation(libs.nordic.theme)
    implementation(libs.nordic.navigation)
    implementation(libs.nordic.permissions.ble)
    implementation(libs.nordic.logger)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.iconsExtended)
}
