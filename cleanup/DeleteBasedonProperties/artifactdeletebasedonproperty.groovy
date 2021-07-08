/**
    Prunes data from specified repos using method defined in repo/folder properties
*/
// refs
import org.artifactory.build.promotion.PromotionConfig
import org.artifactory.build.staging.ModuleVersion
import org.artifactory.build.staging.VcsConfig
import org.artifactory.exception.CancelException
import org.artifactory.repo.RepoPathFactory
import org.artifactory.request.Request
import org.artifactory.util.StringInputStream
import org.artifactory.resource.ResourceStreamHandle
import groovy.json.JsonSlurper
import groovy.transform.Field
import org.artifactory.fs.ItemInfo
import org.artifactory.repo.RepoPath
import static com.google.common.collect.Multimaps.forMap
// jobs to trigger based on cron or interval
jobs {
    // Every Min Run cron Regex  depreciation run
    dailyDataDepreciation(cron: "0 0/1 * 1/1 * ? *") {
        depreciateData("repositoryname-local")
    }
}
def depreciateData(repoName){
    
    log.info "Starting data depreciation job for repository " + repoName // This will log in the LogFile
    
    // search given repo name for folders with property DataDeprecation = true 
    def repoRootPath = RepoPathFactory.create(repoName, "/")
    List<RepoPath> deprecatePaths = searches.itemsByProperties(forMap(['DataDeprecation': 'true']))
    // call the corresponding method to handle deprecation
    for(RepoPath repoPath : deprecatePaths){
        log.info "Depreciating data for path " + repoPath
        

        String deprecateMethodStr = repositories.getProperty(repoPath, 'DataDeprecationMethod')   //version:4 This is the Property on the artifact
if (deprecateMethodStr !=null)
{
    String[] depreciateMethodOpts = deprecateMethodStr.split(':')   //  (version) : (4) 

    String[] depreciateMethodArgs = depreciateMethodOpts[1]
moduleMajorVersion(repoPath,repoName,depreciateMethodArgs)
  
}else{
    println "No Such Property Defined on the folder"
}
      
    }
}
/**
    Deprecate files by Module name and major version, removing by build number
    Keep last x number of semantic versions of an Artifact
    args is assumed to be a single integer value (but as a string) for number of files to keep
*/
def moduleMajorVersion(RepoPath repoPath, repoName,String[] args){
def int numToKeep =0
args.each{
    log.info "Depreciating data using method moduleMajorVersion and args " + it
    println "Depreciating data using method moduleMajorVersion and args " + it
    numToKeep=it
}
    println "Starting data depreciation job for repository " + repoName
    log.info "Starting data depreciation job for repository " + repoName
    // search given repo name for folders with property DataDeprecation = true
    def repoRootPath = RepoPathFactory.create(repoName, "/")
    List<RepoPath> deprecatePaths = searches.itemsByProperties(forMap(['DataDeprecation': 'true']))
    List<ItemInfo> folderParent 
    List<String> fillVersions =[""]
    def int numArtifacts
    // call the corresponding method to handle deprecation
    for(RepoPath rPath : deprecatePaths){
        println "Depreciating data for path " + rPath
        log.info "Depreciating data for path " + rPath
         def parent = rPath.parent
       println "Parent Path " + parent

       folderParent= repositories.getChildren(rPath)
      for (child in folderParent) {
         RepoPath childRepoPath = child.getRepoPath()
        def artifactVersion = repositories.getProperty(childRepoPath,"build.number")
         
         if (artifactVersion != null)
         {
            fillVersions << artifactVersion
         }
    
       }
    def List<String> versionsToKeepList = mostRecentVersion(fillVersions,numToKeep)
    for (child in folderParent) {
         RepoPath childRepoPath = child.getRepoPath()
         def artifactVersion = repositories.getProperty(childRepoPath,"build.number")
        
        def versionToDelete= versionsToKeepList.find {it== artifactVersion}
        
       if  (versionToDelete ==null ){
           
            numArtifacts++
            repositories.delete(childRepoPath)
        println "Successfully Deleted ${childRepoPath} in repository ${repoName}"
         log.info("Successfully Deleted ${childRepoPath} in repository ${repoName}") 
       }
    
       }
    }
         println "Finished cleaning up ${numArtifacts} artifacts in repository ${repoName}"
       log.info("Finished cleaning up ${numArtifacts} artifacts in repository ${repoName}")
}


List<String> mostRecentVersion(List versions,int numbersToKeep) {
if (versions ==null || versions.empty)
{
return versions
}else{

  def List<String> sorted = versions.sort(false) { a, b -> 

    List verA = a.tokenize('.')
    List verB = b.tokenize('.')

    def commonIndices = Math.min(verA.size(), verB.size())

    for (int i = 0; i < commonIndices; ++i) {
      def numA = verA[i].toInteger()
      def numB = verB[i].toInteger()
      
      if (numA != numB) {
        return numA <=> numB
      }
    }
    verA.size() <=> verB.size()
  }
    Collections.reverse(sorted);
    sorted[-1]
    sorted.removeAll(Arrays.asList([null],""))
    sorted.unique()
    def sortedMajor = []
    sorted.each {
    String[] semversion = it.tokenize('.')
    String majorVersion = semversion[0]; 
    sortedMajor  << majorVersion

}
def sortedMajorUniqueVersions= sortedMajor.unique()
def verToKeep =[]
sortedMajorUniqueVersions.each {
def String versionToPass = it
def startwithparam = sorted.findAll { y -> y.startsWith(versionToPass)}
def topxvalues = startwithparam.take(numbersToKeep)
verToKeep.addAll(topxvalues) 
}

return verToKeep
}

}
