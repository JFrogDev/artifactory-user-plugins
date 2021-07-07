/*
 * Copyright (C) 2019 JFrog Ltd.
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

/**
 * @author Ariel Kabov
 * version 23/07/2019 - 18:43
 */

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.apache.http.client.methods.*
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.commons.lang3.StringUtils

import org.artifactory.util.HttpUtils
import org.artifactory.common.ArtifactoryHome
import org.artifactory.common.crypto.CryptoHelper

replication {
    //Below we will configure the property key & value the plugin will watch.
    def propKey = "ShouldIreplicate"
    def propValue = "true"

    //repoList is the repository list the plugin is watching for.
    def repoList =["generic-local","generic-remote"]
    log.debug("Loaded repoList is: " + repoList)

    beforeDirectoryReplication { localRepoPath ->
        def repoKey = localRepoPath.getRepoKey()
        log.debug("Attempting directory replication from repo: " + repoKey + ", directory path is: " + localRepoPath)
        skip = shouldSkip(localRepoPath, repoKey, repoList, targetInfo, propKey, propValue)
    }

    beforeFileReplication { localRepoPath ->
        def repoKey = localRepoPath.getRepoKey()
        log.debug("Attempting file replication from repo: " + repoKey + ", file path is: " + localRepoPath)
        skip = shouldSkip(localRepoPath, repoKey, repoList, targetInfo, propKey, propValue)
    }
}


def shouldSkip(localRepoPath, repoKey, repoList, targetInfo, propKey, propValue) {
    if (StringUtils.endsWith(repoKey,"-cache") && repositories.getRemoteRepositories().contains(StringUtils.substringBeforeLast(repoKey, "-cache"))) {
        if (!(isRepoInList(StringUtils.substringBeforeLast(repoKey, "-cache"), repoList))) {
            return false
        }
    } else {
        if (!(isRepoInList(repoKey, repoList))) {
            return false
        }
    }
    if (repositories.getLocalRepositories().contains(repoKey)) {
        return (replicateLocal(localRepoPath, propKey, propValue))
    } else {
        return (replicateRemote(localRepoPath, repoKey, targetInfo, propKey, propValue))
    }
}

def isRepoInList(repoKey, repoList) {

    if (!repoList.contains(repoKey)) {
        log.debug("Item's repository is NOT in the repository list")
        return false
    } else {
        log.debug("Item's repository is in the repository list")
        return true
    }
}

def replicateLocal(localRepoPath, propKey, propValue) {
    boolean hasProperties = false
    def properties = repositories.getProperties(localRepoPath)
    log.debug("Items properties are: " + properties)
    properties.entries().each { item ->
        if (item.getKey().equals(propKey) && item.getValue().equals(propValue)) {
            hasProperties = true
        }
    }
    return (replicateResult(hasProperties, localRepoPath))
}

def replicateRemote(localRepoPath, repoKey, targetInfo, propKey, propValue) {
    boolean hasProperties = false

    def baseURL = StringUtils.substringBeforeLast(targetInfo.toString(), "/")
    def repoURL = StringUtils.substringAfterLast(targetInfo.toString(), "/")
    def repo = repositories.getRepositoryConfiguration(StringUtils.substringBeforeLast(repoKey,"-cache"))
    def username = repo.getUsername()
    def password = CryptoHelper.decryptIfNeeded(ArtifactoryHome.get(),repo.getPassword().toString())
    def encoded = "$username:$password".getBytes().encodeBase64().toString()
    def auth = "Basic $encoded"
    def targetRepoPath = URLEncoder.encode(repoURL + "/" + localRepoPath.getPath(),"UTF-8")
    def resp = remoteCall(propKey,baseURL,auth,targetRepoPath,null)
    def message = unwrapData('js', resp[0])
    def status = resp[1]
    switch (status){
        case 404:
            log.debug("Skipping replication of a file:" +
                " ${localRepoPath} as it does not have the" +
                " right property")
            return true
            break;
        case (!(200)):
            log.debug("Skipping replication of a file:" +
                " ${localRepoPath}, got http status ${status} as a response while checking for property")
            return true
            break;
    }

    def slurped = new JsonSlurper().parseText(message)
    def ShouldIreplicateList = slurped.properties."${propKey}"
    log.debug("content of property is:" + ShouldIreplicateList)
    if (ShouldIreplicateList.contains(propValue)) {
        hasProperties = true
    }
    return(replicateResult(hasProperties, localRepoPath))
}

def replicateResult(hasProperties,localRepoPath) {
    if(hasProperties){
        log.info("Replicating file: ${localRepoPath} as it has" +
                " the right property")
        return false
    } else {
        log.info("Skipping replication of a file:" +
                " ${localRepoPath} as it does not have the" +
                " right property")
        return true
    }
}

def remoteCall(propKey, baseURL, auth, targetRepoPath, data = wrapData('jo', null)) {
    def exurl = "$baseURL/api/storage/$targetRepoPath?properties=$propKey"
    log.debug("request goes to: " + exurl)
    try {
        def req = new HttpGet("$exurl")
        return makeRequest(req, auth, data, "application/json")
    } catch (Exception ex) {
        def writer = new StringWriter()
        writer.withPrintWriter { ex.printStackTrace(it) }
        log.error(writer.toString())
        return [wrapData('js', "Exception during call: $ex.message"), 500]
    }
}


def makeRequest(req, auth, data = null, ctype = null) {
    def resp = null, httpclient = HttpClients.createDefault()
    req.addHeader("User-Agent", HttpUtils.artifactoryUserAgent)
    req.addHeader("Authorization", auth)
    if (data) {
        req.addHeader("Content-Type", ctype)
        req.entity = new StringEntity(unwrapData('js', data))
    }
    try {
        resp = httpclient.execute(req)
        def ips = resp.entity.content
        def statusCode = resp.statusLine.statusCode
        return [wrapData('js', ips.text), statusCode]
    } catch (ex) {
        log.error("Problem making request: $ex.message")
        return [wrapData('js', "Problem making request: $ex.message"), 502]
    } finally {
        httpclient?.close()
        resp?.close()
    }
}

// Sometimes we generate json objects, other times we pull json strings from the
// database, other times we need to look inside a json object or write a string
// to the database. Sometimes we need to convert to or from a string to send
// over a network, and sometimes we don't. All of these systems interacting in
// various ways would require either many different special case handling
// scenarios, or else some unnecessary and potentially costly conversions
// between json object and string. This wrapper system allows us to avoid both
// of those issues. It works like so:
// - When a value is created, wrap it. This saves the value with its type, and
//   they will be passed around together wherever the value goes.
// - When you need to use a value, unwrap it. This converts the value to the
//   required type if necessary.
// Supported types are 'ji' (json input stream), 'js' (json string), and 'jo'
// (json object).

def wrapData(typ, data) {
    if (data && data instanceof Iterable && data[0] == 'datawrapper') {
        throw new RuntimeException("Cannot wrap wrapped data: $data")
    }
    if (!(typ in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $typ not recognized")
    }
    return ['datawrapper', typ, data]
}

def unwrapData(rtyp, wdata) {
    if (!wdata || !(wdata instanceof Iterable) || wdata[0] != 'datawrapper') {
        throw new RuntimeException("Cannot unwrap non-wrapped data: $wdata")
    }
    if (!(rtyp in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $rtyp not recognized")
    }
    def (kw, typ, data) = wdata
    if (!(typ in ['ji', 'js', 'jo'])) {
        throw new RuntimeException("Data type $typ not recognized")
    }
    if (typ == rtyp) return data
    if (typ == 'js' && rtyp == 'jo') {
        return new JsonSlurper().parseText(data)
    } else if (typ == 'jo' && rtyp == 'js') {
        return new JsonBuilder(data).toString()
    } else if (typ == 'ji' && rtyp == 'js') {
        return data.text
    } else if (typ == 'ji' && rtyp == 'jo') {
        return new JsonSlurper().parse(data)
    } else {
        throw new RuntimeException("Cannot convert $typ to $rtyp")
    }
}
