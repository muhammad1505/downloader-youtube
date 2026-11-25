package id.local.tubeflow.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkDnsTest {
    @Test
    fun queryHasStandardHeaderAndSingleQuestion() {
        val query = NetworkDns.buildQuery("www.youtube.com", id = 0x1234)
        assertEquals(0x12, query[0].toInt())
        assertEquals(0x34, query[1].toInt())
        assertEquals(0x01, query[2].toInt()) // recursion desired
        assertEquals(0x00, query[3].toInt())
        assertEquals(1, query[4].toInt()) // QDCOUNT
        assertEquals(0, query[5].toInt())
        assertEquals(0, query[6].toInt()) // ANCOUNT
        assertEquals(0, query[11].toInt()) // ARCOUNT
        assertEquals(3, query[12].toInt()) // label "www"
        assertEquals('w'.code.toByte(), query[13])
        // 12 header + 3 label panjang + 15 isi + 1 akar + 4 QTYPE/QCLASS
        assertEquals(12 + 3 + "www.youtube.com".length + 1 + 4, query.size)
    }

    @Test
    fun trailingDotIsTolerated() {
        assertEquals(
            NetworkDns.buildQuery("dns.google").toList(),
            NetworkDns.buildQuery("dns.google.").toList(),
        )
    }

    @Test
    fun rankingPutsReachableResolversFirst() {
        val ranked = NetworkDns.rank(listOf(
            NetworkDns.Probe("Slow", "9.9.9.9", 120),
            NetworkDns.Probe("Silent", "8.8.8.8", null),
            NetworkDns.Probe("Fast", "1.1.1.1", 24),
        ))
        assertEquals(listOf("Fast", "Slow", "Silent"), ranked.map { it.label })
    }

    @Test
    fun emptyRankingStaysEmpty() {
        assertEquals(emptyList<NetworkDns.Probe>(), NetworkDns.rank(emptyList()))
    }

    @Test
    fun candidatesCoverThreePublicResolvers() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            NetworkDns.candidates().map { it.host },
        )
        assertNull(NetworkDns.candidates().first().latencyMs)
    }
}
