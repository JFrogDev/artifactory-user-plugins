# Delete Artifacts By Property Value

This plugin is used to delete artifacts with a specific property with a value less than the value specified in the execution command. 

Execution
---------

curl -X POST -v -u admin:password "http://localhost:8081/artifactory/api/plugins/execute/deleteByPropertyValue?params=propertyName=test;propertyValue=2;repo=libs-release-local"


Parameters
----------

- `propertyName`: The property name which you want to search for
- `propertyValue`: The value of the property, all files with a value lower than this will be deleted
- `repo`: The repository from which you want to delete artifacts with this property
- `dryRun`: If set to *true* the artifacts to delete will be logged but not deleted. The parameter is optional. Default: *false*.

To ensure logging for this plugin, edit ${ARTIFACTORY_HOME}/etc/logback.xml to add:
```xml
    <logger name="deleteByPropertyValue">
        <level value="info"/>
    </logger>
```
