@file:Suppress("UnstableApiUsage")

import build.CompilerRelocatedJarConfigurator
import org.jetbrains.gradle.shadow.registerShadowJarTasksBy
import org.jetbrains.kotlinx.publisher.composeOfTaskOutputs


plugins {
    kotlin("libs.publisher")
    kotlin("jvm")
    kotlin("kapt")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

val springJvmTarget = 17

buildSettings {
    withJvmTarget(springJvmTarget.toString())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(springJvmTarget))
    }
}

val springKernel: Configuration by configurations.creating

dependencies {
    kapt(libs.spring.boot.configuration.processor)

    implementation(projects.kotlinJupyterKernel)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.autoconfigure)

    springKernel(projects.kotlinJupyterKernel) { isTransitive = false }
    springKernel(projects.api) { isTransitive = false }
    springKernel(projects.lib) { isTransitive = false }
    springKernel(projects.commonDependencies) { isTransitive = false }
    springKernel(projects.sharedCompiler) { isTransitive = false }
    springKernel(projects.springStarter) { isTransitive = false }

    springKernel(libs.kotlin.dev.scriptingDependencies) { isTransitive = false }
    springKernel(libs.kotlin.dev.scriptingDependenciesMavenAll) { isTransitive = false }
    springKernel(libs.kotlin.dev.scriptingIdeServices) { isTransitive = false }
    springKernel(libs.kotlin.dev.scriptingCompilerImplEmbeddable) { isTransitive = false }
    springKernel(libs.kotlin.dev.scriptingCompilerEmbeddable) { isTransitive = false }
    springKernel(libs.kotlin.dev.compilerEmbeddable)
    springKernel(libs.kotlin.dev.scriptRuntime) { isTransitive = false }
    springKernel(libs.serialization.dev.embeddedPlugin) { isTransitive = false }

    springKernel(libs.coroutines.core) { isTransitive = false }

    springKernel(libs.kotlin.dev.scriptingJvm) { isTransitive = false }
    springKernel(libs.kotlin.dev.scriptingCommon) { isTransitive = false }

    springKernel(libs.zeromq)
    springKernel(libs.serialization.json)
}

configurations.matching { it.name.startsWith("dokka") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("com.fasterxml.jackson")) {
            useVersion("2.15.3")
        }
    }
}

val springKernelJar =
    tasks.registerShadowJarTasksBy(
        springKernel,
        withSources = true,
        binaryTaskConfigurator = CompilerRelocatedJarConfigurator,
    )

kotlinPublications {
    publication {
        publicationName.set("spring-starter")
        description.set("Interactive Jupyter-style console for Spring applications")
        composeOfTaskOutputs(springKernelJar)
    }
}