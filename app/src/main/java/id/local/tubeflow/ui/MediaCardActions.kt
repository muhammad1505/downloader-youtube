package id.local.tubeflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Satu bahasa aksi untuk kartu media selesai di Koleksi dan Riwayat, supaya ikon yang sama
 * melakukan hal yang sama di kedua daftar.
 *
 * Enam aksi sehari-hari tampil langsung di kartu: Putar/Buka, Favorit, Bagikan, Ubah nama,
 * Cari lirik, dan Hapus. Sisanya (antrean, playlist, atur waktu lirik, lampiran, detail/berkas,
 * unduh lagi) masuk menu titik tiga agar baris kartu tetap ringkas. Aksi berisiko tetap memakai
 * konfirmasi di layar pemanggil.
 *
 * Perbedaan isi antar-layar berasal dari kemampuan masing-masing, bukan dari selera:
 * Koleksi punya favorit/playlist/ubah nama, Riwayat punya unduh lagi dan lampiran unduhan.
 */
enum class MediaActionKind {
    PLAY,
    OPEN,
    SHARE,
    FAVORITE_ADD,
    FAVORITE_REMOVE,
    ENQUEUE,
    PLAY_NEXT,
    ADD_PLAYLIST,
    REMOVE_PLAYLIST,
    FIND_LYRICS,
    EDIT_LYRIC_TIMING,
    DETAIL,
    RENAME,
    REDOWNLOAD,
    CLIP_STATUS,
    OPEN_THUMBNAIL,
    OPEN_SUBTITLE,
    DELETE,
}

enum class MediaActionSlot { Inline, Menu }

data class MediaActionSpec(
    val kind: MediaActionKind,
    val slot: MediaActionSlot,
    val group: String? = null,
)

object MediaCardPlan {
    /** Batas aksi yang tampil langsung di kartu; sisanya masuk menu titik tiga. */
    const val MAX_INLINE = 6

    const val GROUP_QUEUE = "Antrean & playlist"
    const val GROUP_LYRICS = "Lirik"
    const val GROUP_ATTACHMENT = "Lampiran"
    const val GROUP_FILE = "Berkas"

    private fun inline(kind: MediaActionKind) = MediaActionSpec(kind, MediaActionSlot.Inline)
    private fun menu(kind: MediaActionKind, group: String) = MediaActionSpec(kind, MediaActionSlot.Menu, group)

    /**
     * Rencana bersama untuk SEMUA kartu media. Urutan, nama grup, dan slot tidak boleh
     * berbeda antar-layar; setiap layar hanya menyalakan kemampuan yang benar-benar dimilikinya.
     * Enam aksi utama selalu tampil di kartu, sisanya berada di menu titik tiga.
     */
    fun card(
        audio: Boolean,
        favorite: Boolean? = null,
        inPlaylist: Boolean = false,
        hasLyrics: Boolean = false,
        hasThumbnail: Boolean = false,
        hasSubtitle: Boolean = false,
        queue: Boolean = false,
        playlist: Boolean = false,
        detail: Boolean = false,
        rename: Boolean = false,
        clipStatus: Boolean = false,
        canRedownload: Boolean = false,
    ): List<MediaActionSpec> = buildList {
        // Enam aksi yang selalu terlihat, urut sesuai kebiasaan memakai kartu.
        // Video juga diputar di dalam TubeFlow; membuka di aplikasi lain tetap tersedia di menu.
        add(inline(MediaActionKind.PLAY))
        // Favorit selalu terlihat di kartu — untuk audio maupun video — supaya pengguna tidak
        // perlu menebak apakah item sudah bisa difavoritkan.
        if (favorite != null) add(inline(if (favorite) MediaActionKind.FAVORITE_REMOVE else MediaActionKind.FAVORITE_ADD))
        add(inline(MediaActionKind.SHARE))
        if (rename) add(inline(MediaActionKind.RENAME))
        if (audio) add(inline(MediaActionKind.FIND_LYRICS))
        add(inline(MediaActionKind.DELETE))

        // Sisanya di menu titik tiga, dikelompokkan agar tetap mudah dipindai.
        if (audio) {
            if (queue) {
                add(menu(MediaActionKind.ENQUEUE, GROUP_QUEUE))
                add(menu(MediaActionKind.PLAY_NEXT, GROUP_QUEUE))
            }
            if (playlist) {
                add(menu(MediaActionKind.ADD_PLAYLIST, GROUP_QUEUE))
                if (inPlaylist) add(menu(MediaActionKind.REMOVE_PLAYLIST, GROUP_QUEUE))
            }
            if (hasLyrics) add(menu(MediaActionKind.EDIT_LYRIC_TIMING, GROUP_LYRICS))
        }
        if (hasThumbnail) add(menu(MediaActionKind.OPEN_THUMBNAIL, GROUP_ATTACHMENT))
        if (hasSubtitle) add(menu(MediaActionKind.OPEN_SUBTITLE, GROUP_ATTACHMENT))
        if (detail) add(menu(MediaActionKind.DETAIL, GROUP_FILE))
        if (!audio && clipStatus) add(menu(MediaActionKind.CLIP_STATUS, GROUP_FILE))
        // Kedua jenis bisa dibuka di aplikasi lain; aksi utama selalu memutar di TubeFlow.
        add(menu(MediaActionKind.OPEN, GROUP_FILE))
        if (canRedownload) add(menu(MediaActionKind.REDOWNLOAD, GROUP_FILE))
    }

    /**
     * Kartu Koleksi: file ada di perangkat, jadi aksi pustaka dan berkas lengkap tersedia.
     * "Unduh lagi" hanya ditawarkan bila item masih tertaut ke unduhan selesai (punya sumber).
     */
    fun library(
        audio: Boolean,
        favorite: Boolean,
        inPlaylist: Boolean,
        hasLyrics: Boolean,
        canRedownload: Boolean = false,
    ): List<MediaActionSpec> = card(
        audio = audio,
        favorite = favorite,
        inPlaylist = inPlaylist,
        hasLyrics = hasLyrics,
        queue = true,
        playlist = true,
        detail = true,
        rename = true,
        clipStatus = !audio,
        canRedownload = canRedownload,
    )

    /**
     * Kartu Riwayat: aksi mengikuti catatan unduhan. Lampiran thumbnail/subtitle hanya
     * ditawarkan bila unduhan benar-benar menyimpannya.
     */
    fun history(
        audio: Boolean,
        hasThumbnail: Boolean,
        hasSubtitle: Boolean,
        hasLyrics: Boolean = false,
    ): List<MediaActionSpec> = card(
        audio = audio,
        hasLyrics = hasLyrics,
        hasThumbnail = hasThumbnail,
        hasSubtitle = hasSubtitle,
        queue = true,
        canRedownload = true,
    )

    /** Satu nama untuk satu aksi di seluruh aplikasi; hanya beda bila maknanya memang beda. */
    fun label(kind: MediaActionKind, audio: Boolean): String = when (kind) {
        MediaActionKind.PLAY -> "Putar"
        MediaActionKind.OPEN -> "Buka dengan aplikasi lain"
        MediaActionKind.SHARE -> "Bagikan"
        MediaActionKind.FAVORITE_ADD -> "Tambah ke favorit"
        MediaActionKind.FAVORITE_REMOVE -> "Hapus dari favorit"
        MediaActionKind.ENQUEUE -> "Tambah ke antrean"
        MediaActionKind.PLAY_NEXT -> "Putar berikutnya"
        MediaActionKind.ADD_PLAYLIST -> "Tambah ke playlist"
        MediaActionKind.REMOVE_PLAYLIST -> "Hapus dari playlist"
        MediaActionKind.FIND_LYRICS -> "Cari lirik"
        MediaActionKind.EDIT_LYRIC_TIMING -> "Atur waktu lirik"
        MediaActionKind.DETAIL -> "Detail"
        MediaActionKind.RENAME -> "Ubah nama"
        MediaActionKind.REDOWNLOAD -> "Unduh lagi"
        MediaActionKind.CLIP_STATUS -> "Potong & bagikan video"
        MediaActionKind.OPEN_THUMBNAIL -> "Buka thumbnail"
        MediaActionKind.OPEN_SUBTITLE -> "Buka subtitle (CC)"
        MediaActionKind.DELETE -> "Hapus file"
    }

    fun icon(kind: MediaActionKind): ImageVector = when (kind) {
        MediaActionKind.PLAY -> Icons.Outlined.PlayArrow
        MediaActionKind.OPEN -> Icons.AutoMirrored.Outlined.OpenInNew
        MediaActionKind.SHARE -> Icons.Outlined.Share
        MediaActionKind.FAVORITE_ADD -> Icons.Outlined.FavoriteBorder
        MediaActionKind.FAVORITE_REMOVE -> Icons.Outlined.Favorite
        MediaActionKind.ENQUEUE -> Icons.AutoMirrored.Outlined.QueueMusic
        MediaActionKind.PLAY_NEXT -> Icons.AutoMirrored.Outlined.PlaylistPlay
        MediaActionKind.ADD_PLAYLIST -> Icons.AutoMirrored.Outlined.PlaylistAdd
        MediaActionKind.REMOVE_PLAYLIST -> Icons.Outlined.PlaylistRemove
        MediaActionKind.FIND_LYRICS -> Icons.Outlined.Lyrics
        MediaActionKind.EDIT_LYRIC_TIMING -> Icons.Outlined.Timer
        MediaActionKind.DETAIL -> Icons.Outlined.Info
        MediaActionKind.RENAME -> Icons.Outlined.Edit
        MediaActionKind.REDOWNLOAD -> Icons.Outlined.Refresh
        MediaActionKind.CLIP_STATUS -> Icons.Outlined.ContentCut
        MediaActionKind.OPEN_THUMBNAIL -> Icons.Outlined.Image
        MediaActionKind.OPEN_SUBTITLE -> Icons.Outlined.ClosedCaption
        MediaActionKind.DELETE -> Icons.Outlined.DeleteOutline
    }

    fun destructive(kind: MediaActionKind): Boolean = kind == MediaActionKind.DELETE
}

/**
 * Baris aksi kartu media: aksi utama sebagai ikon 48dp yang membungkus, lalu satu tombol titik
 * tiga untuk aksi sekunder. Urutan dan pembagian slot berasal dari [MediaCardPlan]; `enabled`
 * dan `onClick` diisi pemanggil.
 *
 * `FlowRow` sengaja TANPA `fillMaxWidth`: di dalam `Row`, anak non-weight diukur lebih dulu
 * dengan sisa lebar (`RowColumnMeasurePolicy`), jadi action bar ber-lebar penuh melahap seluruh
 * ruang dan kolom teks `weight(1f)` terukur dengan lebar 0 — judul/keterangan hilang dari baris.
 */
@Composable
fun MediaActionBar(
    specs: List<MediaActionSpec>,
    audio: Boolean,
    modifier: Modifier = Modifier,
    /** Baris ringkas: semua aksi pindah ke menu titik tiga agar tinggi item tetap kecil. */
    compact: Boolean = false,
    enabled: (MediaActionKind) -> Boolean = { true },
    onClick: (MediaActionKind) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!compact) specs.filter { it.slot == MediaActionSlot.Inline }.forEach { spec ->
            IconAction(
                MediaCardPlan.icon(spec.kind),
                MediaCardPlan.label(spec.kind, audio),
                { onClick(spec.kind) },
                enabled = enabled(spec.kind),
                destructive = MediaCardPlan.destructive(spec.kind),
            )
        }
        val secondary = if (compact) specs else specs.filter { it.slot == MediaActionSlot.Menu }
        if (secondary.isNotEmpty()) {
            Box {
                IconAction(Icons.Outlined.MoreVert, "Aksi lainnya", { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    var currentGroup: String? = null
                    secondary.forEach { spec ->
                        if (spec.group != null && spec.group != currentGroup) {
                            Text(
                                spec.group,
                                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            currentGroup = spec.group
                        }
                        val destructive = MediaCardPlan.destructive(spec.kind)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    MediaCardPlan.label(spec.kind, audio),
                                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                            },
                            onClick = { menuOpen = false; onClick(spec.kind) },
                            enabled = enabled(spec.kind),
                            leadingIcon = {
                                Icon(
                                    MediaCardPlan.icon(spec.kind),
                                    null,
                                    tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Lampiran unduhan (`thumb|<uri>`, `cc|<uri>` per baris) yang dapat dibuka dari kartu Riwayat. */
fun attachmentUris(sidecarUris: String?): Map<String, String> = sidecarUris.orEmpty().lines()
    .mapNotNull { line ->
        val kind = line.substringBefore('|')
        val uri = line.substringAfter('|', "")
        if (uri.isBlank()) null else kind to uri
    }
    .toMap()

const val ATTACHMENT_THUMBNAIL = "thumb"

/**
 * Lampiran subtitle hanya milik video. Pada audio, `sub|` adalah berkas LRC untuk pemutar,
 * bukan subtitle yang bisa dibuka dengan aplikasi lain.
 */
fun hasSubtitleAttachment(audio: Boolean, sidecarUris: String?): Boolean =
    !audio && attachmentUris(sidecarUris).keys.any { it != ATTACHMENT_THUMBNAIL }

/**
 * Penanda kecil di ujung baris Koleksi: ikon lirik bila ada berkas lirik, ikon subtitle beserta
 * jumlahnya, dan hati favorit bila item ada di playlist Favorit. Murni (tanpa Compose) supaya
 * bisa diuji di JVM.
 */
data class RowBadges(val lyrics: Boolean = false, val subtitles: Int = 0, val favorite: Boolean = false)

/** Jumlah baris lampiran yang bukan thumbnail; berkas LRC audio ikut terhitung sebagai lirik. */
fun sidecarCount(sidecarUris: String?): Int = sidecarUris.orEmpty().lines().count { line ->
    val uri = line.substringAfter('|', "")
    uri.isNotBlank() && line.substringBefore('|') != ATTACHMENT_THUMBNAIL
}

/**
 * Penanda yang layak ditampilkan satu baris media: audio menandai lirik (dari `lyricUri` atau
 * baris `sub|` unduhan), video menghitung subtitle tanpa menghitung thumbnail, dan `favorite`
 * mewakili keanggotaan playlist Favorit tanpa bergantung pada lampiran.
 */
fun rowBadges(audio: Boolean, lyricUri: String?, sidecarUris: String?, favorite: Boolean = false): RowBadges {
    val sidecars = sidecarCount(sidecarUris)
    return if (audio) RowBadges(lyrics = !lyricUri.isNullOrBlank() || sidecars > 0, favorite = favorite)
    else RowBadges(subtitles = sidecars, favorite = favorite)
}

/**
 * Aksi cepat baris Koleksi: audio yang dapat diputar mendapat hati favorit dan bagikan di
 * samping ikon lirik, tanpa perlu membuka menu ⋮. Video tetap memakai penanda hati di ujung
 * baris, dan item yang tidak tersedia tidak diberi aksi cepat karena berkasnya tidak bisa
 * dibagikan atau dibuka.
 */
fun rowQuickActions(audio: Boolean, available: Boolean, favorite: Boolean): List<MediaActionKind> =
    if (!audio || !available) emptyList()
    else listOf(
        if (favorite) MediaActionKind.FAVORITE_REMOVE else MediaActionKind.FAVORITE_ADD,
        MediaActionKind.SHARE,
    )

/**
 * Nama file tampil hanya bila tidak sekadar mengulang judul — hierarki daftar lama sebelum
 * baris ringkas, supaya baris tidak dobel info.
 */
fun showFileName(title: String, displayName: String): Boolean {
    val fileName = displayName.substringBeforeLast('.')
    return title.isBlank() || (!fileName.startsWith(title, ignoreCase = true) && fileName != title)
}
