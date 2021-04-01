/*
 * Copyright (C) 2016 JFrog Ltd.
 * Maintained by Shikhar Rawat, shikharr@jfrog.com.
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

import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.api.module.ModuleInfo
import org.artifactory.api.module.ModuleInfoUtils
import org.artifactory.maven.MavenModelUtils
import org.artifactory.repo.RepoPathFactory
import org.artifactory.repo.service.InternalRepositoryService
import org.artifactory.storage.db.util.JdbcHelper
import org.artifactory.util.RepoLayoutUtils
import org.artifactory.resource.ResourceStreamHandle
import com.google.common.collect.HashMultimap
import groovy.json.JsonSlurper
import groovy.json.JsonException
import org.codehaus.groovy.runtime.typehandling.GroovyCastException

storage {
  afterCreate { item ->
    // when an artifact is created, make a pom file for it
    def exclusionList = readExclusionList()
    def ext = getExtension(item.repoPath)
    if (checkRepo(item.repoKey) && !exclusionList.contains(ext) && greylistCheck(item)) {
      upload(item)
    }
  }
}

executions {
  pommify() { params, ResourceStreamHandle body ->
    // retrieve the provided list of repositories to pommify
    def repos = params?.getAt('repos')
    def bodyJson
    if (body.size > 0) {
        bodyJson = new JsonSlurper().parse(body.inputStream)
    }
    String[] blacklist = bodyJson?.blacklist ?: []

    // if the list is empty or nonexistant, return a 400
    if (!repos) {
      status = 400
      message = "Parameter 'repos' not present or empty"
      return
    }
    // if any repository on the list can't be checked, return a 400
    for (repo in repos) {
      if (!checkRepo(repo)) {
        status = 400
        message = "Repository '$repo' does not exist or is not a local Maven 2"
        return
      }
    }
    // otherwise, pommify each repository in turn, and return success
    def exclusionList = readExclusionList()

    for (repo in repos) {
      pommifyRepo(repo, exclusionList, blacklist)
    }
    status = 200
    message = "Successfully added poms to all specified repositories"
  }
}

def readExclusionList() {
  def etcdir = ctx.artifactoryHome.etcDir
  // This function reads the file and creates an arraylist that can be compared against the uploaded items.
  try {
    def extfile = new File(etcdir, "plugins/pommer.json")
    def jsonResult = new JsonSlurper().parse(extfile)
    // Add extensions from the file to the arraylist
    def extensions = jsonResult.fileExtensions
    if (extensions == null) return ["pom"]
    extensions << "pom"
    log.debug("json found, returning exlusionlist")
    return extensions
    // catch IO or JSON exception and create a list with pom only
  } catch (FileNotFoundException | JsonException ex) {
    log.warn("file not found or json exception, checking against .pom extension only")
    return ["pom"]
  }
}

def getExtension(item) {
  // get the extension for the item to be uploaded
  def file = new File(item.path).name
  def dotidx = file.lastIndexOf('.')
  if (dotidx < 0) {
    log.debug("no extension found")
    return ''
  }
  return file[dotidx + 1 .. -1]
}

// given a repository name, create pom files for all artifacts in the repository
def pommifyRepo(repo, exclusionList, blacklist) {
  // a multimap of module ids -> module infos
  def modules = HashMultimap.create()
  def reposerv = ctx.beanForType(InternalRepositoryService)
  // iterate over each file in the given repository
  for (node in getNodesList(repo)) {
    // if the file is a maven metdata, we don't care about it
    if (node.endsWith('/maven-metadata.xml')) continue
    // check extension of file and comapre it with list from JSON file
    def ext = getExtension(RepoPathFactory.create(repo, node))
    if (exclusionList.contains(ext)) continue
    // if the file does not follow the layout, we don't care about it
    def repopath = RepoPathFactory.create(repo, node)
    //check blacklist
    if (listcheck(blacklist, repopath.getRepoKey(), repopath.getPath())) continue
    def modinf = reposerv.getItemModuleInfo(repopath)
    if (!modinf.isValid()) continue
    // otherwise, add the file to the multimap
    modules.put(modinf.prettyModuleId, [repopath, modinf])
  }
  // iterate over each module id in the multimap
  modules.asMap().each { key, values ->
    // if there is already a pom file at this location, don't bother
    def item = repositories.getItemInfo(values[0][0])
    // otherwise, make a pom file
    upload(item)
  }
}

// query the database and select a list of all files in a repository
def getNodesList(repo) {
  def rs = null, nodelist = []
  def jdbcHelper = ctx.beanForType(JdbcHelper)
  // get the path of every file in the given repo
  def query = 'SELECT node_path, node_name FROM nodes'
  query += ' WHERE repo = ? AND node_type = 1'
  try {
    // execute the query and get the result set
    rs = jdbcHelper.executeSelect(query, repo)
    // add each result to the node list, as a file path
    while (rs.next()) {
      def path = rs.getString(1)
      if (path == '.') path = ''
      nodelist << path + '/' + rs.getString(2)
    }
  } finally {
    // close the result set
    if (rs) {
        try {
            ("org.artifactory.storage.db.util.DbUtils" as Class).close(rs)
        } catch (GroovyCastException e) {
            ("org.jfrog.storage.util.DbUtils" as Class).close(rs)
        }
    }
  }
  // return the completed node list
  return nodelist
}

// ensure that the given repository is a local maven 2 repository
def checkRepo(reponame) {
  def repo = repositories.getRepositoryConfiguration(reponame)
  if (!repo) return false
  if (repo.type != 'local' || repo.packageType != 'maven') return false
  if (repo.repoLayoutRef != 'maven-2-default') return false
  return true
}

// given an ItemInfo object, create and deploy a pom file for that artifact
def upload(item) {
  // extract the maven data from the path
  def layout = RepoLayoutUtils.MAVEN_2_DEFAULT
  def modinf = ModuleInfoUtils.moduleInfoFromArtifactPath(item.relPath, layout)
  if (!modinf.isValid()) return
  // build a path to the pom file
  def pompath = ModuleInfoUtils.constructDescriptorPath(modinf, layout, true)
  def pom = RepoPathFactory.create(item.repoKey, pompath)
  if (repositories.exists(pom)) return
  // build and deploy the pom file
  def info = MavenArtifactInfo.fromRepoPath(item.repoPath)
  def model = MavenModelUtils.toMavenModel(info)
  def pomstring = MavenModelUtils.mavenModelToString(model)
  log.info("Deploying pom file to '{}'", pom)
  repositories.deploy(pom, new ByteArrayInputStream(pomstring.bytes))
}

def greylistCheck(item) {
  def etcdir = ctx.artifactoryHome.etcDir
  def extfile
  def jsonResult

  extfile = new File(etcdir, "plugins/pommer.json")
  if (!extfile.canRead()) {
    return true
  }
  jsonResult = new JsonSlurper().parse(extfile)

  def blacklist = jsonResult.blacklist
  def whitelist = jsonResult.whitelist

  String pathUpload = item.repoPath.getPath()
  String keyUpload  = item.repoPath.getRepoKey()

  if (listcheck(blacklist, keyUpload, pathUpload)) {
    return false
  }
  if (listcheck(whitelist, keyUpload, pathUpload)) {
    return true
  }
  if (whitelist != null) {
    return false
  }
  return true
}

def listcheck(list, String keyUpload, String pathUpload) {
  String pathList = ""
  String keyList  = ""
  if (list != null) {
    for (String i : list) {
      if (i == keyUpload) {
        return true
      }
      if (i.contains(":")) {
        def expl = i.split(":")
        keyList = expl[0] 
        pathList = expl[1]
        if ((keyList == keyUpload || keyList == "*") && pathUpload.startsWith(pathList)) {
          return true
        }
      }
    }
  }
  return false
}
