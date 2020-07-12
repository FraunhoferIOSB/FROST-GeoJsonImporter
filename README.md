# FROST-GeoJSON-Importer

A tool for importing GeoJSON FeatureCollections as Locations & Things into a SensorThings API compatible service.

Starting without parameters opens the gui, which can be used to create or edit a configuration file.

Start the application by running (or double-clicking the jar):
```
java -jar FROST-GeoJsonImporter-0.1-shaded.jar
```


## Manual

The GeoJSON Importer takes a GeoJSON file containing a Feature collection, parses each feature in turn, and create a SensorThings Location and (optionally) a Thing for each Feature.

1. First it loads a cache of existing Locations and Things from the target SensorThings API service.
   Which Entities are loaded depends on the `CacheFilter` that is used. Each entity is assigned a key, based on the `CacheKey` template.
2. For each Feature it creates a Location Entity, generating name, description and properties using the respective templates.
3. It checks the cache to see if the generated Entity already exists on the server, by searching it based on the `CacheKey` template.
4. If the Entity is not found, it searches for it on the server, using the `EqualsFilter`.
5. Depending on if the Entity is found in step 3 or 4:
   1. If the Entity is found in the Cache or directly on the server, the version loaded from the cache/server is updated, stored in the cache, and sent to the server.
   2. If the Entity is not found on the server, the new Entity is sent to the server, and added to the cache.
6. After the Location is created, a Thing is created, linked to the Location and steps 3 to 5 are repeated for the Thing.

### User Interface

* Use the "Open" button on the top-left to load a GeoJSON file. After loading a GeoJSON file, the top-left text area shows the first feature in the file.
* Use the `<--` and `-->` buttons to show a different feature.
* The top-right text area shows how the filters and template are applied to the shown feature.
* The configuration can be changed in the bottom area, and saved or loaded using the `Load Config` and `Save Config` buttons in the top-right.
* The Import button on the bottom-right starts the import. The progress bar at the top shows the progress of the import.


### Placeholders

Most templates accept placeholders in the form of:

```
{path/to/field|default}
```

For the templates that define fields of the Entity the path is relative to the Feature.
For the other templates, the path is relative to the newly created entity.
If any of the fields in the path contain the `~` character, encode it as `~0`.
If any of the fields in the path contain the `/` character, encode it as `~1`.

