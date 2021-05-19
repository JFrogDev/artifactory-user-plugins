Artifactory Pommer User Plugin
==============================

*As this plugin creates and uploads new files, it is not supported on Artifactory Edge Nodes.  The recommended best practice is to use the plugin upstream, and ensure that release bundles include the POM files when deploying to your Artifactory Edge node*

This plugin autogenerates `.pom` files for all the artifacts that don't have them already and are not in the JSON file. This JSON file has all the file extensions that you want to avoid the plugin to create `.pom` files for. This can be useful, for example, when running a version search on a very large repository: since metadata searches are much faster than full database searches, any artifact with a `.pom` file will be found much more quickly by the search. 

This plugin only creates `.pom` files for artifacts in local Maven 2 repositories, which conform to the Maven 2 layout. The **pommer.json** is given as a reference where users can add or remove file extensions in it. If the JSON file is not found, the plugin will keep generating `.pom` files for all artifacts deployed to a local Maven repository which conforms to Maven 2 layout.

Whenever an artifact is deployed manually,  a `.pom` file will automatically be generated if one doesn't already exist. Also, a REST endpoint `pommify` is defined that allows `.pom` files to be generated for all artifacts located in a provided set of repositories. Before generating the `.pom` file, it will check for the JSON file. For example:

``` shell
curl -v -XPOST -uadmin:password "http://localhost:8081/artifactory/api/plugins/execute/pommify?params=repos=ext-release-local,libs-release-local"
```
