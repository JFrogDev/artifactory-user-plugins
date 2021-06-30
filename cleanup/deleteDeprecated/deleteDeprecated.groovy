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

import org.artifactory.repo.RepoPath

import static com.google.common.collect.Multimaps.forMap

/**
 *
 * @author jbaruch
 * @since 08/08/12
 */

// this is REST-executed plugin
// curl -X POST -v -u admin:password "http://localhost:8080/artifactory/api/plugins/execute/deleteDeprecatedPlugin"
executions {
    // this execution is named 'deleteDeprecated' and it will be called by REST by this name
    // map parameters provide extra information about this execution, such as version, description, users that allowed to call this plugin, etc.
    deleteDeprecatedPlugin(version: '1.0', description: 'Deletes artifacts marked with \'analysis.deprecated=true\' property', users: ['admin'].toSet()) {
        // we use the searched object to search for all items annotated with the desired property (hey, IntelliJ IDEA knows about the searches object and provides code completion and analysis!
        // It's thanks to this - https://github.com/JFrogDev/artifactory-user-plugins/blob/master/ArtifactoryUserPlugins.gdsl)
        List<RepoPath> paths = searches.itemsByProperties(forMap(['analysis.deprecated': true.toString()]))
        paths.each {
            // just delete whatever found
            repositories.delete it
            // now let's delete if some directories became empty
            deleteEmptyDirs it.parent
            // uncomment next line to enable throttling when deleting artifacts to 1 deletion per 1000ms, change value to slow down further.
            // sleep( 1000 )
        }
    }
}

def deleteEmptyDirs(RepoPath path) {
    def parent = path.parent
    if (repositories.getChildren(path).empty) {
        repositories.delete path
    }
    if (!parent.root) {
        deleteEmptyDirs parent
    }
}
