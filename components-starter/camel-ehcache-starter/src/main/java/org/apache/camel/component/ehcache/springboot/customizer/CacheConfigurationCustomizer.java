/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.ehcache.springboot.customizer;

import java.util.Map;

import org.apache.camel.component.ehcache.EhcacheComponent;
import org.apache.camel.component.ehcache.springboot.EhcacheComponentAutoConfiguration;
import org.apache.camel.spi.ComponentCustomizer;
import org.apache.camel.spi.HasId;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.ehcache.config.CacheConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * A simple implementation of {@link ComponentCustomizer} that auto discovers a
 * {@link org.ehcache.config.CacheConfiguration} instance and bind it to the
 * {@link EhcacheComponent} component.
 *
 * This customizer can be disabled/enabled with different strategies:
 *
 * 1. globally using:
 *    camel.component.customizer.enable = true/false
 * 2. for component:
 *    camel.component.ehcache.customizer.enabled = true/false
 * 3. individually:
 *    camel.component.ehcache.customizer.cache-configuration.enabled = true/false
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Configuration
@Conditional(CacheConfigurationCustomizer.NestedConditions.class)
@AutoConfigureAfter(CamelAutoConfiguration.class)
@AutoConfigureBefore(EhcacheComponentAutoConfiguration.class)
@EnableConfigurationProperties(CacheConfigurationCustomizerConfiguration.class)
public class CacheConfigurationCustomizer implements HasId, ComponentCustomizer<EhcacheComponent> {
    @Autowired(required = false)
    private Map<String, CacheConfiguration> configurations;
    @Autowired
    private CacheConfigurationCustomizerConfiguration configuration;

    @Override
    public void customize(EhcacheComponent component) {
        if (configurations != null && configuration.getMode() == CacheConfigurationCustomizerConfiguration.Mode.APPEND) {
            component.addCachesConfigurations(configurations);
        }
        if (configurations != null && configuration.getMode() == CacheConfigurationCustomizerConfiguration.Mode.REPLACE) {
            component.setCachesConfigurations(configurations);
        }
    }

    @Override
    public String getId() {
        return "camel.component.ehcache.customizer.cache-configuration";
    }

    // *************************************************************************
    // By default ConditionalOnBean works using an OR operation so if you list
    // a number of classes, the condition succeeds if a single instance of any
    // class is found.
    //
    // A workaround is to use AllNestedConditions and creates some dummy classes
    // annotated with @ConditionalOnBean
    //
    // This should be fixed in spring-boot 2.0 where ConditionalOnBean uses and
    // AND operation instead of the OR as it does today.
    // *************************************************************************

    static class NestedConditions extends AllNestedConditions {
        public NestedConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnBean(CamelAutoConfiguration.class)
        static class OnCamelAutoConfiguration {
        }
    }
}
