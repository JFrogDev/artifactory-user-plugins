#!/bin/bash

docker cp approveDeny.json  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.json
docker cp approveDeny.groovy  rt1:/opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.groovy

docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.json
docker exec -it --user root rt1 chown artifactory:artifactory /opt/jfrog/artifactory/var/etc/artifactory/plugins/approveDeny.groovy


curl -u admin:password -XPOST -H'Content-Type: application/json' http://localhost:8082/artifactory/api/plugins/reload
