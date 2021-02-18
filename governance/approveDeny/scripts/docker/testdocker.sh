#!/bin/bash

docker rm --force test
docker image rm host.docker.internal:8081/test-docker/test-image
docker run -it --name test host.docker.internal:8081/test-docker/test-image:latest
