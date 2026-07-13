plugins {
    kotlin("jvm")
    `java-library`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.goterl:lazysodium-java:5.1.4")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    api("io.socket:socket.io-client:2.1.2") {
        exclude(group = "org.json", module = "json")
    }
    compileOnly("org.json:json:20250517")
    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}
