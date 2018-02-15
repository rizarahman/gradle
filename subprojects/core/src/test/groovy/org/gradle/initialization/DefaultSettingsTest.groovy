/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.UnknownProjectException
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.FeaturePreviews
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.groovy.scripts.ScriptSource
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ServiceRegistryFactory
import spock.lang.Specification

import java.lang.reflect.Type

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA

class DefaultSettingsTest extends Specification {

    AsmBackedClassGenerator classGenerator = new AsmBackedClassGenerator()
    File settingsDir = new File('/somepath/root').absoluteFile
    StartParameter startParameter = new StartParameter(currentDir: new File(settingsDir, 'current'), gradleUserHomeDir: new File('gradleUserHomeDir'))
    ClassLoaderScope rootClassLoaderScope = Mock(ClassLoaderScope)
    ClassLoaderScope classLoaderScope = Mock(ClassLoaderScope)
    ScriptSource scriptSourceMock = Mock(ScriptSource)
    GradleInternal gradleMock = Mock(GradleInternal)
    ProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    ServiceRegistryFactory serviceRegistryFactory
    FileResolver fileResolver = Mock(FileResolver)
    ScriptPluginFactory scriptPluginFactory = Mock(ScriptPluginFactory)
    ScriptHandlerFactory scriptHandlerFactory = Mock(ScriptHandlerFactory)
    ScriptHandler settingsScriptHandler = Mock(ScriptHandler)
    DefaultPluginManager pluginManager = Mock(DefaultPluginManager)
    FeaturePreviews previews = new FeaturePreviews()
    DefaultSettings settings

    def setup() {
        fileResolver.resolve(_) >> { args -> args[0].canonicalFile }

        def settingsServices = Mock(ServiceRegistry)
        settingsServices.get((Type)FileResolver.class) >> fileResolver
        settingsServices.get((Type)ScriptPluginFactory.class) >> scriptPluginFactory
        settingsServices.get((Type)ScriptHandlerFactory.class) >> scriptHandlerFactory
        settingsServices.get((Type)ProjectDescriptorRegistry.class) >> projectDescriptorRegistry
        settingsServices.get((Type)FeaturePreviews.class) >> previews
        settingsServices.get((Type)DefaultPluginManager.class) >>> [pluginManager, null]

        serviceRegistryFactory = Mock(ServiceRegistryFactory) {
           1 * createFor(_) >> settingsServices
        }

        settings = classGenerator.newInstance(DefaultSettings.class, serviceRegistryFactory,
                gradleMock, classLoaderScope, rootClassLoaderScope, settingsScriptHandler,
                settingsDir, scriptSourceMock, startParameter)
    }

    def 'is wired properly'() {
        expect:
        settings.startParameter == startParameter
        settings.is(settings.getSettings())

        settings.getSettingsDir() == settingsDir
        settings.getRootProject().getProjectDir() == settingsDir
        settings.rootDir == settingsDir

        settings.getRootProject().getParent() == null
        settings.getRootProject().getProjectDir().getName() == settings.getRootProject().getName()
        settings.rootProject.buildFileName == Project.DEFAULT_BUILD_FILE
        settings.gradle.is(gradleMock)
        settings.buildscript.is(settingsScriptHandler)
        settings.getClassLoaderScope().is(classLoaderScope)
    }

    def 'can include projects'() {
        String projectA = "a"
        String projectB = "b"
        String projectC = "c"
        String projectD = "d"

        when:
        settings.include([projectA, "$projectB:$projectC"] as String[])

        then:
        settings.getRootProject().getChildren().size() == 2
        testDescriptor(settings.project(":$projectA"), projectA, new File(settingsDir, projectA))
        testDescriptor(settings.project(":$projectB"), projectB, new File(settingsDir, projectB))

        settings.project(":$projectB").getChildren().size() == 1
        testDescriptor(settings.project(":$projectB:$projectC"), projectC, new File(settingsDir, "$projectB/$projectC"))
    }

    def 'can include projects flat'() {
        String projectA = "a"
        String projectB = "b"

        when:
        settings.includeFlat([projectA, projectB] as String[])

        then:
        settings.getRootProject().getChildren().size() == 2
        testDescriptor(settings.project(":" + projectA), projectA, new File(settingsDir.parentFile, projectA))
        testDescriptor(settings.project(":" + projectB), projectB, new File(settingsDir.parentFile, projectB))
    }

    void testDescriptor(DefaultProjectDescriptor descriptor, String name, File projectDir) {
        assert name == descriptor.getName()
        assert projectDir == descriptor.getProjectDir()
    }

    def 'can create project descriptor'() {
        String testName = "testname"
        File testDir = new File("testDir")

        when:
        DefaultProjectDescriptor projectDescriptor = settings.createProjectDescriptor(settings.getRootProject(), testName, testDir)

        then:
        settings.getRootProject().is(projectDescriptor.getParent())
        settings.getProjectDescriptorRegistry().is(projectDescriptor.getProjectDescriptorRegistry())
        testName == projectDescriptor.getName()
        testDir.canonicalFile == projectDescriptor.getProjectDir()
    }

    def 'can find project by path'() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()

        when:
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getPath())

        then:
        foundProjectDescriptor.is(projectDescriptor)
    }

    def 'can find project by directory'() {
        DefaultProjectDescriptor projectDescriptor = createTestDescriptor()

        when:
        DefaultProjectDescriptor foundProjectDescriptor = settings.project(projectDescriptor.getProjectDir())

        then:
        foundProjectDescriptor.is(projectDescriptor)
    }

    def 'fails on unknown project path'() {
        when:
        settings.project("unknownPath")

        then:
        thrown(UnknownProjectException)
    }


    def 'fails on unknown project directory'() {
        when:
        settings.project(new File("unknownPath"))

        then:
        thrown(UnknownProjectException)
    }

    private DefaultProjectDescriptor createTestDescriptor() {
        String testName = "testname"
        File testDir = new File("testDir")
        return settings.createProjectDescriptor(settings.getRootProject(), testName, testDir)
    }

    def 'can get and set dynamic properties'() {
        when:
        settings.ext.dynamicProp = 'value'

        then:
        settings.dynamicProp == 'value'
    }

    def 'fails on missing property'() {
        when:
        settings.unknownProp

        then:
        thrown(MissingPropertyException)
    }

    def 'has useful toString'() {
        expect:
        settings.toString() == 'settings \'root\''
    }

    def 'can enable feature preview'() {
        when:
        settings.enableFeaturePreview("GRADLE_METADATA")

        then:
        previews.isFeatureEnabled(GRADLE_METADATA)
    }
}
