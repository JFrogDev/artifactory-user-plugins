Artifactory Manual Build Cleanup User Plugin
============================================

This plugin deletes builds that are referencing artifacts that have been deleted.
(For example by the usage of the [artifactCleanup plugin](https://github.com/markgalpin/artifactory-user-plugins/tree/master/cleanup/artifactCleanup)
which does not delete corresponding builds).
The plugin utilizes the `manualBuildCleanup.json` to control how it handles
different types of repositories.

* `softClean`
  * When evaluating a build and the corresponding buildInfo, only one referenced
    artifact for a single module is required to exist in order to keep this build.
    In the case where referenced artifacts exists in both a "`softClean` repository"
    and a "`hardClean` repository" the `softClean` approach takes presedence.
* `hardClean`
  * When evaluating a build and the corresponding buildInfo, all referenced
    artifacts for a single module, must exist in order to keep this build.
* `Undefined`
  * For repositories not defined in `manualBuildCleanup.json` no clean up will be
    performed.

All modules for a given build will be evaluated untill a module has been
verified. In case of a verified module, the build is marked as valid and the
plugin will move on to the next build.

Since buildInfo only has a SHA1 as reference to any artifact, if all artifacts
of a given buildInfo are missing, the plugin cannot verify which repository the
artifacts are located. In this case the plugin will deleted the build since it is
"pointing to nothing".



Artifactory Build Cleanup User Plugin
=====================================

**&ast;&ast;&ast; DEPRECATION NOTICE &ast;&ast;&ast;**

*Artifactory 6.6 introduced [Build Info Repositories](https://www.jfrog.com/confluence/display/RTF/Release+Notes#ReleaseNotes-Artifactory6.6).  With this introduction, standard artifact cleanup techniques such as via the [CLI](https://jfrog.com/blog/aql-cli-a-match-made-in-heaven/) or via the [artifactCleanup plugin](https://github.com/markgalpin/artifactory-user-plugins/tree/master/cleanup/artifactCleanup) should work equivalently to this plugin, so we are deprecating this plugin.*

*Documentation and plugin is preserved here for users of older versions.*

This plugin deletes all builds that are older than n days. It can be run
manually from the REST API, or automatically as a scheduled job.

Parameters
----------

- `days`: The number of days back to look before deleting a build. Default 2.
- `dryRun`: If this parameter is passed, builds will not actually be deleted.

Properties
----------

- `days`: The number of days back to look before deleting a build.
- `dryRun`: If this property is true, builds will not actually be deleted.

Executing
---------

To execute the plugin:


For Artifactory 4.x:


`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanBuilds?params=days=50|dryRun"`


For Artifactory 5.x:
`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/cleanBuilds?params=days=50;dryRun"`
