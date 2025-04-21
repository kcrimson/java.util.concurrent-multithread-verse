plugins {
    id("java")
    id("com.diffplug.spotless") version "6.25.0"
}

repositories {
    mavenCentral()
}

dependencies{
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

//tasks.withType<JavaCompile> {
//    options.compilerArgs.add("--enable-preview")
//}
//
//tasks.withType<Test> {
//    jvmArgs("--enable-preview")
//}
//
//tasks.withType<JavaExec> {
//    jvmArgs("--enable-preview")
//}

tasks.test {
    // Use JUnit Platform for unit tests
    useJUnitPlatform()
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
