plugins {
    kotlin("jvm") version "2.3.0"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.goterl:lazysodium-java:5.1.4")
    implementation("net.java.dev.jna:jna:5.14.0")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20250517")
}

tasks.test {
    useJUnitPlatform()
}
