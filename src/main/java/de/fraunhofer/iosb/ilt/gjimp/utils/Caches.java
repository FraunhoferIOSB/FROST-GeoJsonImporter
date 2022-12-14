/*
 * Copyright (C) 2022 Fraunhofer IOSB
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.gjimp.utils;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.dao.BaseDao;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Entity;
import de.fraunhofer.iosb.ilt.sta.model.FeatureOfInterest;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.MultiDatastream;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
public class Caches implements AnnotatedConfigurable<SensorThingsService, Object> {
	private static final Logger LOGGER = LoggerFactory.getLogger(Caches.class.getName());

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Thing Cache", description = "The cache for Things")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<Thing> cacheThings;

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "Location Cache", description = "The cache for Locations")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<Location> cacheLocations;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Sensor Cache", description = "The cache for Sensors")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<Sensor> cacheSensors;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "ObservedProperty Cache", description = "The cache for ObservedProperties")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<ObservedProperty> cacheObservedProperties;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Datastream Cache", description = "The cache for Datastreams")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<Datastream> cacheDatastreams;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "MultiDatastream Cache", description = "The cache for MultiDatastreams")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<MultiDatastream> cacheMultiDatastreams;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Feature Cache", description = "The cache for Features of Interest")
	@EditorClass.EdOptsClass(clazz = EntityCache.class)
	private EntityCache<FeatureOfInterest> cacheFeatures;

	private SensorThingsService service;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
	}

	public void loadAll(ProgressTracker tracker) throws ServiceFailureException {
		tracker.updateProgress(0, 7);
		LOGGER.info("Loading Things Cache...");
		maybeLoad(cacheThings, service.things());
		tracker.updateProgress(1, 7);
		LOGGER.info("Loading Locations Cache...");
		maybeLoad(cacheLocations, service.locations());
		tracker.updateProgress(2, 7);
		LOGGER.info("Loading Sensors Cache...");
		maybeLoad(cacheSensors, service.sensors());
		tracker.updateProgress(3, 7);
		LOGGER.info("Loading ObservedProperties Cache...");
		maybeLoad(cacheObservedProperties, service.observedProperties());
		tracker.updateProgress(4, 7);
		LOGGER.info("Loading Datastreams Cache...");
		maybeLoad(cacheDatastreams, service.datastreams());
		tracker.updateProgress(5, 7);
		LOGGER.info("Loading MultiDatastreams Cache...");
		maybeLoad(cacheMultiDatastreams, service.multiDatastreams());
		tracker.updateProgress(6, 7);
		LOGGER.info("Loading Features Cache...");
		maybeLoad(cacheFeatures, service.featuresOfInterest());
		tracker.updateProgress(7, 7);
		LOGGER.info("Done Loading Caches.");
	}

	private <E extends Entity<E>> void maybeLoad(EntityCache<E> cache, BaseDao<E> dao) throws ServiceFailureException {
		if (cache.isInitialised()) {
			cache.load(dao);
		}
	}

	public EntityCache<Thing> getCacheThings() {
		return cacheThings;
	}

	public EntityCache<Location> getCacheLocations() {
		return cacheLocations;
	}

	public EntityCache<Sensor> getCacheSensors() {
		return cacheSensors;
	}

	public EntityCache<ObservedProperty> getCacheObservedProperties() {
		return cacheObservedProperties;
	}

	public EntityCache<Datastream> getCacheDatastreams() {
		return cacheDatastreams;
	}

	public EntityCache<MultiDatastream> getCacheMultiDatastreams() {
		return cacheMultiDatastreams;
	}

	public EntityCache<FeatureOfInterest> getCacheFeatures() {
		return cacheFeatures;
	}

}
