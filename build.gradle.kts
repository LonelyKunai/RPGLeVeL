plugins {
    `java-library`
}

group = "com.antigravity"
version = "1.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Compile-time stubs for optional plugins (PlaceholderAPI); not included in the jar
sourceSets {
    create("stubs") {
        java.srcDir("stubs")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    "stubsCompileOnly"("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly(sourceSets["stubs"].output)
    compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    compileOnly("net.kyori:adventure-api:5.2.0")
    compileOnly("net.kyori:adventure-text-minimessage:5.2.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:5.2.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

