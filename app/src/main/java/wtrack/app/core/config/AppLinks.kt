package wtrack.app.core.config

import wtrack.app.BuildConfig

object AppLinks {
    val repositoryUrl: String = BuildConfig.REPO_URL.trimEnd('/')
    val releasesUrl: String = "$repositoryUrl/releases"
    val issuesUrl: String = "$repositoryUrl/issues"
    val licenseUrl: String = "$repositoryUrl/blob/main/LICENSE"
}
