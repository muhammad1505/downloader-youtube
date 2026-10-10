pluginManagement {
 repositories {
  if (providers.gradleProperty("useGoogleMirror").orNull == "true") {
   exclusiveContent {
    forRepository { maven("https://repo.huaweicloud.com/repository/maven") }
    filter { includeGroupByRegex("androidx.*"); includeGroupByRegex("com[.]android.*"); includeGroupByRegex("com[.]google[.]android.*"); includeGroupByRegex("com[.]google[.]testing.*"); includeGroupByRegex("com[.]google[.]firebase.*"); includeGroupByRegex("org[.]jetbrains[.]androidx.*") }
   }
  } else google()
  mavenCentral(); gradlePluginPortal()
 }
}
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories {
  if (providers.gradleProperty("useGoogleMirror").orNull == "true") {
   exclusiveContent {
    forRepository { maven("https://repo.huaweicloud.com/repository/maven") }
    filter { includeGroupByRegex("androidx.*"); includeGroupByRegex("com[.]android.*"); includeGroupByRegex("com[.]google[.]android.*"); includeGroupByRegex("com[.]google[.]testing.*"); includeGroupByRegex("com[.]google[.]firebase.*"); includeGroupByRegex("org[.]jetbrains[.]androidx.*") }
   }
  } else google()
  mavenCentral()
 }
}
rootProject.name = "TubeFlow"
include(":app")
