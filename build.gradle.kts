plugins {
    java
}

group = "dev.randomizer"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi: String by project
val javaVer: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(javaVer.toInt())
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
