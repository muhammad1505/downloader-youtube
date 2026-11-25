package id.local.tubeflow.core

import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * DNS yang sedang dipakai sistem dan pengukuran singkat ke resolver publik.
 *
 * Aplikasi tanpa VPN tidak dapat mengganti DNS per aplikasi; unduhan TubeFlow memakai resolver
 * sistem. Karena itu panel ini menunjukkan DNS aktif, mengukur latensi resolver, dan membuka
 * pengaturan Private DNS agar perubahan berlaku untuk seluruh perangkat.
 */
internal object NetworkDns {
    data class Probe(val label: String, val host: String, val latencyMs: Long?)

    fun candidates(): List<Probe> = listOf(
        Probe("Cloudflare", "1.1.1.1", null),
        Probe("Google", "8.8.8.8", null),
        Probe("Quad9", "9.9.9.9", null),
    )

    fun privateDnsHosts(): List<Pair<String, String>> = listOf(
        "Cloudflare" to "one.one.one.one",
        "Google" to "dns.google",
        "Quad9" to "dns.quad9.net",
    )

    /**
     * Urutan layar yang dicoba tombol Private DNS. Aksi khusus tidak selalu ada di ColorOS/realme,
     * jadi pengguna tetap diarahkan ke Jaringan & internet atau Setelan, bukan gagal diam-diam.
     */
    fun privateDnsActions(): List<String> = listOf(
        "android.settings.PRIVATE_DNS_SETTINGS",
        Settings.ACTION_WIRELESS_SETTINGS,
        Settings.ACTION_SETTINGS,
    )

    /** Resolver aktif dari jaringan yang terhubung; daftar kosong berarti belum terbaca. */
    fun currentServers(context: Context): List<String> = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val networks = buildList {
            manager.activeNetwork?.let { add(it) }
            manager.allNetworks?.forEach { add(it) }
        }
        networks.distinct()
            .flatMap { manager.getLinkProperties(it)?.dnsServers.orEmpty().mapNotNull { server -> server.hostAddress } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }.getOrDefault(emptyList())

    /** Urutkan dari yang tercepat; resolver yang tidak menjawab diletakkan di akhir. */
    fun rank(entries: List<Probe>): List<Probe> = entries.sortedWith(
        compareBy({ it.latencyMs == null }, { it.latencyMs ?: Long.MAX_VALUE }),
    )

    /**
     * Satu kueri A untuk www.youtube.com melalui UDP port 53. Hanya mengukur waktu jawab resolver;
     * tidak mengubah konfigurasi jaringan dan gagal sebagai null bila UDP diblokir.
     */
    fun latencyMs(host: String, timeoutMs: Int = 900): Long? = runCatching {
        val query = buildQuery("www.youtube.com")
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val target = InetAddress.getByName(host)
            val started = System.nanoTime()
            socket.send(DatagramPacket(query, query.size, target, 53))
            socket.receive(DatagramPacket(ByteArray(512), 512))
            (System.nanoTime() - started) / 1_000_000
        }
    }.getOrNull()

    /** Kueri DNS standar: header 12 byte, label panjang-prefix, lalu QTYPE A dan QCLASS IN. */
    fun buildQuery(name: String, id: Int = 0x4a1f): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }
        u16(id and 0xFFFF)
        u16(0x0100) // recursion desired
        u16(1)      // satu pertanyaan
        u16(0)
        u16(0)
        u16(0)
        name.trim('.').split('.').filter { it.isNotEmpty() }.forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        u16(1) // A
        u16(1) // IN
        return out.toByteArray()
    }
}
