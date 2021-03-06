/*
 * Copyright 2019 lespinsideg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.bulkhead.configure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.bulkhead.configure.threadpool.ThreadPoolBulkheadConfiguration;
import io.github.resilience4j.bulkhead.event.BulkheadEvent;
import io.github.resilience4j.consumer.DefaultEventConsumerRegistry;
import io.github.resilience4j.consumer.EventConsumerRegistry;
import io.github.resilience4j.fallback.FallbackDecorators;
import io.github.resilience4j.fallback.configure.FallbackConfiguration;
import io.github.resilience4j.utils.ReactorOnClasspathCondition;
import io.github.resilience4j.utils.RxJava2OnClasspathCondition;

/**
 * {@link Configuration
 * Configuration} for resilience4j-bulkhead.
 */
@Configuration
@Import({ThreadPoolBulkheadConfiguration.class, FallbackConfiguration.class})
public class BulkheadConfiguration {

	/**
	 * @param bulkheadConfigurationProperties bulk head spring configuration properties
	 * @param bulkheadEventConsumerRegistry   the bulk head event consumer registry
	 * @return the BulkheadRegistry with all needed setup in place
	 */
	@Bean
	public BulkheadRegistry bulkheadRegistry(BulkheadConfigurationProperties bulkheadConfigurationProperties,
	                                         EventConsumerRegistry<BulkheadEvent> bulkheadEventConsumerRegistry) {
		BulkheadRegistry bulkheadRegistry = createBulkheadRegistry(bulkheadConfigurationProperties);
		registerEventConsumer(bulkheadRegistry, bulkheadEventConsumerRegistry, bulkheadConfigurationProperties);
		bulkheadConfigurationProperties.getBackends().forEach((name, properties) -> bulkheadRegistry.bulkhead(name, bulkheadConfigurationProperties.createBulkheadConfig(name)));
		return bulkheadRegistry;
	}

	/**
	 * Initializes a bulkhead registry.
	 *
	 * @param bulkheadConfigurationProperties The bulkhead configuration properties.
	 * @return a BulkheadRegistry
	 */
	private BulkheadRegistry createBulkheadRegistry(BulkheadConfigurationProperties bulkheadConfigurationProperties) {
		Map<String, BulkheadConfig> configs = bulkheadConfigurationProperties.getConfigs()
				.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
						entry -> bulkheadConfigurationProperties.createBulkheadConfig(entry.getValue())));

		return BulkheadRegistry.of(configs);
	}

	/**
	 * Registers the post creation consumer function that registers the consumer events to the bulkheads.
	 *
	 * @param bulkheadRegistry      The BulkHead registry.
	 * @param eventConsumerRegistry The event consumer registry.
	 */
	private void registerEventConsumer(BulkheadRegistry bulkheadRegistry,
	                                   EventConsumerRegistry<BulkheadEvent> eventConsumerRegistry, BulkheadConfigurationProperties bulkheadConfigurationProperties) {
		bulkheadRegistry.getEventPublisher().onEntryAdded(event -> registerEventConsumer(eventConsumerRegistry, event.getAddedEntry(), bulkheadConfigurationProperties));
	}

	private void registerEventConsumer(EventConsumerRegistry<BulkheadEvent> eventConsumerRegistry, Bulkhead bulkHead, BulkheadConfigurationProperties bulkheadConfigurationProperties) {
		int eventConsumerBufferSize = Optional.ofNullable(bulkheadConfigurationProperties.getBackendProperties(bulkHead.getName()))
				.map(BulkheadConfigurationProperties.BackendProperties::getEventConsumerBufferSize)
				.orElse(100);
		bulkHead.getEventPublisher().onEvent(eventConsumerRegistry.createEventConsumer(bulkHead.getName(), eventConsumerBufferSize));
	}

	@Bean
	public BulkheadAspect bulkheadAspect(BulkheadConfigurationProperties bulkheadConfigurationProperties, ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry,
	                                     BulkheadRegistry bulkheadRegistry, @Autowired(required = false) List<BulkheadAspectExt> bulkHeadAspectExtList,
	                                     FallbackDecorators fallbackDecorators) {
		return new BulkheadAspect(bulkheadConfigurationProperties, threadPoolBulkheadRegistry, bulkheadRegistry, bulkHeadAspectExtList, fallbackDecorators);
	}

	@Bean
	@Conditional(value = {RxJava2OnClasspathCondition.class})
	public RxJava2BulkheadAspectExt rxJava2BulkHeadAspectExt() {
		return new RxJava2BulkheadAspectExt();
	}

	@Bean
	@Conditional(value = {ReactorOnClasspathCondition.class})
	public ReactorBulkheadAspectExt reactorBulkHeadAspectExt() {
		return new ReactorBulkheadAspectExt();
	}

	/**
	 * The EventConsumerRegistry is used to manage EventConsumer instances.
	 * The EventConsumerRegistry is used by the BulkheadHealthIndicator to show the latest Bulkhead events
	 * for each Bulkhead instance.
	 *
	 * @return a default EventConsumerRegistry {@link DefaultEventConsumerRegistry}
	 */
	@Bean
	public EventConsumerRegistry<BulkheadEvent> bulkheadEventConsumerRegistry() {
		return new DefaultEventConsumerRegistry<>();
	}
}
