#!/bin/bash

# 1. Create the image
docker build --tag host.docker.internal:8081/test-docker/test-image:latest . -f Dockerfile

# 2. Login to local Artifactory
docker login host.docker.internal:8081 -u admin -p password

# 3. Push to local Artifactory
docker push host.docker.internal:8081/test-docker/test-image:latest

# 4. Remove local image
docker image rm host.docker.internal:8081/test-docker/test-image
