/*
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2012 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 */



//import org.jfrog.build.api.release.Promotion


import groovy.xml.StreamingMarkupBuilder
import org.artifactory.build.Artifact
import org.artifactory.build.BuildRun
import org.artifactory.build.Dependency
import org.artifactory.build.DetailedBuildRun
import org.artifactory.build.Module
import org.artifactory.build.ReleaseStatus
import org.artifactory.common.StatusHolder
import org.artifactory.exception.CancelException
import org.artifactory.fs.FileInfo
import org.artifactory.repo.RepoPath
import org.artifactory.repo.RepoPathFactory
import org.artifactory.util.StringInputStream

import static groovy.xml.XmlUtil.serialize

promotions {
    /**
     * A REST executable build promotion definition.
     *
     * Context variables:
     * status (int) - a response status code. Defaults to -1 (unset).
     * message (java.lang.String) - a text message to return in the response body, replacing the response content. Defaults to null.
     *
     * Plugin info annotation parameters:
     * version (java.lang.String) - Closure version. Optional.
     * description (java.lang.String - Closure description. Optional.
     * params (java.util.Map<java.lang.String, java.lang.String>) - Closure parameters. Optional.
     * users (java.util.Set<java.lang.String>) - Users permitted to query this plugin for information or invoke it.
     * groups (java.util.Set<java.lang.String>) - Groups permitted to query this plugin for information or invoke it.
     *
     * Closure parameters:
     * buildName (java.lang.String) - The build name specified in the REST request.
     * buildNumber (java.lang.String) - The build number specified in the REST request.
     * params (java.util.Map<java.lang.String, java.util.List<java.lang.String>>) - The parameters specified in the REST request.
     */
    snapshotToRelease(users: "jenkins", params: [snapExp: 'd14', targetRepository: 'gradle-release-local']) { buildName, buildNumber, params ->
        echo('Promoting build: ' + buildName + '/' + buildNumber)

        //1. Extract properties
        buildStartTime = getStringProperty(params, 'buildStartTime', false)
        String snapExp = getStringProperty(params, 'snapExp', true)
        String targetRepository = getStringProperty(params, 'targetRepository', true)
        //2. Get Stage build information by name/number
        //Sanity check
        List<BuildRun> buildsRun = builds.getBuilds(buildName, buildNumber, buildStartTime)
        if (buildsRun.size() > 1) cancelPromotion('Found two matching build to promote, please provide build start time', null, 409)

        def buildRun = buildsRun[0]
        if (buildRun == null) cancelPromotion("Build $buildName/$buildNumber was not found, canceling promotion", null, 409)
        DetailedBuildRun stageBuild = builds.getDetailedBuild(buildRun)
        Set<FileInfo> stageArtifactsList = builds.getArtifactFiles(buildRun)


        //3. Prepare release DetailedBuildRun and release artifacts for deployment
        releaseBuild = stageBuild.copy("$stageBuild.number-r")
        releaseArtifactsSet = [] as Set
        List<Module> modules = releaseBuild.modules
        //Modify this condition to feet your needs
        if (!(snapExp == 'd14' || snapExp == 'SNAPSHOT')) cancelPromotion('This plugin logic support only Unique/Non-Unique snapshot patterns', null, 400)
        //If there is mor then one Artifacts that have the same checksum but different name only the first one will be return in the search so they will have to have different care
        def missingArtifacts = []
        //Iterate over modules list
        modules.each {Module module ->
            //Find project inner module dependencies
            List<FileInfo> innerModuleDependencies = []
            def dependenciesList = module.dependencies
            dependenciesList.each {dep ->
                FileInfo res = stageArtifactsList.asList().find {sal -> sal.checksumsInfo.sha1 == dep.sha1}
                if (res != null) innerModuleDependencies << res
            }

            //Find and set module ID with release version
            def id = module.id
            List idTokens = id.split(':')
            String stageVersion = idTokens.pop()
            //Implement version per module logic
            idTokens << extractVersion(stageVersion, snapExp)
            id = idTokens.join(':')
            (module.id = id)

            //Iterate over the artifact list, create a release artifact deploy it and add it to the release DetailedBuildRun
            //Save a copy of the RepoPath to roll back if needed
            List<Artifact> artifactsList = module.artifacts
            RepoPath releaseRepoPath = null
            artifactsList.eachWithIndex {art, index ->
                def stageRepoPath = getStageRepoPath(art, stageArtifactsList)
                if (stageRepoPath != null) {
                    releaseRepoPath = getReleaseRepoPath(targetRepository, stageRepoPath, stageVersion, snapExp)
                } else {
                    missingArtifacts << art
                    return
                }

                //If ivy.xml or pom then create and deploy a new Artifact with the fix revision,status,publication inside the xml
                StatusHolder status
                switch (art.type) {
                    case 'ivy':
                        status = generateAndDeployReleaseIvyFile(stageRepoPath, releaseRepoPath, innerModuleDependencies, snapExp)
                        break;
                    case 'pom':
                        status = generateAndDeployReleasePomFile(stageRepoPath, releaseRepoPath, innerModuleDependencies, stageArtifactsList, snapExp)
                        break;
                    default:
                        status = repositories.copy(stageRepoPath, releaseRepoPath)
                }
                if (status.isError()) rollback(releaseArtifactsSet, status.exception)
                setReleaseProperties(stageRepoPath, releaseRepoPath)
                releasedArtifact = new Artifact(repositories.getFileInfo(releaseRepoPath), art.type)
                artifactsList[index] = releasedArtifact

                //Add the release RepoPath for roll back
                releaseArtifactsSet << releaseRepoPath
            }

        }

        //Fix dependencies of other modules with release version
        modules.each {mod ->
            def dependenciesList = mod.dependencies
            dependenciesList.eachWithIndex {dep, i ->
                def match = stageArtifactsList.asList().find {item ->
                    item.checksumsInfo.sha1 == dep.sha1
                }
                if (match != null) {
                    //Until GAP-129 is resolved this will have todo
                    List<String> tokens = match.repoPath.path.split('/')
                    String stageVersion = tokens[tokens.size() - 2]
                    def releaseRepoPath = getReleaseRepoPath(targetRepository, match.repoPath, stageVersion, snapExp)
                    def releaseFileInfo = repositories.getFileInfo(releaseRepoPath)
                    dependenciesList[i] = new Dependency(dep.id, releaseFileInfo, dep.scopes, dep.type)
                }
            }
        }

        //Add release status
        def statuses = releaseBuild.releaseStatuses
        statuses << new ReleaseStatus("released", 'Releasing build gradle-multi-example', targetRepository, getStringProperty(params, 'ciUser', false), security.currentUsername)
        //Save new DetailedBuildRun (Build info)
        builds.saveBuild(releaseBuild)
        if (releaseArtifactsSet.size() != stageArtifactsList.size()) {
            echo("The plugin implementaion don't feet your build, release artifact size is different from the stagin number")
            rollback(releaseArtifactsSet, null)
        }

        message = " Build $buildName/$buildNumber has been successfully promoted"
        echo(message)
        status = 200
    }
}

def rollback(def releaseArtifactsSet, Throwable cause) {
    releaseArtifactsSet.each {item ->
        repositories.delete(item)
    }
    cancelPromotion('Rolling back build promotion', cause, 500)
}


def getReleaseRepoPath(String targetRepository, RepoPath stageRepoPath, String stageVersion, String snapExp) {
    releaseVersion = extractVersion(stageVersion, snapExp)
    def layoutInfo = repositories.getLayoutInfo(stageRepoPath)
    //this might not work
    if (layoutInfo.valid) {
        releasePath = stageRepoPath.path.replace("-$layoutInfo.folderIntegrationRevision", '') //removes -SNAPSHOT from folder name
        releasePath = releasePath.replace("-$layoutInfo.fileIntegrationRevision", '') //removes -timestamp from file name
    } else {
        //let's hope the version is simple
        releasePath = stageRepoPath.path.replace(stageVersion, releaseVersion)
    }
    if (releasePath == stageRepoPath.path) {
        cancelPromotion('Converting stage repository path to released repository path failed, please check your snapshot expression', null, 400)
    }
    RepoPathFactory.create(targetRepository, releasePath)
}

def getStageRepoPath(Artifact stageArtifact, Set<FileInfo> stageArtifactsList) {
    //stageArtifact.name = multi-2.15-SNAPSHOT.pom
    //stageArtifactsList.toArray()[0].name= multi1-2.15-20120503.095917-1-tests.jar
    def tmpArtifact = stageArtifactsList.find {
        def layoutInfo = repositories.getLayoutInfo(it.repoPath)
        //this might not work for repos without layout
        //checking the name won't help - it is  called ivy.xml
        (stageArtifact.type == 'ivy' || !layoutInfo.valid || stageArtifact.name.startsWith(layoutInfo.module)) &&
                it.sha1 == stageArtifact.sha1
    }
    if (tmpArtifact == null) {
        echo("No Artifact with the same name and sha1 was found, somthing is wrong with your build info, look for $stageArtifact.name $stageArtifact.sha1 there is probably mor then one artifact with the same sha1")
        return null
    }
    tmpArtifact.repoPath
}

@SuppressWarnings("GroovyAccessibility") //it complains about Node.parent when I refer to <parent> tag
private StatusHolder generateAndDeployReleasePomFile(RepoPath stageRepoPath, releaseRepoPath, List<FileInfo> innerModuleDependencies, Set<FileInfo> stageArtifactsList, String snapExp) {
    def stagePom = repositories.getStringContent(stageRepoPath)
    def project = new XmlSlurper(false, false).parseText(stagePom)
    if (!project.version.isEmpty()) {
        project.version = extractVersion(project.version.text(), snapExp)
    }
    //also try the parent
    if (!project.parent.version.isEmpty()) {
        project.parent.version = extractVersion(project.parent.version.text(), snapExp)
    }

    innerModuleDependencies.each { FileInfo artifact ->
        def layoutInfo = repositories.getLayoutInfo(artifact.repoPath)
        project.dependencies.dependency.findAll {dependency ->
            dependency.groupId == layoutInfo.organization && dependency.artifactId == layoutInfo.module
        }.each {dependency ->
            dependency.version = extractVersion(dependency.version.isEmpty() ? "${layoutInfo.baseRevision}${layoutInfo.integration ? '-' + layoutInfo.folderIntegrationRevision : ''}" : dependency.version.text(), snapExp)
        }
    }

    repositories.deploy(releaseRepoPath, streamXml(project))

}

private StringInputStream streamXml(xml) {
    String result = new StreamingMarkupBuilder().bind { mkp.yield xml }
    new StringInputStream(serialize(result))
}

//Pars the xml and modify values and deploy
private StatusHolder generateAndDeployReleaseIvyFile(RepoPath stageRepoPath, releaseRepoPath, List<FileInfo> innerModuleDependencies, String snapExp) {
    def stageIvy = repositories.getStringContent(stageRepoPath)
    //stageIvy.replace('m:classifier','classifier')
    def slurper = new XmlSlurper(false, false)
    slurper.keepWhitespace = true
    def releaseIvy = slurper.parseText(stageIvy)
    def info = releaseIvy.info[0]
    def stageRev = info.@revision.text()
    info.@revision = extractVersion(stageRev, snapExp)
    info.@status = 'release'
    //fix date and xml alignment and module dependency
    info.@publication = System.currentTimeMillis().toString()
    //Fix inner module dependencies
    innerModuleDependencies.each {art ->
        String[] tokens = art.repoPath.path.split('/')
        def stageVersion = tokens[tokens.size() - 2]
        def name = art.name.split('-')[0]
        def org = tokens[0]
        releaseIvy.dependencies.dependency.findAll {md -> md.@org == org && md.@rev == stageVersion && md.@name == name }.each {e -> e.@rev = extractVersion(stageVersion, snapExp)}
    }

    repositories.deploy(releaseRepoPath, streamXml(releaseIvy))
}

//Copy properties and modify status/timestamp
private void setReleaseProperties(stageRepoPath, releaseRepoPath) {
    def properties = repositories.getProperties(stageRepoPath)
    properties.replaceValues('build.number', ["${properties.getFirst('build.number')}-r"])
    properties.replaceValues('build.status', ['release'])
    properties.replaceValues('build.timestamp', [System.currentTimeMillis().toString()])
    def keys = properties.keys()
    keys.each {item ->
        key = item
        def values = properties.get(item)
        values.each {val ->
            repositories.setProperty(releaseRepoPath, key, val)
        }
    }

}

//This is the place to implement the release version expressions logic
def extractVersion(String stageVersion, snapExp) {
    stageVersion.split('-')[0]
}

private String getStringProperty(params, pName, mandatory) {
    def key = params[pName]
    def val = key == null ? null : key[0].toString()
    if (mandatory && val == null) cancelPromotion("$pName is mandatory paramater", null, 400)
    return val
}

def cancelPromotion(String message, Throwable cause, int errorLevel) {
    echo(message)
    throw new CancelException(message, cause, errorLevel)
}


def echo(str) {
    log.warn("#####Promote Plugin: " + str);
}