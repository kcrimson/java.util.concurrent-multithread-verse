import com.github.erizo.gradle.JcstressTask

plugins {
    id("java")
    id("com.diffplug.spotless") version "6.25.0"
    id("io.github.reyerizo.gradle.jcstress") version "0.8.15"
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.7")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Use JUnit Platform for unit tests
    useJUnitPlatform()
}

jcstress {
    jcstressDependency = "org.openjdk.jcstress:jcstress-core:0.16"
    regexp = "pl\\.symentis\\.concurrent\\.pool\\.*"
}

tasks.build{
    dependsOn(tasks.jmhClasses)
}

spotless {
    java {
        // Exclude generated files
        targetExclude("build/**")

        // Apply Palantir's Java formatter
        palantirJavaFormat()

        // Remove unused imports
        removeUnusedImports()

        // Make sure every file has the following copyright header
        licenseHeaderFile("spotless.license.java") // You'll need to create this file

        // Apply specific formatting rules
        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
}
