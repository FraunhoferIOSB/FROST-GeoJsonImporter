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
import de.fraunhofer.iosb.ilt.configurable.editor.EditorBoolean;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import de.fraunhofer.iosb.ilt.gjimp.utils.Caches;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
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
public class CreatorSensor implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorSensor.class.getName());

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Name Template", description = "Template used to generate the name, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Description Template", description = "Template used to generate the description, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 3)
	private String templateDescription;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Encoding Template", description = "Template used to generate the encoding type, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateEncoding;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Metadata Template", description = "Template used to generate the metadata, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 3)
	private String templateMetadata;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Properties Template", description = "Template used to generate the properties, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path/to/field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorBoolean.class, optional = false,
			label = "Evaluate Once", description = "Evaluate this ObservedProperty once per file, or for each feature found.")
	@EditorBoolean.EdOptsBool()
	private boolean evaluatedOnce = false;

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

	public boolean isEvaluatedOnce() {
		return evaluatedOnce;
	}

	public String generateTestOutput(Feature feature, Caches caches) {
		if (templateName.isEmpty()) {
			return "Sensor not configured.\n";
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return "Sensor:\n  ifNotEmpty Template is empty.\n";
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String encoding = fillTemplate(templateEncoding, feature, false);
		String metadata = fillTemplate(templateMetadata, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);

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

		Sensor newSensor = FrostUtils.buildSensor(name, description, encoding, metadata, properties);
		String equalsFilter = fillTemplate(templateEqualsFilter, newSensor, true);
		String cacheKey = fillTemplate(caches.getCacheSensors().getTemplateCacheKey(), newSensor, false);

		StringBuilder output = new StringBuilder("Sensor:\n");
		output.append("  name: ").append(name).append('\n')
				.append("  description: ").append(description).append('\n')
				.append("  properties: ").append(propertiesString).append('\n')
				.append("  encoding: ").append(encoding).append('\n')
				.append("  metadata: ").append(metadata).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Cache Key: ").append(cacheKey).append('\n');

		return output.toString();
	}

	public Sensor createSensor(Feature feature, FrostUtils frostUtils, Caches caches) throws JsonProcessingException, ServiceFailureException {
		if (templateName.isEmpty()) {
			return null;
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return null;
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String encoding = fillTemplate(templateEncoding, feature, false);
		String metadata = fillTemplate(templateMetadata, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		Map<String, Object> properties = Collections.emptyMap();
		if (!Utils.isNullOrEmpty(propertiesString)) {
			properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
		}

		var newSensor = FrostUtils.buildSensor(name, description, encoding, metadata, properties);
		var cache = caches.getCacheSensors();
		var cachedSensor = cache.getCachedVersion(newSensor);

		String filter = fillTemplate(templateEqualsFilter, newSensor, true);
		LOGGER.debug("Sensor Filter: {}", filter);
		Sensor sensor = frostUtils.findOrCreateSensor(filter, newSensor, cachedSensor);
		cache.put(sensor);

		return sensor;
	}

}
