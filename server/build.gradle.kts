plugins {
    id("Entropy.kotlin-common-conventions")
    id("com.ncorti.ktfmt.gradle") version "0.15.1"
    application
}

ktfmt { kotlinLangStyle() }

dependencies {
    implementation(project(":core"))
    testImplementation(project(":test-core"))
}

application {
    // Define the main class for the application.
    mainClass.set("server.EntropyServer")
}
