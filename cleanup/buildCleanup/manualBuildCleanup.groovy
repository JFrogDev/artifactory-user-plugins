import groovy.time.TimeCategory
import groovy.time.TimeDuration
import groovy.json.JsonSlurper
import groovy.transform.Field

// usage: curl -i -uadmin:password -X POST https://artifactory.io/api/plugins/execute/manualBuildCleanup?params=dryRun=true

@Field final String CONFIG_FILE_PATH = "plugins/${this.class.name}.json"

def pluginGroup = 'cleaners'

executions {
    manualBuildCleanup(groups: [pluginGroup]) { params ->
        def dryRun = params['dryRun'] ? new Boolean(params['dryRun'][0]) : true
        log.info "Starting build cleanup"
        Date start = new Date()
        config = getConfigSettings()
        buildCleanup(config, dryRun)

        Date stop = new Date()
        TimeDuration td = TimeCategory.minus( stop, start )

        log.info "Build cleanup finished, took: $td"
    }
}

class CleaningStrategy{
  String[] softClean;
  String[] hardClean;
}

def private getConfigSettings(){
  def configFile = new File(ctx.artifactoryHome.etcDir, CONFIG_FILE_PATH)
  def config = new JsonSlurper().parse(configFile.toURL())
  CleaningStrategy strat = new CleaningStrategy()

  config.policies.each{ policySettings ->
      strat.hardClean = policySettings.hardClean as String[]
      strat.softClean = policySettings.softClean as String[]
  }
  return strat
}

private def buildCleanup(CleaningStrategy config, dryRun){
  List<String> buildNames = builds.getBuildNames()

  Integer totalNumberOfBuilds = 0
  Map<org.artifactory.build.BuildRun, Integer>  deletedBuilds = [:]

  buildNames.each{ buildName ->
    log.info "Current Build: $buildName"
    List<org.artifactory.build.BuildRun> artifactBuilds = builds.getBuilds(buildName, null, null)
    Integer nrOfBuildsCurArtifact = artifactBuilds.size()

    totalNumberOfBuilds = totalNumberOfBuilds + nrOfBuildsCurArtifact
    deletedBuilds = deleteInvalidBuilds(artifactBuilds, dryRun, config, deletedBuilds)
  }
    deletedBuilds.each{ build ->

    if (!dryRun){
      log.info "Build: $build.key is invalid, deleting..."
      builds.deleteBuild(build.key)
    }
    else{
      log.info "DryRun enabled. Would've deleted: $build.key"
    }
  }

  log.info "Total number of builds: $totalNumberOfBuilds"
  def nrOfDeletedBuilds = deletedBuilds.size()
  if(dryRun){
    log.info "Running in DRYRUN mode. Total number of builds that will be deleted in non dryRun: $nrOfDeletedBuilds"
  }
  else{
    log.info "Total number of builds deleted: $nrOfDeletedBuilds"
  }
}

private def deleteInvalidBuilds(List<org.artifactory.build.BuildRun> artifactBuilds, Boolean dryRun, CleaningStrategy config, deletedBuilds){
  for (org.artifactory.build.BuildRun build : artifactBuilds){
    List<org.artifactory.build.Module> modules = builds.getDetailedBuild(build).getModules()
    if (!containsValidArtifactReferences(modules, config)){
      deletedBuilds[build] = build.getNumber()
    }
  }
  return deletedBuilds
}

private def containsValidArtifactReferences(List<org.artifactory.build.Module> modules, CleaningStrategy config){
  List<Boolean> intactModules = [true] * modules.size()
  modules.eachWithIndex{ module, idx ->

    List<org.artifactory.build.Artifact> artifacts = module.getArtifacts()
    if (artifacts.size() == 0){
      intactModules[idx] = false
    }
    else{
      intactModules[idx] = isModuleValid(getRepoKeysFromArtifacts(artifacts), config)
    }
  }
  Boolean hasIntactModule = intactModules.contains(true)
  return hasIntactModule
}

private def getRepoKeysFromArtifacts(List<org.artifactory.build.Artifact> artifacts){
  Set<String> validRepoKeys = []
  artifacts.each{ artifact ->
    Set<org.artifactory.repo.RepoPath> artifactBySha = searches.artifactsBySha1(artifact.getSha1())
    if (artifactBySha){
      artifactBySha.each{repoPath ->
        validRepoKeys.add(repoPath.getRepoKey())
      }
    }
    else{
      validRepoKeys.add("NULL")
    }
  }
  return validRepoKeys.unique()
}

private def isModuleValid(Set<String> repoKeys, CleaningStrategy config){
  Boolean validModule = false

  // If any referenced artifacts exist within a "softClean" repository, we treat
  // the whole thing as "softClean"
  if (repoKeys.intersect(config.softClean as Set)){
    validModule = true
  }
  // If no referenced artifacts exists within a "softClean" repository, we check
  // if some exists within a "hardClean" repository. If they do, we treat the whole
  // thing as "hardClean"
  else if(repoKeys.intersect(config.hardClean as Set)){
    if (repoKeys.contains("NULL")){
      validModule = false
    }
    else{
      validModule = true
    }
  }
  // If "repoKeys" is empty, we have no references and the buildInfo is completely
  // invalid
  else if(repoKeys.isEmpty()){
    validModule = false
  }
  // Same if repoKeys only contains "NULL" and has a size of 1
  else if(repoKeys.contains("NULL") && repoKeys.size() == 1){
    validModule = false
  }
  // The last case is where no clean type (soft/hard) has been defined for the given repo
  // here we do nothing.
  else{
    validModule = true
  }
  return validModule
}