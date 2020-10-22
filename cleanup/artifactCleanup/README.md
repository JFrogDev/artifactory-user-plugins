Artifactory Artifact Cleanup User Plugin
========================================

This plugin deletes all artifacts that have not been downloaded for the past *n time units*,
which is by default 1 month. It can be run manually from the REST API, or automatically as a scheduled job.

Many delete operations can affect performance due to disk I/O occurring. A new parameter now allows a delay per delete operation. See below.

To ensure logging for this plugin, edit ${ARTIFACTORY_HOME}/etc/logback.xml to add:
```xml
    <logger name="artifactCleanup">
        <level value="info"/>
    </logger>
```

**Note:**

If you're trying to clean Docker images, this plugin may lead to unexpectedly partial or broken cleans. It is recommended to instead use the [cleanDockerImages](https://github.com/jfrog/artifactory-user-plugins/tree/master/cleanup/cleanDockerImages) plugin for this purpose.

Parameters
----------

- `months`: **Deprecated**. Instead of `timeUnit` and `timeInterval` the `month` parameter is supported for backwards compatibility reasons. It defined the months to look back before deleting an artifact. Default *1*.
- `timeUnit`: The unit of the time interval. *year*, *month*, *day*, *hour* or *minute* are allowed values. Default *month*.
- `timeInterval`: The time interval to look back before deleting an artifact. Default *1*.
- `repos`: A list of repositories to clean. Use keyword "__all__" to cleanup all. Default *"__none__"*
- `dryRun`: If this parameter is passed, artifacts will not actually be deleted. Default *false*.
- `paceTimeMS`: The number of milliseconds to delay between delete operations. Default *0*.
- `disablePropertiesSupport`: Disable the support of Artifactory Properties (see below *Artifactory Properties support* section). Default *false*.

Properties
----------

- `monthsUntil`: The number of months back to look before deleting an artifact.
- `repos`: A list of repositories to clean.
- `paceTimeMS`: The number of milliseconds to delay between delete operations.

Artifactory Properties support
----------

Some Artifactory [Properties](https://www.jfrog.com/confluence/display/RTF/Properties) are supported if defined on *artifacts* or *folders*:

- `cleanup.skip`: Skip the artifact deletion if property defined on artifact's path ; artifact itself or in a parent folder(s).


`artifactCleanup.json`
----------

The json contains the policies for scheduling cleanup jobs for different repositories.

The following properties are supported by each policy descriptor object:
- `cron`: The cron time when the job is executed. Default `0 0 5 ? * 1` *should* be redefined.
- `repos`: The mandatory list of repositories the policies will be applied to.
- `timeUnit`: The unit of the time interval. *year*, *month*, *day*, *hour* or *minute* are allowed values. Default *month*.
- `timeInterval`: The time interval to look back before deleting an artifact. Default *1*.
- `dryRun`:  If this parameter is passed, artifacts will not actually be deleted. Default *false*.
- `paceTimeMS`: The number of milliseconds to delay between delete operations. Default *0*.
- `disablePropertiesSupport`: Disable the support of Artifactory Properties (see above *Artifactory Properties support* section).

An example file could contain the following json:
```json
{
    "policies": [
        {
            "cron": "0 0 1 ? * 1",
            "repos": [
                "libs-releases-local"
            ],
            "timeUnit": "day",
            "timeInterval": 3,
            "dryRun": true,
            "paceTimeMS": 500,
            "disablePropertiesSupport": true
        }
    ]
}
```

**Note**: If a deprecated `artifactCleanup.properties` is defined it will only be applied if no `artifactCleanup.json` is present.

Executing
---------

To execute the plugin:

For Artifactory 4.x: 
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanup?params=timeUnit=month|timeInterval=1|repos=libs-release-local|dryRun=true|paceTimeMS=2000|disablePropertiesSupport=true"`

For Artifactory 5.x or higher:
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanup?params=timeUnit=month;timeInterval=1;repos=libs-release-local;dryRun=true;paceTimeMS=2000;disablePropertiesSupport=true"`

For Artifactory 5.x or higher, using the depracted `months` parameter:
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanup?params=months=1;repos=libs-release-local;dryRun=true;paceTimeMS=2000;disablePropertiesSupport=true"`

Admin users and users inside the `cleaners` group can execute the plugin.

There is also ability to control the running script. The following operations can occur

Operation
---------

The plugin have 4 control options:

- `stop`: When detected, the loop deleting artifacts is exited and the script ends. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=stop"`
- `pause`: Suspend operation. The thread continues to run with a 1 minute sleep for retest. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=pause"`
- `resume`: Resume normal execution. Example:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=resume"`
- `adjustPaceTimeMS`: Modify the running delay factor by increasing/decreasing the delay value. Example:


For Artifactory 4.x
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS|value=-1000"` 

For Artifactory 5.x or higher:
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS;value=-1000"` 
