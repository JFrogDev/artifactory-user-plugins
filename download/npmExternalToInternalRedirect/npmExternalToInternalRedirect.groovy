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
import org.artifactory.repo.RepoPathFactory


download {

    altRemoteContent { repoPath ->
        String repoKey = repoPath.getRepoKey()
        String localNpmRepo = "npm-local";
        RepoPath newPath = null;
        boolean foundInLocal = false;
        def repo = repositories.getRepositoryConfiguration(repoKey)
        //change it to match only the external npm name if needed
        if (repo.type == "remote" && repo.packageType == "npm" ) {

            //Check if its available in local repo
            newPath = RepoPathFactory.create(localNpmRepo, repoPath.path)
            if (repositories.exists(newPath)) {
                log.info "Local repository path exist... ${newPath.toPath()}"
                foundInLocal = true
            }
        }

        if (foundInLocal && newPath) {
            log.info "Local package found return that ${newPath.toPath()}"
            def stream = null, localNpm = null
            try {
                stream = repositories.getContent(newPath)
                localNpm = stream.inputStream.bytes
            } catch (Exception ex) {
                log.warn "EXCEPTION : ${ex.getMessage()}"
            }
            finally {
                stream?.close()
            }
            if (localNpm && stream) {
                size = localNpm.length
                inputStream = new ByteArrayInputStream(localNpm)
            }
        }
    }
}