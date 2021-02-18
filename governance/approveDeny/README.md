Artifactory Authenticate Entitlements User Plugin
=================================================

This plugin authenticates client IP address and entitlements by
injecting the approval of an external REST service into the authentication
process using **altResponse**.

Reference the User [Plugin documentation](https://www.jfrog.com/confluence/display/JFROG/User+Plugins)
for more information about plugins.

Features
--------

- Calls external REST service for additional authentication beyond just
  username/password. For example: Client IP address verification.
- Provides two examples of chained authentication for IP address and
  user access to specific artifacts.
- Provides simulated REST service example.

Installation
---------------------

1. Edit the `${ARTIFACTORY_HOME}/var/etc/artifactory/logback.xml` to add:

   ```xml
   <logger name="approveDeny">
       <level value="debug"/>
   </logger>
   ```

1. Download the following dependency jars, and copy them to the
  `${ARTIFACTORY_HOME}/etc/plugins/lib` directory:
  * [HTTPBuilder](https://bintray.com/bintray/jcenter/org.codehaus.groovy.modules.http-builder%3Ahttp-builder/_latestVersion)
  * [Json-lib](https://bintray.com/bintray/jcenter/net.sf.json-lib%3Ajson-lib/_latestVersion)
  * [Xml-resolver](https://bintray.com/bintray/jcenter/xml-resolver%3Axml-resolver/_latestVersion)
1. Edit the `approveDeny.json` file. See the example `approveDeny.json` provided or
   the details below on how to edit `approveDeny.json`.
1. Copy `approveDeny.groovy` and `approveDeny.json` to the `${ARTIFACTORY_HOME}/etc/artifactory/plugins` directory.
1. There are two ways to load the plugin:
   - Manually force the plugin to be loaded `curl -u username:password -XPOST -H'Content-Type: application/json' http://localhost:8080/artifactory/api/plugins/reload`
   - Refer to the Plugin documentation for how to enable **Auto Reload**.
1. Verify in the `$JF_PRODUCT_DATA_INTERNAL/logs/artifactory-service.log` that the plugin
   loaded the configuration correctly.

Creating an approveDeny.json
-------------------------

1. There is support for two servers: IP validation and entitlement validation. Adjust
   URL (the current version is a test server running locally on port 8888):

  ```
  {
    "ipServer": "http://host.docker.internal:8888/validateIP",
    "entitlementServer": "http://host.docker.internal:8888/validateEntitlements"
  }
  ```

REST API
-------------------------
1. IP Server:

  Expects to receive the json object:

  ```
  {'Transaction_IP': '', 'AppName': '', 'Email': ''}
  ```

  Expects the result:

  ```
  {'status': 'Approved/DECLINE', 'country': '', 'country2letter': '', 'domain': '', 'region': '', 'message': ''}
  ```

1. Entitlement Server:

  Expects to receive the json object:

  ```
  {'RepositoryName': '', 'ModuleName': '', 'ProductNameSpace': '', 'Version': '', 'Email': ''}
  ```

  Expects the result:

  ```
  {'status': 'Approved/DECLINE'}
  ```


Testing
-------

Please see [DEBUG.md](DEBUG.md) for testing and debugging.
