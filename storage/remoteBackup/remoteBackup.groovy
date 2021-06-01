/*
 * Copyright (C) 2017 JFrog Ltd.
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
import groovy.json.JsonSlurper
import groovy.json.JsonBuilder

import org.artifactory.repo.RepoPathFactory

@Field final String REMOTE_BACKUP = "plugins/remoteBackup.json"

// A cron expression describing how often to run the backup. Modify this
// variable to customize the timing.
cronExpression = "0 0 0/1 * * ?"

jobs {
  remoteBackupJob(cron: cronExpression) {
    runBackup(null)
  }
}

executions {
  remoteBackup() { params ->
    def repos = params?.getAt('repos') ?: null
    def (complete, total) = runBackup(repos)
    if (complete instanceof CharSequence) {
      message = "Error copying items: $complete"
      status = 400
    } else if (complete < total) {
      message = "Successfully copied $complete of $total items"
    } else {
      message = "Successfully copied all $total items"
    }
  }
}

storage {
  afterCreate { item ->
    def etcdir = ctx.artifactoryHome.etcDir
    def cfgfile = new File(etcdir, REMOTE_BACKUP)
    def cfg = new JsonSlurper().parse(cfgfile)
    if (item.repoKey in cfg && !item.isFolder()) {
      asSystem {
        def dest = cfg[item.repoKey]
        try {
          def destpath = RepoPathFactory.create(dest, item.relPath)
          repositories.copy(item.repoPath, destpath)
        } catch (Exception ex) {
          log.warn("Unable to backup $item.repoPath to $dest: $ex.message")
        }
      }
    }
  }

  afterPropertyCreate { item, name, values ->
    def etcdir = ctx.artifactoryHome.etcDir
    def cfgfile = new File(etcdir, REMOTE_BACKUP)

    // Ensure invalid or missing JSON config does not prevent Artifactory
    // from serving and receiving artifacts.
    //
    cfg = parseJson(cfgfile)
    if (!cfg) return

    if (item.repoKey in cfg && !item.isFolder() && name != "artifactory.internal.etag") {
      asSystem {
        def dest = cfg[item.repoKey]
        try {
          // Set property in archive repo
          def destpath = RepoPathFactory.create(dest, item.relPath)
          repositories.setProperty(destpath, name, values)
        } catch (Exception ex) {
          log.warn("Unable to set property $name for $destpath: $ex.message")
        }
      }
    }
  }
}

def runBackup(repos) {
  def etcdir = ctx.artifactoryHome.etcDir
  def cfgfile = new File(etcdir, REMOTE_BACKUP)
  def cfg = new JsonSlurper().parse(cfgfile)
  if (repos) {
    def cfgtmp = [:]
    for (repo in repos) {
      if (repo in cfg) cfgtmp[repo] = cfg[repo]
      else return ["Repo $repo not in configuration"]
    }
    cfg = cfgtmp
  }
  def complete = 0, total = 0
  for (repopair in cfg.entrySet()) {
    def src = repopair.key, dest = repopair.value
    def quer = new JsonBuilder([type: 'file', repo: src]).toString()
    def aql = "items.find($quer).include(\"repo\",\"path\",\"name\")"
    searches.aql(aql.toString()) {
      for (item in it) {
        def path = item.path + '/' + item.name
        if (item.path == '.') path = item.name
        def srcpath = RepoPathFactory.create(src, path)
        def destpath = RepoPathFactory.create(dest, path)
        if (repositories.exists(srcpath) &&
            (!repositories.exists(destpath) ||
             (repositories.getFileInfo(srcpath).checksumsInfo.sha1 !=
              repositories.getFileInfo(destpath).checksumsInfo.sha1))) {
          total += 1
          try {
            repositories.copy(srcpath, destpath)
            complete += 1
          } catch (Exception ex) {
            log.warn("Unable to backup $srcpath to $dest: $ex.message")
          }
        }
      }
    }
  }
  return [complete, total]
}
