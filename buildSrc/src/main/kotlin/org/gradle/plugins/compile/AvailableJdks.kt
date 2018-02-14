package org.gradle.plugins.compile

import org.gradle.api.JavaVersion
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation
import java.io.File

class DefaultJavaInstallation : LocalJavaInstallation {
    private lateinit var name: String
    private lateinit var javaVersion: JavaVersion
    private lateinit var javaHome: File
    private lateinit var displayName: String

    override fun getName() = name
    override fun getDisplayName() = displayName
    override fun setDisplayName(displayName: String) { this.displayName = displayName }
    override fun getJavaVersion() = javaVersion
    override fun setJavaVersion(javaVersion: JavaVersion) { this.javaVersion = javaVersion }
    override fun getJavaHome() = javaHome
    override fun setJavaHome(javaHome: File) { this.javaHome = javaHome }

    override fun toString(): String = "${displayName} (${javaHome.absolutePath})"
}

fun findJavaInstallations(javaHomes: List<String>, javaInstallationProbe: JavaInstallationProbe): Map<JavaVersion, LocalJavaInstallation> =
    javaHomes.map { javaHome ->
        val javaInstallation = DefaultJavaInstallation()
        javaInstallation.javaHome = File(javaHome)
        javaInstallationProbe.checkJdk(File(javaHome)).configure(javaInstallation)
        javaInstallation
    }.associateBy { it.javaVersion }

open class AvailableJdks(javaHomes: List<String>, javaInstallationProbe: JavaInstallationProbe) {
    private val javaInstallations: Map<JavaVersion, LocalJavaInstallation> = findJavaInstallations(javaHomes, javaInstallationProbe)
    private val currentJavaInstallation: LocalJavaInstallation
    init {
        val current = DefaultJavaInstallation()
        javaInstallationProbe.current(current)
        current.javaHome = Jvm.current().javaHome
        currentJavaInstallation = current
    }

    fun jdkFor(javaVersion: JavaVersion): LocalJavaInstallation? =
        if (currentJavaInstallation.javaVersion == javaVersion) {
            currentJavaInstallation
        } else {
            javaInstallations[javaVersion]
        }
}
