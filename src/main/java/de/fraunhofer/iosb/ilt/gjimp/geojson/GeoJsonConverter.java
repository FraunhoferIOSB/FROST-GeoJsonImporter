/*
 * Copyright (C) 2020 Fraunhofer IOSB
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
package de.fraunhofer.iosb.ilt.gjimp.geojson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorClass;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorList;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorSubclass;
import de.fraunhofer.iosb.ilt.gjimp.ImportException;
import de.fraunhofer.iosb.ilt.gjimp.StaService;
import de.fraunhofer.iosb.ilt.gjimp.utils.Caches;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.ObservationUploader;
import de.fraunhofer.iosb.ilt.gjimp.utils.ProgressTracker;
import de.fraunhofer.iosb.ilt.gjimp.validator.Validator;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.util.ArrayList;
import java.util.List;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class GeoJsonConverter implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(GeoJsonConverter.class.getName());

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "Caches", description = "The various caches to use for loading entities.")
	@EditorClass.EdOptsClass(clazz = Caches.class)
	private Caches caches;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Characterset", description = "The character set to use when parsing the csv file (default UTF-8).")
	@EditorString.EdOptsString(dflt = "UTF-8")
	private String charset;

	@ConfigurableField(editor = EditorClass.class, optional = false,
			label = "SensorThingsService", description = "The STA service to upload the Locations & Things to")
	@EditorClass.EdOptsClass(clazz = StaService.class)
	private StaService uploader;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Locations", description = "The definition of how to create Locations.")
	@EditorClass.EdOptsClass(clazz = CreatorLocation.class)
	private CreatorLocation creatorLocations;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "Things", description = "The definition of how to create Things.")
	@EditorClass.EdOptsClass(clazz = CreatorThing.class)
	private CreatorThing creatorThings;

	@ConfigurableField(editor = EditorList.class, optional = true,
			label = "ObsProps", description = "The definitions of how to create ObservedProperties.")
	@EditorList.EdOptsList(editor = EditorClass.class)
	@EditorClass.EdOptsClass(clazz = CreatorObservedProperty.class)
	private List<CreatorObservedProperty> creatorObservedProperties;

	@ConfigurableField(editor = EditorList.class, optional = true,
			label = "Sensors", description = "The definitions of how to create Sensors.")
	@EditorList.EdOptsList(editor = EditorClass.class)
	@EditorClass.EdOptsClass(clazz = CreatorSensor.class)
	private List<CreatorSensor> creatorSensors;

	@ConfigurableField(editor = EditorList.class, optional = true,
			label = "Datastreams", description = "The definitions of how to create Datastreams.")
	@EditorList.EdOptsList(editor = EditorClass.class)
	@EditorClass.EdOptsClass(clazz = CreatorDatastream.class)
	private List<CreatorDatastream> creatorDatastreams;

	@ConfigurableField(editor = EditorList.class, optional = true,
			label = "Observations", description = "The definitions of how to create Observations.")
	@EditorList.EdOptsList(editor = EditorClass.class)
	@EditorClass.EdOptsClass(clazz = CreatorObservation.class)
	private List<CreatorObservation> creatorObservations;

	@ConfigurableField(editor = EditorSubclass.class, optional = true,
			label = "Validator", description = "The validator to use.")
	@EditorSubclass.EdOptsSubclass(iface = Validator.class)
	private Validator validator;

	@ConfigurableField(editor = EditorClass.class, optional = true,
			label = "CSV Loader", description = "The definition of if and how to load CSV.")
	@EditorClass.EdOptsClass(clazz = CsvLoaderOptions.class)
	private CsvLoaderOptions csvLoader;

	private SensorThingsService service;
	private FrostUtils frostUtils;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
		frostUtils = new FrostUtils(service);
	}

	public CsvLoaderOptions getCsvLoader() {
		return csvLoader;
	}

	public String getCharset() {
		return charset;
	}

	public String generateTestOutput(Feature feature) {
		final StringBuilder output = new StringBuilder()
				.append(creatorLocations.generateTestOutput(feature, caches))
				.append('\n')
				.append(creatorThings.generateTestOutput(feature, caches));
		for (var cr : creatorObservedProperties) {
			output.append('\n').append(cr.generateTestOutput(feature, caches));
		}
		for (var cr : creatorSensors) {
			output.append('\n').append(cr.generateTestOutput(feature, caches));
		}
		for (var cr : creatorDatastreams) {
			output.append('\n').append(cr.generateTestOutput(feature, caches));
		}
		for (var cr : creatorObservations) {
			output.append('\n').append(cr.generateTestOutput(feature, caches));
		}
		return output
				.toString();
	}

	public void importAll(FeatureCollection collection, ProgressTracker tracker) throws ImportException, ServiceFailureException {
		caches.loadAll(tracker);

		for (var cop : creatorObservedProperties) {
			if (cop.isEvaluatedOnce()) {
				try {
					cop.createObservedProperty(null, frostUtils, caches);
				} catch (JsonProcessingException ex) {
					throw new ImportException(ex);
				}
			}
		}
		for (var cs : creatorSensors) {
			if (cs.isEvaluatedOnce()) {
				try {
					cs.createSensor(null, frostUtils, caches);
				} catch (JsonProcessingException ex) {
					throw new ImportException(ex);
				}
			}
		}

		List<Observation> obs = new ArrayList<>();
		List<Feature> features = collection.getFeatures();
		int total = features.size();
		int count = 0;
		tracker.updateProgress(count, total);
		for (Feature feature : features) {
			try {
				importFeature(feature, obs);
				tracker.updateProgress(++count, total);
			} catch (JsonProcessingException | ServiceFailureException ex) {
				throw new ImportException(ex);
			}
		}
		LOGGER.info("Generated {} Observations.", obs.size());
		ObservationUploader ul = new ObservationUploader(service, true, 100);
		if (validator != null) {
			validator.setObservationUploader(ul);
			for (var o : obs) {
				if (validator.isValid(o)) {
					ul.addObservation(o);
				}
			}
		}
		long uploaded = ul.sendDataArray();
		LOGGER.info("Uploaded {} Observations.", uploaded);
	}

	public void importFeature(Feature feature, List<Observation> obs) throws JsonProcessingException, ServiceFailureException, ImportException {
		Location location = creatorLocations.createLocation(feature, frostUtils, caches);
		creatorThings.createThing(feature, location, frostUtils, caches);
		for (var cop : creatorObservedProperties) {
			if (!cop.isEvaluatedOnce()) {
				cop.createObservedProperty(feature, frostUtils, caches);
			}
		}
		for (var cs : creatorSensors) {
			if (!cs.isEvaluatedOnce()) {
				cs.createSensor(feature, frostUtils, caches);
			}
		}
		for (var cd : creatorDatastreams) {
			cd.createDatastream(feature, frostUtils, caches);
		}
		if (!creatorObservations.isEmpty()) {
			for (var co : creatorObservations) {
				var o = co.createObservation(feature, frostUtils, caches);
				if (o != null) {
					obs.add(o);
				}
			}
		}
	}

}
