#!/bin/bash

docker run -it --name rt1 -p 8081:8081 -p 8082:8082 -p 5555:5555 -e "JAVA_TOOL_OPTIONS=\"-agentlib:jdwp=transport=dt_socket,address=*:5555,server=y,suspend=n\"" docker.bintray.io/jfrog/artifactory-pro:latest /bin/bash
