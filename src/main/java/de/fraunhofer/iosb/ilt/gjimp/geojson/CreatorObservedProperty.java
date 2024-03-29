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
import de.fraunhofer.iosb.ilt.gjimp.utils.EntityCache;
import de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.FrostUtils.ENCODING_GEOJSON;
import de.fraunhofer.iosb.ilt.gjimp.utils.JsonUtils;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
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
public class CreatorObservedProperty implements AnnotatedConfigurable<SensorThingsService, Object> {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreatorObservedProperty.class.getName());

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Name Template", description = "Template used to generate the name, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateName;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Definition Template", description = "Template used to generate the definition, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1)
	private String templateDefinition;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "Description Template", description = "Template used to generate the description, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 3)
	private String templateDescription;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Properties Template", description = "Template used to generate the properties, using {path/to/field|default} placeholders.")
	@EditorString.EdOptsString(lines = 4)
	private String templateProperties;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "EqualsFilter", description = "Template used to generate the filter to check for duplicates, using {path/to/field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "name eq '{name|-}'")
	private String templateEqualsFilter;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "If Not Empty", description = "Template that must result in a non-empty value for the Entity to be created, using {path.to.field|default} placeholders.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String ifNotEmptyTemplate;

	@ConfigurableField(editor = EditorBoolean.class, optional = false,
			label = "Evaluate Once", description = "Evaluate this ObservedProperty once per file, or for each feature found.")
	@EditorBoolean.EdOptsBool()
	private boolean evaluatedOnce = false;

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
			return "ObservedProperty not configured.\n";
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return "ObservedProperty:\n  ifNotEmpty Template is empty.\n";
		}

		String name = fillTemplate(templateName, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String definition = fillTemplate(templateDefinition, feature, false);
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

		Location newLocation = new Location(name, description, ENCODING_GEOJSON, feature.getGeometry());
		newLocation.setProperties(properties);

		String equalsFilter = fillTemplate(templateEqualsFilter, newLocation, true);
		String cacheKey = fillTemplate(caches.getCacheObservedProperties().getTemplateCacheKey(), newLocation, false);

		StringBuilder output = new StringBuilder("ObservedProperty:\n");
		output.append("  Name: ").append(name).append('\n')
				.append("  Description: ").append(description).append('\n')
				.append("  Definition: ").append(definition).append('\n')
				.append("  Properties: ").append(propertiesString).append('\n')
				.append('\n')
				.append("  Equals Filter: ").append(equalsFilter).append('\n')
				.append("  Cache Key: ").append(cacheKey).append('\n');

		return output.toString();
	}

	public ObservedProperty createObservedProperty(Feature feature, FrostUtils frostUtils, Caches caches) throws JsonProcessingException, ServiceFailureException {
		if (templateName.isEmpty()) {
			return null;
		}
		if (!ifNotEmptyTemplate.isBlank() && fillTemplate(ifNotEmptyTemplate, feature, false).isBlank()) {
			return null;
		}

		String name = fillTemplate(templateName, feature, false);
		String definition = fillTemplate(templateDefinition, feature, false);
		String description = fillTemplate(templateDescription, feature, false);
		String propertiesString = fillTemplate(templateProperties, feature, false);
		Map<String, Object> properties = Collections.emptyMap();
		if (!Utils.isNullOrEmpty(propertiesString)) {
			properties = ObjectMapperFactory.get().readValue(propertiesString, JsonUtils.TYPE_MAP_STRING_OBJECT);
		}

		EntityCache<ObservedProperty> cache = caches.getCacheObservedProperties();

		ObservedProperty newEntity = new ObservedProperty(name, definition, description);
		newEntity.setProperties(properties);
		ObservedProperty cachedEntity = cache.getCachedVersion(newEntity);

		String filter = fillTemplate(templateEqualsFilter, newEntity, true);
		LOGGER.debug("Thing Filter: {}", filter);
		ObservedProperty entity = frostUtils.findOrCreateOp(filter, newEntity, cachedEntity);
		cache.put(entity);

		return entity;
	}

}
