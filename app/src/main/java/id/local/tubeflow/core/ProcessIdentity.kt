package id.local.tubeflow.core

/** Identity of one isolated engine session; never authorizes signals from a PID alone. */
data class ProcessIdentity(
    val pid: Int,
    val pgid: Int,
    val sid: Int,
    val startTimeTicks: String,
    val uid: Int,
    val token: String,
) {
    fun isOwned(liveStat: String, liveUid: Int, expectedUid: Int, expectedToken: String, appPid: Int): Boolean = runCatching {
        if (pid <= 1 || pid == appPid || uid != expectedUid || liveUid != expectedUid) return false
        if (token != expectedToken || !token.matches(Regex("[a-f0-9]{32}"))) return false
        if (pgid != pid || sid != pid || !startTimeTicks.matches(Regex("[0-9]+"))) return false
        val close = liveStat.lastIndexOf(')')
        if (close < 0 || liveStat.substringBefore(' ').toInt() != pid) return false
        val fields = liveStat.substring(close + 1).trim().split(Regex("\\s+"))
        fields.size >= 20 && fields[2].toInt() == pid && fields[3].toInt() == pid && fields[19] == startTimeTicks
    }.getOrDefault(false)
}
