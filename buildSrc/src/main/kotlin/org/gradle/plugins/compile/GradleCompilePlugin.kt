package org.gradle.plugins.compile

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.kotlin.dsl.the

open class GradleCompilePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val rootProject = project.rootProject
        if (rootProject == project) {
            val projectInternal = project as ProjectInternal
            val javaInstallationProbe = projectInternal.services.get(JavaInstallationProbe::class.java)
            val java7Home by project.properties
            project.extensions.create("availableJdks", AvailableJdks::class.java, listOf(java7Home), javaInstallationProbe)
        }

        val availableJdks = rootProject.the<AvailableJdks>()
        println(availableJdks.jdkFor(JavaVersion.VERSION_1_7))
        println(availableJdks.jdkFor(JavaVersion.VERSION_1_8))
    }
}
