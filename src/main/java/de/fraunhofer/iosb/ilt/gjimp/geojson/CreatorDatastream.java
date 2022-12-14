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
import de.fraunhofer.iosb.ilt.configurable.Utils;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableClass;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.utils.Caches;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.geojson.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hylke
 */
@ConfigurableClass
public class CreatorDatastream implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorDatastream.class.getName());

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Name Template", description = "Template used to generate the name, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Description Template", description = "Template used to generate the description, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateDescription;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Properties Template", description = "Template used to generate the properties, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "UoM Name Template", description = "Template used to generate the Name of the Unit Of Measurement, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateUomName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "UoM Symbol Template", description = "Template used to generate the Symbol of the Unit Of Measurement, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateUomSymbol;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "UoM Definition Template", description = "Template used to generate the Definition of the Unit Of Measurement, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateUomDefinition;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "ObservationType Template", description = "Template used to generate the ObservationType, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4, dflt = "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Observation")
	private String templateObservationType;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path.to.field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Thing Key Template", description = "Template used to generate the key to find the Thing, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "{name}")
	private String thingCacheTemplate;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Sensor Key Template", description = "Template used to generate the key to find the Sensor, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "{name}")
	private String sensorCacheTemplate;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "ObsProp Key Template", description = "Template used to generate the key to find the ObservedProperty, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "{name}")
	private String obsPropCacheTemplate;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "If Not Empty", description = "Template that must result in a non-empty value for the Entity to be created, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String ifNotEmptyTemplate;

	private SensorThingsService service;

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		service = context;
	}

	public String generateTestOutput(Feature feature, Caches caches) {
		if (templateName.isEmpty()) {
			return "Datastream not configured.\n";
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return "Datastream:\n  ifNotEmpty Template is empty.\n";
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String uomName = fillTemplate(templateUomName, feature, false);
		String uomSymbol = fillTemplate(templateUomSymbol, feature, false);
		String uomDef = fillTemplate(templateUomDefinition, feature, false);
		String obsType = fillTemplate(templateObservationType, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		String thingKey = fillTemplate(thingCacheTemplate, feature, false);
		String sensorKey = fillTemplate(sensorCacheTemplate, feature, false);
		String obsPropKey = fillTemplate(obsPropCacheTemplate, feature, false);

		Map<String, Object> properties = null;
		if (!propertiesString.trim().isEmpty()) {
			try {
				properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
				propertiesString = ObjectMapperFactory.get().writeValueAsString(properties);
			} catch (JsonProcessingException ex) {
				propertiesString = "Failed to parse json: " + ex.getMessage();
				properties = new HashMap<>();
			}
		}

		var uom = new UnitOfMeasurement(uomName, uomSymbol, uomDef);
		Datastream newDatastream = FrostUtils.buildDatastream(name, description, properties, uom, obsType, null, null, null);

		String equalsFilter = fillTemplate(templateEqualsFilter, newDatastream, true);
		String cacheKey = fillTemplate(caches.getCacheDatastreams().getTemplateCacheKey(), newDatastream, false);

		StringBuilder output = new StringBuilder("Datastream:\n");
		output.append("  Name: ").append(name).append('\n')
				.append("  Description: ").append(description).append('\n')
				.append("  Unit: {\n")
				.append("    ").append(uomName).append(",\n")
				.append("    ").append(uomSymbol).append(",\n")
				.append("    ").append(uomDef).append("}\n")
				.append("  ObservationType: ").append(obsType).append('\n')
				.append("  Properties: ").append(propertiesString).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Cache Key: ").append(cacheKey).append('\n')
				.append("  Thing Cache Key: ").append(thingKey).append('\n')
				.append("  Sensor Cache Key: ").append(sensorKey).append('\n')
				.append("  ObsProp Cache Key: ").append(obsPropKey).append('\n');

		return output.toString();
	}

	public Datastream createDatastream(Feature feature, FrostUtils frostUtils, Caches caches) throws JsonProcessingException, ServiceFailureException {
		if (templateName.isEmpty()) {
			return null;
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return null;
		}
		var cache = caches.getCacheDatastreams();

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String uomName = fillTemplate(templateUomName, feature, false);
		String uomSymbol = fillTemplate(templateUomSymbol, feature, false);
		String uomDef = fillTemplate(templateUomDefinition, feature, false);
		String obsType = fillTemplate(templateObservationType, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		String keyThing = fillTemplate(thingCacheTemplate, feature, false);
		String keySensor = fillTemplate(sensorCacheTemplate, feature, false);
		String keyObsProp = fillTemplate(obsPropCacheTemplate, feature, false);

		var uom = new UnitOfMeasurement(uomName, uomSymbol, uomDef);
		Map<String, Object> properties = Collections.emptyMap();
		if (!Utils.isNullOrEmpty(propertiesString)) {
			properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
		}
		var thing = caches.getCacheThings().get(keyThing);
		var sensor = caches.getCacheSensors().get(keySensor);
		var obsProp = caches.getCacheObservedProperties().get(keyObsProp);

		Datastream newDs = FrostUtils.buildDatastream(name, description, properties, uom, obsType, thing, obsProp, sensor);
		Datastream cachedDs = cache.getCachedVersion(newDs);

		String filter = fillTemplate(templateEqualsFilter, newDs, true);
		LOGGER.debug("Thing Filter: {}", filter);
		Datastream dataStream = frostUtils.findOrCreateDatastream(filter, newDs, cachedDs);
		cache.put(dataStream);

		return dataStream;
	}

}
