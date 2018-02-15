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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentModuleMetadata;
import org.gradle.api.artifacts.ComponentModuleMetadataDetails;
import org.gradle.api.artifacts.dsl.CapabilitiesHandler;
import org.gradle.api.artifacts.dsl.CapabilityHandler;
import org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler;

import java.util.Map;
import java.util.Set;

public class DefaultCapabilitiesHandler implements CapabilitiesHandler {
    private final ComponentModuleMetadataHandler metadataHandler;
    private final Map<String, DefaultCapability> capabilities = Maps.newHashMap();

    public DefaultCapabilitiesHandler(ComponentModuleMetadataHandler metadataHandler) {
        this.metadataHandler = metadataHandler;
    }

    @Override
    public void capability(String identifier, Action<? super CapabilityHandler> configureAction) {
        DefaultCapability capability = capabilities.get(identifier);
        if (capability == null) {
            capability = new DefaultCapability(identifier);
            capabilities.put(identifier, capability);
        }
        configureAction.execute(capability);
    }

    public void convertToReplacementRules() {
        for (Map.Entry<String, DefaultCapability> capabilityEntry : capabilities.entrySet()) {
            DefaultCapability capabilityValue = capabilityEntry.getValue();
            final String prefer = capabilityValue.prefer;
            if (prefer != null) {
                final String because;
                if (capabilityValue.reason != null) {
                    because = capabilityValue.reason;
                } else {
                    because = "capability " + capabilityEntry.getKey() + " is provided by " + Joiner.on(" and ").join(capabilityValue.providedBy);
                }

                for (String module : capabilityValue.providedBy) {
                    if (!module.equals(prefer)) {
                        metadataHandler.module(module, new Action<ComponentModuleMetadata>() {
                            @Override
                            public void execute(ComponentModuleMetadata componentModuleMetadata) {
                                ((ComponentModuleMetadataDetails) componentModuleMetadata).replacedBy(prefer, because);
                            }
                        });
                    }
                }
            }
        }
    }

    private final static class DefaultCapability implements CapabilityHandler {
        private final String id;
        private final Set<String> providedBy = Sets.newHashSet();
        private String prefer;
        private String reason;

        private DefaultCapability(String id) {
            this.id = id;
        }

        @Override
        public void providedBy(String moduleIdentifier) {
            providedBy.add(moduleIdentifier);
        }

        @Override
        public Preference prefer(String moduleIdentifer) {
            prefer = moduleIdentifer;
            return new Preference() {
                @Override
                public Preference because(String reason) {
                    DefaultCapability.this.reason = reason;
                    return this;
                }
            };
        }
    }
}
