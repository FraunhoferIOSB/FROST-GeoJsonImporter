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
package de.fraunhofer.iosb.ilt.gjimp.utils;

import com.google.gson.JsonElement;
import de.fraunhofer.iosb.ilt.configurable.AnnotatedConfigurable;
import de.fraunhofer.iosb.ilt.configurable.ConfigEditor;
import de.fraunhofer.iosb.ilt.configurable.ConfigurationException;
import de.fraunhofer.iosb.ilt.configurable.annotations.ConfigurableField;
import de.fraunhofer.iosb.ilt.configurable.editor.EditorString;
import static de.fraunhofer.iosb.ilt.gjimp.utils.TemplateUtils.fillTemplate;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.sta.dao.BaseDao;
import de.fraunhofer.iosb.ilt.sta.model.Entity;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.query.Query;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * @param <T> The entity type this cache caches.
 */
public class EntityCache<T extends Entity<T>> implements AnnotatedConfigurable<SensorThingsService, Object> {

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheFilter", description = "Filter used to load the cache.")
	@EditorString.EdOptsString(lines = 1, dflt = "properties/type eq 'NUTS'")
	private String cacheFilter;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Select", description = "Select to use when loading the cache.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String select;

	@ConfigurableField(editor = EditorString.class, optional = true,
			label = "Expand", description = "Expand to use when loading the cache.")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String expand;

	@ConfigurableField(editor = EditorString.class, optional = false,
			label = "CacheKey", description = "Template used to generate the key used to cache, using {path.to.field|default} placeholders. Template runs against the new Entity!")
	@EditorString.EdOptsString(lines = 1, dflt = "")
	private String templateCacheKey;

	private final Map<String, T> entitiesByLocalId = new LinkedHashMap<>();
	private final Map<String, T> entitiesByName = new LinkedHashMap<>();

	private PropertyExtractor<String, T> localIdExtractor;
	private PropertyExtractor<String, T> nameExtractor;
	private boolean initialised = false;

	public EntityCache() {
		this.localIdExtractor = null;
		this.nameExtractor = null;
	}

	public EntityCache(PropertyExtractor<String, T> localIdExtractor, PropertyExtractor<String, T> nameExtractor) {
		this.localIdExtractor = localIdExtractor;
		this.nameExtractor = nameExtractor;
		initialised = true;
	}

	@Override
	public void configure(JsonElement config, SensorThingsService context, Object edtCtx, ConfigEditor<?> configEditor) throws ConfigurationException {
		AnnotatedConfigurable.super.configure(config, context, edtCtx, configEditor);
		if (Utils.isNullOrEmpty(templateCacheKey)) {
			return;
		}
		localIdExtractor = entity -> fillTemplate(templateCacheKey, entity, false);
		nameExtractor = entity -> Objects.toString(TemplateUtils.getFrom("name", entity));
		initialised = true;
	}

	public boolean isInitialised() {
		return initialised;
	}

	private void ensureInitialised() {
		if (initialised) {
			return;
		}
		throw new IllegalStateException("Cache can not be used when not initialised.");
	}

	public String getTemplateCacheKey() {
		return templateCacheKey;
	}

	public T get(String localId) {
		ensureInitialised();
		return entitiesByLocalId.get(localId);
	}

	public T getByName(String name) {
		ensureInitialised();
		return entitiesByName.get(name);
	}

	public boolean containsId(String localId) {
		ensureInitialised();
		return entitiesByLocalId.containsKey(localId);
	}

	public T getCachedVersion(T nonCachedVersion) {
		String cacheKey = fillTemplate(templateCacheKey, nonCachedVersion, false);
		return get(cacheKey);
	}

	public void put(T entity) {
		ensureInitialised();
		try {
			String localId = localIdExtractor.extractFrom(entity);
			if (localId != null) {
				entitiesByLocalId.put(localId, entity);
			}
		} catch (RuntimeException ex) {
			// probably no localId, ignore.
		}
		if (nameExtractor != null) {
			String name = nameExtractor.extractFrom(entity);
			entitiesByName.put(name, entity);
		}
	}

	public boolean isEmpty() {
		ensureInitialised();
		return entitiesByLocalId.isEmpty();
	}

	public int load(BaseDao<T> dao) throws ServiceFailureException {
		return load(dao, cacheFilter, select, expand);
	}

	public int load(BaseDao<T> dao, String filter) throws ServiceFailureException {
		return load(dao, filter, "", "");
	}

	public int load(BaseDao<T> dao, String filter, String select, String expand) throws ServiceFailureException {
		ensureInitialised();
		Query<T> query = dao.query();
		if (!select.isEmpty()) {
			query.select(select);
		}
		if (!expand.isEmpty()) {
			query.expand(expand);
		}
		if (!Utils.isNullOrEmpty(filter)) {
			query.filter(filter);
		}
		EntityList<T> entities = query.top(1000).list();
		Iterator<T> it = entities.fullIterator();
		int count = 0;
		while (it.hasNext()) {
			T entitiy = it.next();
			put(entitiy);
			count++;
		}
		return count;
	}

	public Collection<T> values() {
		ensureInitialised();
		return entitiesByLocalId.values();
	}

	public static interface PropertyExtractor<String, T extends Entity<T>> {

		public String extractFrom(T entity);
	}

}
