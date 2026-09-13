import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    java
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        bundledPlugin("org.jetbrains.plugins.textmate")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
kotlin { jvmToolchain(21) }

intellijPlatform {
    pluginConfiguration {
        name = "WGSL Support"
        version = project.version.toString()
        ideaVersion { sinceBuild = providers.gradleProperty("pluginSinceBuild") }
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            markdownToHTML(it.substringAfter("<!-- Plugin description -->").substringBefore("<!-- Plugin description end -->").trim())
        }
        changeNotes = provider {
            changelog.renderItem((changelog.getOrNull(project.version.toString()) ?: changelog.getUnreleased()).withHeader(false), Changelog.OutputType.HTML)
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf(project.version.toString().substringAfter('-', "default").substringBefore('.'))
    }
    pluginVerification {
        ides { current() }
    }
}

changelog { groups.empty() }

// TextMate reads bundles from disk, outside the plugin JAR.
tasks.withType<PrepareSandboxTask>().configureEach {
    from("textmate") { into("${project.name}/textmate") }
}

tasks.wrapper { gradleVersion = "9.7.1" }

tasks.test {
    providers.gradleProperty("wgslServerArchive").orNull?.let { systemProperty("wgsl.server.test.archive", it) }
}
