plugins {
    java
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
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("com.dylibso.chicory:runtime:$chicoryVersion")
    compileOnly("com.google.code.gson:gson:2.11.0")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("com.dylibso.chicory:runtime:$chicoryVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.test {
    useJUnitPlatform()
    systemProperty("pasm.testWasm", providers.gradleProperty("pasmTestWasm").getOrElse(""))
}
