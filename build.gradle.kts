// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.aboutLibraries) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.diffplug.spotless") version "8.1.0"
    id("com.google.devtools.ksp") version "2.3.3" apply false
}

spotless {
    kotlin {
        target("**/*.kt") // Apply to all `.kt` files recursively
        targetExclude("**/build/**", "**/generated/**") // Skip generated/build files

        ktlint()
//            .editorConfigOverride(
//                mapOf(
//                    "ktlint_standard_no-wildcard-imports" to "disabled",
//                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Test",
//                ),
//            )

        trimTrailingWhitespace() // Remove trailing spaces
        endWithNewline() // Ensure files end with newline
        leadingTabsToSpaces(4) // Use 4 spaces for indentation
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        leadingTabsToSpaces(4)
        endWithNewline()
    }

    format("misc") {
        target("**/*.md", "**/.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    toolVersion = "1.23.8"
    config.setFrom(file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        md.required.set(true)

        txt.required.set(false)
        sarif.required.set(false)
//        checkstyle.required.set(false)
        xml.required.set(false)
    }
}
