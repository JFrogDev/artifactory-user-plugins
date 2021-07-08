Artifacts delete based on artifact properties User Plugin
=====================================
##Description:
This plugin deletes builds based on properties defined on repositories . It can be run automatically as a scheduled job.

##Features:
This plugin Takes the Parameters from the Artifact/Repository

It will scan all the repositories marked with property as `DataDeprecation'= 'true' `
It will then look for property called `DataDeprecationMethod = 'version : x' ` 
That means it will keep x number versions of a particular semantic version in that respository.

Eg: 1.1.0.2
    1.1.1.2
    1.1.2.1
    1.2.1.3
    1.2.3.2

it will keep only top x number which is defined in the property, and delete the rest.

##Installation:
Copy the groovy script plugin file under ${ARTIFACTORY_HOME}/etc/plugins.

