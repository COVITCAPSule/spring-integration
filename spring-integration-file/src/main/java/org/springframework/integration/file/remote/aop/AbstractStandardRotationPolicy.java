/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.file.remote.aop;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.integration.core.MessageSource;
import org.springframework.integration.file.remote.session.DelegatingSessionFactory;
import org.springframework.util.Assert;

/**
 * Standard rotation policy; iterates over key/directory pairs; when the end is reached,
 * starts again at the beginning. If the fair option is true the rotation occurs on every
 * poll, regardless of result. Otherwise rotation occurs when the current pair returns no
 * message.
 *
 * Subclasses implement {@code onRotation(MessageSource<?> source)} to configure the
 * {@link MessageSource} on each rotation.
 *
 * @author Gary Russell
 * @author Michael Forstner
 * @author Artem Bilan
 * @author David Turanski
 *
 * @since 5.1.8
 */
public abstract class AbstractStandardRotationPolicy implements RotationPolicy {
	protected final Log logger = LogFactory.getLog(getClass()); // NOSONAR final

	private final DelegatingSessionFactory<?> factory; // NOSONAR final

	private final List<KeyDirectory> keyDirectories = new ArrayList<>();

	private final boolean fair;

	private volatile Iterator<KeyDirectory> iterator;

	private volatile KeyDirectory current;

	private volatile boolean initialized;

	protected AbstractStandardRotationPolicy(DelegatingSessionFactory<?> factory, List<KeyDirectory> keyDirectories,
			boolean fair) {

		Assert.notNull(factory, "factory cannot be null");
		Assert.notNull(keyDirectories, "keyDirectories cannot be null");
		Assert.isTrue(keyDirectories.size() > 0, "At least one KeyDirectory is required");
		this.factory = factory;
		this.keyDirectories.addAll(keyDirectories);
		this.fair = fair;
		this.iterator = this.keyDirectories.iterator();
	}

	@Override
	public void beforeReceive(MessageSource<?> source) {
		if (this.fair || !this.initialized) {
			configureSource(source);
			this.initialized = true;
		}
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Next poll is for " + this.current);
		}
		this.factory.setThreadKey(this.current.getKey());
	}

	@Override
	public void afterReceive(boolean messageReceived, MessageSource<?> source) {
		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Poll produced "
					+ (messageReceived ? "a" : "no")
					+ " message");
		}
		this.factory.clearThreadKey();
		if (!this.fair && !messageReceived) {
			configureSource(source);
		}
	}

	@Override
	public KeyDirectory getCurrent() {
		return this.current;
	}


	protected DelegatingSessionFactory<?> getFactory() {
		return this.factory;
	}

	protected List<KeyDirectory> getKeyDirectories() {
		return this.keyDirectories;
	}

	protected boolean isFair() {
		return this.fair;
	}

	protected Iterator<KeyDirectory> getIterator() {
		return this.iterator;
	}

	protected boolean isInitialized() {
		return this.initialized;
	}

	protected void configureSource(MessageSource<?> source) {

		if (!this.iterator.hasNext()) {
			this.iterator = this.keyDirectories.iterator();
		}
		this.current = this.iterator.next();

		onRotation(source);
	}

	protected abstract void onRotation(MessageSource<?> source);
}
