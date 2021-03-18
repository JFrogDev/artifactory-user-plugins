# Artifactory HA Inactive Server Cleaner User Plugin

Removes unavailable instances from the nodes list of an Artifactory cluster.

## Features

This plugin implements a job that runs every 90 seconds. The job execution starts
15 minutes after the Artifactory instance comes up.

For every execution, the plugin will get the list on nodes members of the cluster
and remove the ones that are unavailable. It will skip nodes in the cluster
that have started within the past 5 minutes, to ensure that nodes that are
starting are not removed before they fully start.

## Installation

Place plugin file under `${ARTIFACTORY_HOME}/etc/plugins/` in the primary node
of your Artifactory cluster.

## Usage

This plugin runs automatically every 90 seconds.
