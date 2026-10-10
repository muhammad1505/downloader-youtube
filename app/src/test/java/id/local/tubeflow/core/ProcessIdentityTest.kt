package id.local.tubeflow.core

import org.junit.Assert.*
import org.junit.Test

class ProcessIdentityTest {
    private val nonce = "0123456789abcdef0123456789abcdef"
    private val identity = ProcessIdentity(123,123,123,"999",10001,nonce)
    private fun stat(pid: Int = 123, group: Int = 123, session: Int = 123, start: String = "999"): String {
        val fields=MutableList(20) { "0" }
        fields[0]="S"; fields[1]="42"; fields[2]=group.toString(); fields[3]=session.toString(); fields[19]=start
        return "$pid (python ) with spaces) ${fields.joinToString(" ")}"
    }
    private fun owned(candidate: ProcessIdentity = identity, live: String = stat(), liveUid: Int = 10001, token: String = nonce, ownPid: Int = 42) = candidate.isOwned(live,liveUid,10001,token,ownPid)
    @Test fun acceptsExactOwnedIsolatedProcessWithComplexComm() { assertTrue(owned()) }
    @Test fun rejectsPidReuseAndDifferentProcess() { assertFalse(owned(live=stat(start="1000"))); assertFalse(owned(live=stat(pid=124))) }
    @Test fun rejectsSharedOrChangedProcessGroupAndSession() { assertFalse(owned(live=stat(group=42))); assertFalse(owned(live=stat(session=42))); assertFalse(owned(identity.copy(pgid=42))) }
    @Test fun rejectsWrongUidOrNonce() { assertFalse(owned(liveUid=10002)); assertFalse(owned(identity.copy(uid=10002))); assertFalse(owned(token="f".repeat(32))); assertFalse(owned(identity.copy(token="bad"),token="bad")) }
    @Test fun rejectsOwnAppAndSystemPid() { assertFalse(owned(ownPid=123)); assertFalse(owned(identity.copy(pid=1))) }
    @Test fun rejectsMalformedOrTruncatedProcIdentity() { listOf("", "123 (python)", "123 python S", "notpid (python) S",stat(start="broken")).forEach { assertFalse(it,owned(live=it)) } }
}
