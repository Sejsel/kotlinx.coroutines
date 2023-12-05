/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.*
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.jvm.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.*
import java.net.*

// Pom configuration

fun MavenPom.configureMavenCentralMetadata(project: Project) {
    name by project.name
    description by "Coroutines support libraries for Kotlin"
    url by "https://github.com/Kotlin/kotlinx.coroutines"

    licenses {
        license {
            name by "The Apache Software License, Version 2.0"
            url by "https://www.apache.org/licenses/LICENSE-2.0.txt"
            distribution by "repo"
        }
    }

    developers {
        developer {
            id by "JetBrains"
            name by "JetBrains Team"
            organization by "JetBrains"
            organizationUrl by "https://www.jetbrains.com"
        }
    }

    scm {
        url by "https://github.com/Kotlin/kotlinx.coroutines"
    }
}

fun mavenRepositoryUri(): URI {
    val repositoryId: String? = System.getenv("libs.repository.id")
    return if (repositoryId == null) {
        URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
    } else {
        URI("https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId")
    }
}

fun configureMavenPublication(rh: RepositoryHandler, project: Project) {
    rh.maven {
        url = mavenRepositoryUri()
        credentials {
            username = project.getSensitiveProperty("libs.sonatype.user")
            password = project.getSensitiveProperty("libs.sonatype.password")
        }
    }

    // Something that's easy to "clean" for development, not mavenLocal
    rh.maven("${project.rootProject.buildDir}/repo") {
        name = "buildRepo"
    }
}

fun signPublicationIfKeyPresent(project: Project, publication: MavenPublication) {
    val keyId = project.getSensitiveProperty("libs.sign.key.id")
    val signingKey = project.getSensitiveProperty("libs.sign.key.private")
    val signingKeyPassphrase = project.getSensitiveProperty("libs.sign.passphrase")
    if (!signingKey.isNullOrBlank()) {
        project.extensions.configure<SigningExtension>("signing") {
            useInMemoryPgpKeys(keyId, signingKey, signingKeyPassphrase)
            sign(publication)
        }
    }
}

private fun Project.getSensitiveProperty(name: String): String? {
    return project.findProperty(name) as? String ?: System.getenv(name)
}

/**
 * This unbelievable piece of engineering^W programming is a workaround for the following issues:
 * - https://github.com/gradle/gradle/issues/26132
 * - https://youtrack.jetbrains.com/issue/KT-61313/
 *
 * Long story short:
 * 1) Single module produces multiple publications
 * 2) 'Sign' plugin signs them
 * 3) Signature files are re-used, which Gradle detects and whines about an implicit dependency
 *
 * There are three patterns that we workaround:
 * 1) 'Sign' does not depend on 'publish'
 * 2) Empty 'javadoc.jar.asc' got reused between publications (kind of a implication of the previous one)
 * 3) `klib` signatures are reused where appropriate
 *
 * It addresses the following failures:
 * ```
 * Gradle detected a problem with the following location: 'kotlinx.coroutines/kotlinx-coroutines-core/build/classes/kotlin/macosArm64/main/klib/kotlinx-coroutines-core.klib.asc'.
 * Reason: Task ':kotlinx-coroutines-core:linkWorkerTestDebugTestMacosArm64' uses this output of task ':kotlinx-coroutines-core:signMacosArm64Publication' without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed.
 *
 * ```
 * and
 * ```
 * Gradle detected a problem with the following location: 'kotlinx-coroutines-core/build/libs/kotlinx-coroutines-core-1.7.2-SNAPSHOT-javadoc.jar.asc'.
 * Reason: Task ':kotlinx-coroutines-core:publishAndroidNativeArm32PublicationToMavenLocal' uses this output of task ':kotlinx-coroutines-core:signAndroidNativeArm64Publication' without declaring an explicit or implicit dependency.
 * ```
 */
fun Project.establishSignDependencies() {
    tasks.withType<Sign>().configureEach {
        val pubName = name.removePrefix("sign").removeSuffix("Publication")
        // Gradle#26132 -- establish dependency between sign and link tasks, as well as compile ones
        mustRunAfter(tasks.matching { it.name == "linkDebugTest$pubName" })
        mustRunAfter(tasks.matching { it.name == "linkWorkerTestDebugTest$pubName" })
        mustRunAfter(tasks.matching { it.name == "compileTestKotlin$pubName" })
    }

    // Sign plugin issues and publication:
    // Establish dependency between 'sign' and 'publish*' tasks
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
