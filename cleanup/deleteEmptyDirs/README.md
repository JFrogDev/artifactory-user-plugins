Artifactory Delete Empty Dirs User Plugin
=========================================

This plugin deletes all empty directories found inside any of a given set of
paths.

Parameters
----------

This plugin takes two parameters, called 
`paths`, which consists of a comma-separated list of paths to search for empty directories in (each path is
in the form `repository-name/path/to/dir`, use keyword "__all__" to check all repositories) and
`cron`, which is a string containing cron syntax schedule to run this plugin (Default *"0 0 5 ? * 1"*).

Executing
---------

To execute the plugin:

`curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=repo,otherRepo/some/path"`
