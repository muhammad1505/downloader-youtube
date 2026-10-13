"""Run the verified yt-dlp zip in an owned session for descendant cancellation.

Installed as the wrapper library's private ``yt-dlp`` entry point. The unchanged,
SHA-256-verified official zip executable must be adjacent as ``yt-dlp-original``.
Only the application-generated control arguments are consumed here.
"""
import json
import os
from pathlib import Path
import re
import runpy
import sys


def control_arguments(arguments):
    options = {}
    forwarded = []
    index = 0
    names = ("--tubeflow-process-file", "--tubeflow-process-token")
    while index < len(arguments):
        argument = arguments[index]
        if argument not in names:
            forwarded.append(argument)
            index += 1
            continue
        if argument in options or index + 1 >= len(arguments):
            raise ValueError("Invalid engine process control arguments")
        options[argument] = arguments[index + 1]
        index += 2
    if set(options) != set(names):
        raise ValueError("Missing engine process control arguments")
    token = options[names[1]]
    if not re.fullmatch(r"[a-f0-9]{32}", token):
        raise ValueError("Invalid engine process token")
    return Path(options[names[0]]), token, forwarded


def install_cover_compression():
    """Compress before EmbedThumbnail, including JPEG sources the stock PP skips.

    The verified upstream zip stays unchanged. The temporary JPEG is replaced
    only after FFmpeg succeeds; the regular PP retains its hooks and file moves.
    """
    from yt_dlp.postprocessor.ffmpeg import FFmpegThumbnailsConvertorPP
    from yt_dlp.utils import replace_extension

    def compress_thumbnails(processor, info):
        files_to_delete = []
        for index, thumbnail in enumerate(info.get("thumbnails") or []):
            if not thumbnail.get("filepath"):
                continue
            processor.fixup_webp(info, index)
            source = thumbnail["filepath"]
            target = replace_extension(source, "jpg")
            temporary = replace_extension(source, "tubeflow-cover.jpg")
            processor.to_screen("Compressing cover to JPEG (maximum 800px)")
            try:
                processor.real_run_ffmpeg(
                    [(source, [])],
                    [(temporary, ["-vf", "scale=w='min(800,iw)':h='min(800,ih)':force_original_aspect_ratio=decrease",
                                  "-q:v", "5", "-frames:v", "1", "-update", "1"])])
                os.replace(temporary, target)
            finally:
                try:
                    os.unlink(temporary)
                except FileNotFoundError:
                    pass
            thumbnail["filepath"] = target
            if source != target:
                info["__files_to_move"][target] = replace_extension(info["__files_to_move"][source], "jpg")
                files_to_delete.append(source)
        return files_to_delete, info

    # Replace the body, preserving yt-dlp's metaclass progress hooks around run().
    # Applying run's original decorators again isn't necessary for image/video inputs.
    from yt_dlp.postprocessor.common import PostProcessorMetaClass
    FFmpegThumbnailsConvertorPP.run = PostProcessorMetaClass.run_wrapper(compress_thumbnails)


def prepare_audio_tags(info, probe):
    """Only explicit music fields or existing audio tags may supply music metadata."""
    native = {}
    for stream in probe.get("streams", []):
        if stream.get("codec_type") == "audio":
            native.update({key.lower(): value for key, value in stream.get("tags", {}).items()})
    native.update({key.lower(): value for key, value in probe.get("format", {}).get("tags", {}).items()})

    def text(value):
        if isinstance(value, (list, tuple)):
            value = ", ".join(str(item) for item in value if item)
        value = str(value or "").strip().replace("\0", "")
        return "" if value.lower() in ("null", "unknown", "unknown artist", "unknown album") else value

    def first(*values):
        return next((value for item in values if (value := text(item))), "")

    info["meta_title"] = first(info.get("meta_title") if info.get("track") else None,
                                info.get("track"), native.get("title"), info.get("meta_title"), info.get("title"))
    info["meta_artist"] = first(info.get("meta_artist"), info.get("artist"), info.get("artists"), native.get("artist"))
    info["meta_album"] = first(info.get("album"), native.get("album"))
    info["meta_album_artist"] = first(info.get("album_artist"), info.get("album_artists"), native.get("album_artist"))
    info["meta_genre"] = first(info.get("genre"), info.get("genres"), native.get("genre"))
    release = first(info.get("release_year"), info.get("release_date"))
    info["meta_date"] = release if re.fullmatch(r"\d{4}(?:\d{4}|-\d{2}-\d{2})?", release) else first(native.get("date"), native.get("year"))
    info["meta_track"] = first(info.get("track_number"), native.get("track"))
    info["meta_disc"] = first(info.get("disc_number"), native.get("disc"))
    # Do not turn URLs, upload descriptions, categories or TV series into audio tags.
    for key in ("description", "synopsis", "purl", "comment", "show", "season_number", "episode_id", "episode_sort"):
        info["meta_" + key] = first(native.get(key))
    return info


def install_optional_audio_processing():
    """A failed optional tag/artwork step must not discard an untouched complete audio file.

    Only the pinned FFmpeg metadata / thumbnail paths are wrapped. Extraction,
    merge, transcoding and downloads still fail normally. No exception text (URLs)
    is emitted. The original media must remain unchanged in size and mtime.
    """
    from yt_dlp.postprocessor.ffmpeg import FFmpegMetadataPP, FFmpegThumbnailsConvertorPP
    from yt_dlp.postprocessor.embedthumbnail import EmbedThumbnailPP, mutagen
    from yt_dlp.utils import PostProcessingError

    def wrap(processor, label):
        original = processor.run
        if getattr(original, "_tubeflow_optional", False):
            return

        def optional(instance, info):
            filename = info.get("filepath")
            before = os.stat(filename) if filename and os.path.isfile(filename) else None
            try:
                if processor is FFmpegMetadataPP:
                    try:
                        probe = instance.get_metadata_object(filename)
                    except (PostProcessingError, OSError, ValueError):
                        instance.to_screen("[tubeflow-processing] Metadata tidak dapat diperiksa; tag asli dipertahankan.")
                        return [], info
                    prepare_audio_tags(info, probe)
                return original(instance, info)
            except (PostProcessingError, OSError):
                if before is None or not os.path.isfile(filename):
                    raise
                after = os.stat(filename)
                if before.st_size <= 0 or (after.st_size, after.st_mtime_ns) != (before.st_size, before.st_mtime_ns):
                    raise
                instance.to_screen("[tubeflow-processing] " + label)
                return [], info

        optional._tubeflow_optional = True
        processor.run = optional

    wrap(FFmpegMetadataPP, "Metadata gagal; media lengkap tetap disimpan.")
    wrap(FFmpegThumbnailsConvertorPP, "Kompresi artwork gagal; media lengkap tetap disimpan.")
    # The bundled Android engine has no mutagen. Never suppress an in-place tag writer's failure.
    if mutagen is None:
        wrap(EmbedThumbnailPP, "Artwork gagal disematkan; media lengkap tetap disimpan.")


def install_resume_validation():
    """Journal source/format identity; leave byte/fragment resumption to the pinned downloader."""
    import hashlib
    import re
    from urllib.parse import urlsplit
    from yt_dlp.downloader.common import FileDownloader
    from yt_dlp.networking import Request
    from yt_dlp.utils import DownloadError

    if getattr(FileDownloader.download, "_tubeflow_resume", False):
        return
    original = FileDownloader.download

    def write(path, value):
        temporary = Path(str(path) + ".tmp")
        temporary.write_text(json.dumps(value, sort_keys=True))
        os.replace(temporary, path)

    def validated(downloader, filename, info, subtitle=False):
        # Fragment downloader's nested HTTP pieces have no media identity; its parent validates the stream.
        if subtitle or not isinstance(filename, str) or filename == "-" or not info.get("id"):
            return original(downloader, filename, info, subtitle)
        target = Path(filename)
        part = Path(filename + ".part")
        journal = Path(filename + ".tubeflow.json")
        checkpoint = Path(filename + ".ytdl")
        fragments = info.get("fragments") or []
        identity = {key: info.get(key) for key in ("id", "extractor_key", "format_id", "ext", "vcodec", "acodec", "filesize", "protocol")}
        identity["fragments"] = hashlib.sha256(json.dumps([
            {"path": urlsplit(fragment.get("url", "")).path, "duration": fragment.get("duration")}
            for fragment in fragments], sort_keys=True).encode()).hexdigest() if fragments else None
        try:
            previous = json.loads(journal.read_text())
        except (OSError, ValueError):
            previous = {}
        has_data = part.exists() or target.exists()
        reason = ""
        if has_data and (previous.get("identity") != identity or info.get("is_live")):
            reason = "Sumber/format berubah atau identitas parsial belum tersedia; mulai ulang dengan aman."
        if part.exists() and info.get("filesize") and part.stat().st_size > info["filesize"]:
            reason = "Ukuran parsial tidak valid; mulai ulang dengan aman."
        if part.exists() and fragments:
            try:
                progress = json.loads(checkpoint.read_text())["downloader"]["current_fragment"]["index"]
                if not isinstance(progress, int) or not 0 <= progress <= len(fragments):
                    raise ValueError("Invalid fragment checkpoint")
            except (OSError, ValueError, KeyError, TypeError):
                reason = "Checkpoint fragmen tidak valid; mulai ulang dengan aman."
        headers = {}
        protocol = info.get("protocol", "https")
        if not fragments and protocol in ("http", "https") and info.get("url"):
            request_headers = dict(info.get("http_headers") or {})
            try:
                if part.exists() and not reason:
                    length = min(part.stat().st_size, 65536)
                    request_headers["Range"] = f"bytes=0-{max(0, length - 1)}"
                    validator = previous.get("headers", {}).get("etag") or previous.get("headers", {}).get("modified")
                    if validator and not validator.startswith("W/"):
                        request_headers["If-Range"] = validator
                    response = downloader.ydl.urlopen(Request(info["url"], headers=request_headers))
                    try:
                        content_range = response.headers.get("Content-Range", "")
                        headers = {"etag": response.headers.get("ETag"), "modified": response.headers.get("Last-Modified")}
                        old = previous.get("headers", {})
                        range_match = re.fullmatch(r"bytes 0-(\d+)/(\d+|\*)", content_range)
                        expected_size = info.get("filesize")
                        if not range_match or int(range_match[1]) + 1 != length:
                            reason = "Server tidak mendukung lanjut atau berkas berubah; mulai ulang dengan aman."
                        elif expected_size and range_match[2] != str(expected_size):
                            reason = "Ukuran berkas server berubah; mulai ulang dengan aman."
                        elif any(old.get(key) and headers.get(key) != old[key] for key in ("etag", "modified")):
                            reason = "Identitas berkas server berubah; mulai ulang dengan aman."
                        else:
                            remote = response.read(length)
                            with part.open("rb") as local:
                                if remote != local.read(length):
                                    reason = "Isi parsial berbeda dari sumber; mulai ulang dengan aman."
                    finally:
                        response.close()
                else:
                    response = downloader.ydl.urlopen(Request(info["url"], headers=request_headers, method="HEAD"))
                    try:
                        headers = {"etag": response.headers.get("ETag"), "modified": response.headers.get("Last-Modified")}
                        if target.exists() and any(previous.get("headers", {}).get(key) and headers.get(key) != previous["headers"][key] for key in ("etag", "modified")):
                            reason = "Identitas berkas server berubah; mulai ulang dengan aman."
                    finally:
                        response.close()
            except Exception:
                # Never append unverifiable bytes; let the normal downloader report/retry connectivity errors.
                if has_data:
                    downloader.to_screen("[tubeflow-resume] Sumber belum dapat diverifikasi; berkas parsial dipertahankan untuk Coba lagi.")
                    raise DownloadError("Sumber parsial belum dapat diverifikasi; coba lagi saat jaringan tersedia.")
        if reason:
            for file in (part, target, checkpoint):
                file.unlink(missing_ok=True)
            for file in target.parent.glob(target.name + ".part-Frag*"):
                file.unlink(missing_ok=True)
            downloader.to_screen("[tubeflow-resume] " + reason)
        elif part.exists():
            downloader.to_screen("[tubeflow-resume] Identitas parsial cocok; melanjutkan byte/fragmen yang tersimpan.")
        write(journal, {"identity": identity, "headers": headers or previous.get("headers", {})})
        return original(downloader, filename, info, subtitle)

    validated._tubeflow_resume = True
    FileDownloader.download = validated


def install_mix_snapshot():
    """Expose the initial radio panel without fetching its endless continuation.

    Regular playlists retain upstream pagination. This hook targets the pinned
    extractor; its signature and initial-panel behavior have regression tests.
    """
    from yt_dlp.extractor.youtube import YoutubeTabIE

    original = YoutubeTabIE._extract_inline_playlist

    def initial_panel(extractor, playlist, playlist_id, data, ytcfg):
        if playlist_id.startswith("RD"):
            yield from extractor._playlist_entries(playlist)
        else:
            yield from original(extractor, playlist, playlist_id, data, ytcfg)

    YoutubeTabIE._extract_inline_playlist = initial_panel


def run():
    control_file, token, forwarded = control_arguments(sys.argv[1:])
    # Never fall back to a shared process group: failure must remain visible.
    os.setsid()
    pid = os.getpid()
    if os.getpgrp() != pid or os.getsid(0) != pid:
        raise RuntimeError("Engine could not create its own process group")
    stat = Path("/proc/self/stat").read_text()
    # comm can contain spaces and ')'; fields following its closing ')' start at 3.
    start_time_ticks = stat[stat.rfind(")") + 2:].split()[19]
    control_file.parent.mkdir(parents=True, exist_ok=True)
    temporary = control_file.with_name(control_file.name + ".tmp")
    data = {"pid": pid, "pgid": pid, "sid": pid, "uid": os.getuid(),
            "startTimeTicks": start_time_ticks, "token": token}
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        json.dump(data, output)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, control_file)
    original = Path(__file__).resolve().with_name("yt-dlp-original")
    try:
        extensions = ("--tubeflow-compress-cover", "--tubeflow-mix-snapshot", "--tubeflow-validate-resume", "--tubeflow-optional-audio")
        if any(flag in forwarded for flag in extensions):
            sys.path.insert(0, str(original))
        if "--tubeflow-compress-cover" in forwarded:
            forwarded.remove("--tubeflow-compress-cover")
            install_cover_compression()
        if "--tubeflow-mix-snapshot" in forwarded:
            forwarded.remove("--tubeflow-mix-snapshot")
            install_mix_snapshot()
        if "--tubeflow-validate-resume" in forwarded:
            forwarded.remove("--tubeflow-validate-resume")
            install_resume_validation()
        if "--tubeflow-optional-audio" in forwarded:
            forwarded.remove("--tubeflow-optional-audio")
            install_optional_audio_processing()
        sys.argv = [str(original), *forwarded]
        runpy.run_path(str(original), run_name="__main__")
    finally:
        # Each attempt must use its own unpredictable path/token. Do not remove a
        # different attempt's file if a caller incorrectly reused that path.
        try:
            if json.loads(control_file.read_text()).get("token") == token:
                control_file.unlink()
        except (FileNotFoundError, json.JSONDecodeError):
            pass


if __name__ == "__main__":
    run()
