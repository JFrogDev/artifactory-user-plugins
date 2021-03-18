import groovy.transform.Field
import org.artifactory.common.ConstantValues
import org.artifactory.state.ArtifactoryServerState
import org.artifactory.storage.db.servers.model.ArtifactoryServer
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService
import org.slf4j.Logger

import java.util.concurrent.TimeUnit

@Field
ArtifactoryServersCommonService artifactoryServersCommonService = ctx.beanForType(ArtifactoryServersCommonService)

@Field
ArtifactoryInactiveServersCleaner artifactoryInactiveServerCleaner = new ArtifactoryInactiveServersCleaner(artifactoryServersCommonService, log)

jobs {
    // Run every 90 seconds; don't run for the first 15 minutes after boot.
    //
    // NOTE: the 'delay' period here only applies when booting the Artifactory
    // leader/primary node; this has nothing to do with the race condition when
    // booting a secondary/member node.
    clean(interval: 90*1000, delay: 15*60*1000) {
        runCleanupHAInactiveServers()
    }
}

executions {
    // Execution that can be manually triggered
    cleanHAInactiveServers() { params ->
        runCleanupHAInactiveServers()
    }
}

void runCleanupHAInactiveServers() {
    artifactoryInactiveServerCleaner.cleanInactiveArtifactoryServers()
}

class ArtifactoryInactiveServersCleaner {

    private ArtifactoryServersCommonService artifactoryServersCommonService
    private Logger log

    ArtifactoryInactiveServersCleaner(ArtifactoryServersCommonService artifactoryServersCommonService, Logger log) {
        this.artifactoryServersCommonService = artifactoryServersCommonService
        this.log = log
    }

    def cleanInactiveArtifactoryServers() {
        long cleaned = 0
        log.info "Executing inactive artifactory servers cleaner plugin"

        List<ArtifactoryServer> allMembers = artifactoryServersCommonService.getAllArtifactoryServers()
        for (member in allMembers) {
            // First, get data about the current server:
            // 1. Start time
            long startGracePeriod = 5 * 60
            long timeSinceStart = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - member.getStartTime())

            // 2. Heartbeat time
            long timeSinceLastHeartbeat = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - member.getLastHeartbeat())

            // Helpful for debugging; uncomment if necessary
            //log.info "[${member.serverId}] state=${member.getServerState()} timeSinceStart=${timeSinceStart} timeSinceLastHeartbeat=${timeSinceLastHeartbeat}"

            boolean shouldRemove = false

            // If the server is unavailable, always remove it.
            if (member.getServerState() == ArtifactoryServerState.UNAVAILABLE) {
                log.debug "[${member.serverId}] Will remove because server is unavailable"
                shouldRemove = true
            }

            // Remove a server if it hasn't heartbeated within the 'stale'
            // interval and isn't either:
            //   (a)  starting up
            //   (b) "converting" from a standalone to HA server
            int heartbeatStaleInterval = ConstantValues.haHeartbeatStaleIntervalSecs.getInt()
            boolean noHeartbeat = timeSinceLastHeartbeat > heartbeatStaleInterval
            if (noHeartbeat && member.getServerState() != ArtifactoryServerState.CONVERTING && member.getServerState() != ArtifactoryServerState.STARTING) {
                log.debug "[${member.serverId}] Will remove because server has not heartbeated in ${heartbeatStaleInterval} seconds and is in state: ${member.getServerState()}"
                shouldRemove = true
            }

            // Skip servers that have started within the past 5 minutes by
            // forcing 'shouldRemove' to false, to allow them to boot and
            // settle down; without this, we get spurious removals that break
            // when starting new instances after a cluster has already started.
            if (timeSinceStart < startGracePeriod) {
                log.debug "[${member.serverId}] Skipping since server recently started (${timeSinceStart} < ${startGracePeriod})"
                shouldRemove = false
            }

            if (shouldRemove) {
                try {
                    log.info "[${member.serverId}] Inactive Artifactory servers cleaning task found server to remove"
                    artifactoryServersCommonService.removeServer(member.serverId)
                    cleaned += 1
                } catch (Exception e) {
                    log.error "[${member.serverId}] Error: Not able to remove: ${e.message}"
                }
            }
        }

        log.info "Cleaned ${cleaned} inactive servers"
    }
}
