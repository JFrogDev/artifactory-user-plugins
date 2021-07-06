# Artifactory Substitute External Package with Internal Package User Plugin

This plugin can be used to substitute an External package with one available in Internal repository if both are having
the same name. For example of there are internal and external packages by the name of 'node-xyz', this plugin would make
sure that the internal package(one in local repository) is given back rather than the external one.

This plugin would change the normal behaviour of the virtual repositories, for npm repo types. Use it in teh special
case where:
1.) You have internal and external npm repositories And
2.) You have the same packages in internal and external repositories
With this plugin the expected behaviour is that we always get the latest package in the local npm repository and
versions in remote repositories are ignored.

Note that the ideal way this should be handled is by using scoped packages. However of its not possible to convert all
to a scoped package use this plugin.


## Features

It's assumed that the npm repositories are having a name that has "npm" in it, and also that there is only one local
repository "npm-local" to be searched. However this can easily be tweaked and made to look for all possible local repos.

#### Installation
To install this plugin:
  - Place the script under the master Artifactory instance in the
  `${ARTIFACTORY_HOME}/etc/plugins`
  - Verify in the `${ARTIFACTORY_HOME}/logs/artifactory.log` that the
  plugin loaded correctly


