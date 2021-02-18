#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

LICENSE_FILE=$1

if [ ! -f "${LICENSE_FILE}" ]; then
    echo "Usage: bash setupdocker.sh <license file>"
    exit
fi

# Take the license file and put it into a JSON object, copy that to the Artifactory service and delete the license file so we don't commit it to the repository.
sed "s/LICENSE/$(cat ${LICENSE_FILE})/" $DIR/license.rjson > $DIR/license.json
curl -u admin:password -XPOST -H'Content-Type: application/json' http://localhost:8081/artifactory/api/system/licenses -d @$DIR/license.json | jq '.status'
rm $DIR/license.json

# Update the logback.xml to support logging for this plugin
docker cp rt1:/opt/jfrog/artifactory/var/etc/artifactory/logback.xml .
python3 $DIR/updatelogback.py logback.xml
docker cp logback.xml  rt1:/opt/jfrog/artifactory/var/etc/artifactory/logback.xml
rm logback.xml
docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/logback.xml

# Create the test repository
curl -u admin:password -XPUT -H'Content-Type: application/json' "http://localhost:8081/artifactory/api/repositories/test-docker" -T $DIR/createDockerRepo.json

# Upload test docker image
pushd $DIR/docker
bash uploaddocker.sh
popd

# Creat the lib directory for the plugins dependent libraries
docker exec -it rt1 mkdir /opt/jfrog/artifactory/var/etc/artifactory/plugins/lib

# Copy jars to container
docker cp lib/http-builder-0.7.2.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/http-builder-0.7.2.jar
docker cp lib/json-lib-2.4-jdk13.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/json-lib-2.4-jdk13.jar
docker cp lib/xml-resolver-1.2.jar  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/xml-resolver-1.2.jar

docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/http-builder-0.7.2.jar
docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/json-lib-2.4-jdk13.jar
docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/plugins/lib/xml-resolver-1.2.jar
