plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin on purpose: the rule engine must never depend on Android, so it can be tested in milliseconds
// on the JVM and can never leak a Context, a Log or a network call into the decision logic.
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
