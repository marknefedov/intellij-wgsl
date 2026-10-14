import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    java
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Newest IntelliJ IDEA EAP build (e.g. "263.6800.24"), whichever release it belongs to.
tasks.printProductsReleases {
    types = listOf(IntelliJPlatformType.IntellijIdea)
    channels = listOf(ProductRelease.Channel.EAP)
    sinceBuild = providers.gradleProperty("pluginSinceBuild")
}
val latestEap = tasks.printProductsReleases.flatMap { it.productsReleases }.map { releases ->
    releases.map { it.substringAfter('-') }.maxWithOrNull { a, b ->
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        x.zip(y).map { (i, j) -> i.compareTo(j) }.firstOrNull { it != 0 } ?: x.size.compareTo(y.size)
    } ?: error("No IntelliJ IDEA EAP build found")
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // -PplatformVersion=eap builds and tests against the newest EAP.
        intellijIdea(providers.gradleProperty("platformVersion").flatMap { if (it == "eap") latestEap else provider { it } })
        bundledPlugin("org.jetbrains.plugins.textmate")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "WGSL / WESL"
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
        ides {
            current()
            // Catch API breaks in the next platform release before it ships.
            create(IntelliJPlatformType.IntellijIdea, latestEap)
        }
    }
}

changelog { groups.empty() }

// TextMate reads bundles from disk, outside the plugin JAR.
tasks.withType<PrepareSandboxTask>().configureEach {
    from("textmate") { into("${project.name}/textmate") }
}

tasks.named<PrepareSandboxTask>("prepareTestSandbox") {
    // The test runner shares a core classloader: Ultimate's obfuscated startup
    // class collides with an unrelated class in product-backend.jar (IDEA 2026.2.2).
    // Keep this exclusion in the test sandbox; TextMate and LSP remain enabled.
    disabledPlugins.add("com.intellij.modules.ultimate")
}

tasks.wrapper { gradleVersion = "9.7.1" }

tasks.test {
    providers.gradleProperty("wgslServerArchive").orNull?.let { systemProperty("wgsl.server.test.archive", it) }
}

tasks.processTestResources {
    from("textmate") { into("textmate") }
}
