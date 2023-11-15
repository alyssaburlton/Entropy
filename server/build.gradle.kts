plugins {
    id("Entropy.kotlin-common-conventions")
    application
}

dependencies {
    implementation("com.jgoodies:jgoodies-forms:1.6.0")
    implementation("com.miglayout:miglayout-swing:5.2")
    implementation("javax.mail:javax.mail-api:1.6.2")
    implementation(project(":core"))
}

application {
    // Define the main class for the application.
    mainClass.set("server.EntropyServer")
}
