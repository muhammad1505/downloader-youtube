package id.local.tubeflow.core

import kotlin.random.Random

/**
 * Antrean putar murni tanpa Android: urutan asli, urutan putar (bisa hasil shuffle), posisi, dan repeat.
 * Setiap perubahan mengembalikan antrean baru sehingga indeks dan item tidak pernah berubah diam-diam;
 * transisi next/previous/add/remove karenanya dapat diuji di host.
 */
class PlaybackQueue<T> private constructor(
    private val items: List<Item<T>>,
    val index: Int,
    val repeat: Repeat,
    val shuffled: Boolean,
    private val lastId: Long,
) {
    class Item<T>(val id: Long, val track: T)
    enum class Repeat { OFF, ALL, ONE }

    init {
        require(items.isNotEmpty()) { "Antrean tidak boleh kosong." }
        require(index in items.indices) { "Posisi antrean tidak valid." }
    }

    constructor(tracks: List<T>, startAt: Int = 0) : this(
        tracks.mapIndexed { position, track -> Item(position.toLong(), track) },
        startAt.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
        Repeat.OFF,
        false,
        tracks.size - 1L,
    )

    val size: Int get() = items.size
    val current: T get() = items[index].track
    /** Urutan putar saat ini, termasuk hasil shuffle. */
    val tracks: List<T> get() = items.map { it.track }
    val hasNext: Boolean get() = index + 1 < size || repeat == Repeat.ALL

    fun jumpTo(target: Int): PlaybackQueue<T> = rebuilt(items, target.coerceIn(0, items.size - 1))

    /** Maju atas aksi pengguna. Repeat ONE tetap berpindah lagu; akhir antrean berhenti kecuali repeat ALL. */
    fun next(): PlaybackQueue<T>? = when {
        index + 1 < items.size -> rebuilt(items, index + 1)
        repeat == Repeat.ALL -> rebuilt(items, 0)
        else -> null
    }

    fun previous(): PlaybackQueue<T>? = when {
        index > 0 -> rebuilt(items, index - 1)
        repeat == Repeat.ALL -> rebuilt(items, items.size - 1)
        else -> null
    }

    /** Antrean setelah lagu selesai: repeat ONE memutar ulang lagu yang sama. */
    fun onCompleted(): PlaybackQueue<T>? = if (repeat == Repeat.ONE) this else next()

    /** Sisipkan tepat setelah lagu aktif; lagu aktif tidak bergeser. */
    fun addNext(track: T): PlaybackQueue<T> {
        val id = lastId + 1
        return rebuilt(items.toMutableList().apply { add(index + 1, Item(id, track)) }, index, lastId = id)
    }

    /** Tambahkan ke akhir antrean; lagu aktif tidak berubah. */
    fun add(track: T): PlaybackQueue<T> {
        val id = lastId + 1
        return rebuilt(items + Item(id, track), index, lastId = id)
    }

    /** Keluarkan satu item. Item aktif bertahan; penghapusan item aktif memindahkan posisi ke penerusnya. */
    fun removeAt(target: Int): PlaybackQueue<T>? {
        require(target in items.indices) { "Posisi antrean tidak valid." }
        if (items.size == 1) return null
        val remaining = items.filterIndexed { position, _ -> position != target }
        val moved = when {
            target < index -> index - 1
            target == index -> index.coerceAtMost(remaining.size - 1)
            else -> index
        }
        return rebuilt(remaining, moved)
    }

    /**
     * Pindahkan satu posisi di antrean (editor antrean). Lagu aktif tetap lagu yang sama — yang
     * berpindah hanya posisinya, jadi pemutaran tidak perlu dimuat ulang. Null bila tidak berubah.
     */
    fun move(from: Int, to: Int): PlaybackQueue<T>? {
        if (from !in items.indices || to !in items.indices || from == to) return null
        val activeId = items[index].id
        val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
        return rebuilt(reordered, reordered.indexOfFirst { it.id == activeId })
    }

    /**
     * Sisakan lagu aktif saja. Dipakai aksi "Bersihkan antrean" tanpa menghentikan lagu yang
     * sedang diputar; urutan dan item aktif tetap utuh.
     */
    fun onlyCurrent(): PlaybackQueue<T>? = if (items.size == 1) null else rebuilt(listOf(items[index]), 0)

    /** Acak dengan lagu aktif tetap pada posisinya; [toggleShuffle] kedua mengembalikan urutan asli. */
    fun toggleShuffle(random: Random = Random.Default): PlaybackQueue<T> = when {
        shuffled -> {
            val ordered = items.sortedBy { it.id }
            rebuilt(ordered, ordered.indexOfFirst { it.id == items[index].id }, shuffled = false)
        }
        else -> {
            val head = items[index]
            rebuilt(listOf(head) + items.filter { it.id != head.id }.shuffled(random), 0, shuffled = true)
        }
    }

    /**
     * Preferensi pengguna (acak/ulangi) berlaku untuk antrean BARU, bukan hanya antrean saat ini:
     * antrean berikutnya mewarisi pilihan yang sama sehingga tombol tidak kembali ke posisi awal
     * setiap kali lagu baru dimulai. Acak memakai aturan [toggleShuffle] agar urutan asli tetap pulih.
     */
    fun withPreferences(shuffled: Boolean, repeat: Repeat, random: Random = Random.Default): PlaybackQueue<T> {
        val ordered = if (shuffled == this.shuffled) this else toggleShuffle(random)
        return ordered.rebuilt(ordered.items, ordered.index, repeat = repeat)
    }

    fun cycleRepeat(): PlaybackQueue<T> = rebuilt(items, index, repeat = when (repeat) {
        Repeat.OFF -> Repeat.ALL
        Repeat.ALL -> Repeat.ONE
        Repeat.ONE -> Repeat.OFF
    })

    private fun rebuilt(
        items: List<Item<T>>,
        index: Int,
        repeat: Repeat = this.repeat,
        shuffled: Boolean = this.shuffled,
        lastId: Long = this.lastId,
    ) = PlaybackQueue(items, index, repeat, shuffled, lastId)
}
