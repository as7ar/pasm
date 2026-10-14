plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = "kr.astar"
version = "0.1.0"

val paperApiVersion = providers.gradleProperty("paperApiVersion").getOrElse("26.1.2.build.+")
val chicoryVersion = "1.7.5"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion"
    compileOnly("com.dylibso.chicory:runtime:$chicoryVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
