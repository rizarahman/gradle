/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies

import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler
import spock.lang.Specification

class DefaultCapabilitiesHandlerTest extends Specification {
    def componentModuleMetadataHandler = Mock(ComponentModuleMetadataHandler)
    def capabilities = new DefaultCapabilitiesHandler(componentModuleMetadataHandler)

    def "can declare a capability"() {
        given:
        def c
        expect:
        capabilities.capability('foo') {
            c = it
            it.providedBy 'foo:bar'
        }

        and:
        capabilities.capability('foo') {
            assert it.is(c)
            it.prefer 'foo:bar'
        }
    }

    def "converts capabilities to rules"() {
        given:
        capabilities.capability('foo') {
            it.providedBy('foo:bar')
            it.providedBy('foo:baz')
        }

        when:
        capabilities.convertToReplacementRules()

        then:
        0 * componentModuleMetadataHandler.module(_, _)

        when:
        capabilities.capability('foo') {
            it.prefer('foo:bar')
        }
        capabilities.convertToReplacementRules()

        then:
        1 * componentModuleMetadataHandler.module('foo:baz', _)
        0 * _
    }
}
