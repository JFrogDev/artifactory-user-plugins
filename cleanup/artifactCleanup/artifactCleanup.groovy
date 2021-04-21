/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.commons.lang3.StringUtils
import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException
import org.artifactory.exception.CancelException

import groovy.json.JsonSlurper
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.transform.Field

import java.text.SimpleDateFormat

@Field final String CONFIG_FILE_PATH = "plugins/${this.class.name}.json"
@Field final String PROPERTIES_FILE_PATH = "plugins/${this.class.name}.properties"
@Field final String DEFAULT_TIME_UNIT = "month"
@Field final int DEFAULT_TIME_INTERVAL = 1

class Global {
    static Boolean stopCleaning = false
    static Boolean pauseCleaning = false
    static int paceTimeMS = 0
}

// curl command example for running this plugin (Prior to Artifactory 5.x, use pipe '|' and not semi-colons ';' for parameters separation).
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanup?params=timeUnit=day;timeInterval=1;repos=libs-release-local;dryRun=true;paceTimeMS=2000;disablePropertiesSupport=true"
//
// For a HA cluster, the following commands have to be directed at the instance running the script. Therefore it is best to invoke
// the script directly on an instance so the below commands can operate on same instance
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=pause"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=resume"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=stop"
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanupCtl?params=command=adjustPaceTimeMS;value=-1000"

def pluginGroup = 'cleaners'

executions {
    cleanup(groups: [pluginGroup]) { params ->
        def timeUnit = params['timeUnit'] ? params['timeUnit'][0] as String : DEFAULT_TIME_UNIT
        def timeInterval = params['timeInterval'] ? params['timeInterval'][0] as int : DEFAULT_TIME_INTERVAL
        def repos = params['repos'] ? params['repos'] as String[] : ["__none__"] as String[]
        def dryRun = params['dryRun'] ? new Boolean(params['dryRun'][0]) : false
        def disablePropertiesSupport = params['disablePropertiesSupport'] ? new Boolean(params['disablePropertiesSupport'][0]) : false
        def paceTimeMS = params['paceTimeMS'] ? params['paceTimeMS'][0] as int : 0

        // Enable fallback support for deprecated month parameter
        if ( params['months'] && !params['timeInterval'] ) {
            log.info('Deprecated month parameter is still in use, please use the new timeInterval parameter instead!', properties)
            timeInterval = params['months'][0] as int
        } else if ( params['months'] ) {
            log.warn('Deprecated month parameter and the new timeInterval are used in parallel: month has been ignored.', properties)
        }

        artifactCleanup(timeUnit, timeInterval, repos, log, paceTimeMS, dryRun, disablePropertiesSupport)
    }

    cleanupCtl(groups: [pluginGroup]) { params ->
        def command = params['command'] ? params['command'][0] as String : ''

        switch ( command ) {
            case "stop":
                Global.stopCleaning = true
                log.info "Stop request detected"
                break
            case "adjustPaceTimeMS":
                def adjustPaceTimeMS = params['value'] ? params['value'][0] as int : 0
                def newPaceTimeMS = ((Global.paceTimeMS + adjustPaceTimeMS) > 0) ? (Global.paceTimeMS + adjustPaceTimeMS) : 0
                log.info "Pacing adjustment request detected, adjusting old pace time ($Global.paceTimeMS) by $adjustPaceTimeMS to new value of $newPaceTimeMS"
                Global.paceTimeMS = newPaceTimeMS
                break
            case "pause":
                Global.pauseCleaning = true
                log.info "Pause request detected"
                break
            case "resume":
                Global.pauseCleaning = false
                log.info "Resume request detected"
                break
            default:
                log.info "Missing or invalid command, '$command'"
        }
    }
}

def deprecatedConfigFile = new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH)
def configFile = new File(ctx.artifactoryHome.etcDir, CONFIG_FILE_PATH)

if ( deprecatedConfigFile.exists() ) {

    if ( !configFile.exists() ) {
        def config = new ConfigSlurper().parse(deprecatedConfigFile.toURL())
        log.info "Schedule job policy list: $config.policies"

        def count=1
        config.policies.each{ policySettings ->
            def cron = policySettings[ 0 ] ? policySettings[ 0 ] as String : ["0 0 5 ? * 1"]
            def repos = policySettings[ 1 ] ? policySettings[ 1 ] as String[] : ["__none__"]
            def months = policySettings[ 2 ] ? policySettings[ 2 ] as int : 6
            def paceTimeMS = policySettings[ 3 ] ? policySettings[ 3 ] as int : 0
            def dryRun = policySettings[ 4 ] ? policySettings[ 4 ] as Boolean : false
            def disablePropertiesSupport = policySettings[ 5 ] ? policySettings[ 5 ] as Boolean : false

            jobs {
                "scheduledCleanup_$count"(cron: cron) {
                    log.info "Policy settings for scheduled run at($cron): repo list($repos), timeUnit(month), timeInterval($months), paceTimeMS($paceTimeMS) dryrun($dryRun) disablePropertiesSupport($disablePropertiesSupport)"
                    artifactCleanup( "month", months, repos, log, paceTimeMS, dryRun, disablePropertiesSupport )
                }
            }
            count++
        }
    } else  {
        log.warn "Deprecated 'artifactCleanup.properties' file is still present, but ignored. You should remove the file."
    }
}

if ( configFile.exists() ) {
  
    def config = new JsonSlurper().parse(configFile.toURL())
    log.info "Schedule job policy list: $config.policies"

    def count=1
    config.policies.each{ policySettings ->
        def cron = policySettings.containsKey("cron") ? policySettings.cron as String : ["0 0 5 ? * 1"]
        def repos = policySettings.containsKey("repos") ? policySettings.repos as String[] : ["__none__"]
        def timeUnit = policySettings.containsKey("timeUnit") ? policySettings.timeUnit as String : DEFAULT_TIME_UNIT
        def timeInterval = policySettings.containsKey("timeInterval") ? policySettings.timeInterval as int : DEFAULT_TIME_INTERVAL
        def paceTimeMS = policySettings.containsKey("paceTimeMS") ? policySettings.paceTimeMS as int : 0
        def dryRun = policySettings.containsKey("dryRun") ? new Boolean(policySettings.dryRun) : false
        def disablePropertiesSupport = policySettings.containsKey("disablePropertiesSupport") ? new Boolean(policySettings.disablePropertiesSupport) : false

        jobs {
            "scheduledCleanup_$count"(cron: cron) {
                log.info "Policy settings for scheduled run at($cron): repo list($repos), timeUnit($timeUnit), timeInterval($timeInterval), paceTimeMS($paceTimeMS) dryrun($dryRun) disablePropertiesSupport($disablePropertiesSupport)"
                artifactCleanup( timeUnit, timeInterval, repos, log, paceTimeMS, dryRun, disablePropertiesSupport )
            }
        }
        count++
    }  
}

if ( deprecatedConfigFile.exists() && configFile.exists() ) {
    log.warn "The deprecated artifactCleanup.properties and the new artifactCleanup.json are defined in parallel. You should migrate the old file and remove it."
}

private def artifactCleanup(String timeUnit, int timeInterval, String[] repos, log, paceTimeMS, dryRun = false, disablePropertiesSupport = false) {
    log.info "Starting artifact cleanup for repositories $repos, until $timeInterval ${timeUnit}s ago with pacing interval $paceTimeMS ms, dryrun: $dryRun, disablePropertiesSupport: $disablePropertiesSupport"

    // Create Map(repo, paths) of skiped paths (or others properties supported in future ...)
    def skip = [:]
    if ( ! disablePropertiesSupport && repos){
        skip = getSkippedPaths(repos)
    }

    def calendarUntil = Calendar.getInstance()

    calendarUntil.add(mapTimeUnitToCalendar(timeUnit), -timeInterval)

    def calendarUntilFormatted = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(calendarUntil.getTime());
    log.info "Removing all artifacts not downloaded since $calendarUntilFormatted"

    Global.stopCleaning = false
    int cntFoundArtifacts = 0
    int cntNoDeletePermissions = 0
    long bytesFound = 0
    long bytesFoundWithNoDeletePermission = 0
    def artifactsCleanedUp = searches.artifactsNotDownloadedSince(calendarUntil, calendarUntil, repos)
    artifactsCleanedUp.find {
        try {
            while ( Global.pauseCleaning ) {
                log.info "Pausing by request"
                sleep( 60000 )
            }

            if ( Global.stopCleaning ) {
                log.info "Stopping by request, ending loop"
                return true
            }

            if ( ! disablePropertiesSupport && skip[ it.repoKey ] && StringUtils.startsWithAny(it.path, skip[ it.repoKey ])){
                if (log.isDebugEnabled()){
                    log.debug "Skip $it"
                }
                return false
            }

            bytesFound += repositories.getItemInfo(it)?.getSize()
            cntFoundArtifacts++
            if (!security.canDelete(it)) {
                bytesFoundWithNoDeletePermission += repositories.getItemInfo(it)?.getSize()
                cntNoDeletePermissions++
            }
            if (dryRun) {
                log.info "Found $it, $cntFoundArtifacts/$artifactsCleanedUp.size total $bytesFound bytes"
                log.info "\t==> currentUser: ${security.currentUser().getUsername()}"
                log.info "\t==> canDelete: ${security.canDelete(it)}"

            } else {
                if (security.canDelete(it)) {
                    log.info "Deleting $it, $cntFoundArtifacts/$artifactsCleanedUp.size total $bytesFound bytes"
                    repositories.delete it
                } else {
                    log.info "Can't delete $it (user ${security.currentUser().getUsername()} has no delete permissions), " +
                            "$cntFoundArtifacts/$artifactsCleanedUp.size total $bytesFound bytes"
                }
            }
        } catch (ItemNotFoundRuntimeException ex) {
            log.info "Failed to find $it, skipping"
        }

        def sleepTime = (Global.paceTimeMS > 0) ? Global.paceTimeMS : paceTimeMS
        if (sleepTime > 0) {
            sleep( sleepTime )
        }

        return false
    }

    if (dryRun) {
        log.info "Dry run - nothing deleted. Found $cntFoundArtifacts artifacts consuming $bytesFound bytes"
        if (cntNoDeletePermissions > 0) {
            log.info "$cntNoDeletePermissions artifacts cannot be deleted due to lack of permissions ($bytesFoundWithNoDeletePermission bytes)"
        }
    } else {
        log.info "Finished cleanup, deleting $cntFoundArtifacts artifacts that took up $bytesFound bytes"
        if (cntNoDeletePermissions > 0) {
            log.info "$cntNoDeletePermissions artifacts could not be deleted due to lack of permissions ($bytesFoundWithNoDeletePermission bytes)"
        }
    }
}

private def getSkippedPaths(String[] repos) {
    def timeStart = new Date()
    def skip = [:]
    for (String repoKey : repos){
        def pathsTmp = []
        def aql = "items.find({\"repo\":\"" + repoKey + "\",\"type\": \"any\",\"@cleanup.skip\":\"true\"}).include(\"repo\", \"path\", \"name\", \"type\")"
        searches.aql(aql.toString()) {
            for (item in it) {
                def path = item.path + '/' + item.name
                // Root path case behavior
                if ('.' == item.path){
                    path = item.name
                }
                if ('folder' == item.type){
                    path += '/'
                }
                if (log.isTraceEnabled()){
                    log.trace "skip found for " + repoKey + ":" + path
                }
                pathsTmp.add(path)
            }
        }

        // Simplify list to have only parent paths
        def paths = []
        for (path in pathsTmp.sort{ it }) {
            if (paths.size == 0 || ! path.startsWith(paths[-1])) {
                if (log.isTraceEnabled()){
                    log.trace "skip added for " + repoKey + ":" + path
                }
                paths.add(path)
            }
        }

        if (paths.size > 0){
            skip[repoKey] = paths.toArray(new String[paths.size])
        }
    }
    def timeStop = new Date()
    TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
    log.info "Elapsed time to retrieve paths to skip: " + duration
    return skip
}

private def mapTimeUnitToCalendar (String timeUnit) {
    switch ( timeUnit ) {
        case "minute":
            return Calendar.MINUTE
        case "hour":
            return Calendar.HOUR
        case "day":
            return Calendar.DAY_OF_YEAR
        case "month":
            return Calendar.MONTH
        case "year":
            return Calendar.YEAR
        default:
            def errorMessage = "$timeUnit is no valid time unit. Please check your request or scheduled policy."
            log.error errorMessage
            throw new CancelException(errorMessage, 400)
    }
}
