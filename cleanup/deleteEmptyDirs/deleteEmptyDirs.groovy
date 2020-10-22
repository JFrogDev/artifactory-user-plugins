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

import groovy.transform.Field
import org.artifactory.repo.RepoPath

import static java.lang.Thread.sleep
import static org.artifactory.repo.RepoPathFactory.create

@Field final String PROPERTIES_FILE_PATH = "plugins/${this.class.name}.properties"

/**
 *
 * @author jbaruch
 * @since 16/08/12
 */

// this is REST-executed plugin
executions {
    // this execution is named 'deleteEmptyDirs' and it will be called by REST by this name
    // map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    // The expected (and mandatory) parameter is comma separated list of paths from which empty folders will be searched.
    // curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteEmptyDirsPlugin?params=paths=repo,otherRepo/some/path"
    deleteEmptyDirsPlugin(version: '1.1', description: 'Deletes empty directories', users: ['admin'].toSet()) { params ->
        if (!params || !params.paths) {
            def errorMessage = 'Paths parameter is mandatory, please supply it.'
            log.error errorMessage
            status = 400
            message = errorMessage
        } else {
            deleteEmptyDirectories(params.paths as String[])
        }
    }
}

private def insertAllRepositories(ArrayList paths) {
        repositories.getLocalRepositories().each {
            paths.add(it)
        }

        repositories.getRemoteRepositories().each {
            paths.add(it)
        }

        repositories.getVirtualRepositories().each {
            paths.add(it)
        }
}

def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.etcDir, PROPERTIES_FILE_PATH).toURI().toURL())
log.info "Schedule job policy list: $config.policies"

config.policies.each{ policySettings ->
    def cron = policySettings[ 0 ] ? policySettings[ 0 ] as String : ["0 0 5 ? * 1"]
    def paths = policySettings[ 1 ] ? policySettings[ 1 ] as String[] : ["__none__"]

    if (paths[0] == "__all__") {
        paths = []
        insertAllRepositories(paths)
    }

    jobs {
        "scheduledDeleteEmptyDirs_$cron"(cron: cron) {
            log.info "Policy settings for scheduled run at($cron): path list($paths)"
            deleteEmptyDirectories( paths as String[])
        }
    }
}

private def deleteEmptyDirectories(String[] paths) {
    def totalDeletedDirs = 0
    paths.each {
        log.info "Beginning deleting empty directories for path($it)"
        def deletedDirs = deleteEmptyDirsRecursively create(it)
        log.info "Deleted($deletedDirs) empty directories for given path($it)"
        totalDeletedDirs += deletedDirs
    }
    log.info "Finished deleting total($totalDeletedDirs) directories"
}

def deleteEmptyDirsRecursively(RepoPath path) {
    def deletedDirs = 0
    // let's let other threads to do something.
    sleep 50
    // if not folder - we're done, nothing to do here
    if (repositories.getItemInfo(path).folder) {
        def children = repositories.getChildren path
        children.each {
            deletedDirs += deleteEmptyDirsRecursively it.repoPath
        }
        // now let's check again
        if (repositories.getChildren(path).empty) {
            // it is folder, and no children - delete!
            log.info "Deleting empty directory($path)"
            repositories.delete path
            deletedDirs += 1
        }
    }

    return deletedDirs
}
