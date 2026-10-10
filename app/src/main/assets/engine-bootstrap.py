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
        extensions = ("--tubeflow-compress-cover", "--tubeflow-mix-snapshot")
        if any(flag in forwarded for flag in extensions):
            sys.path.insert(0, str(original))
        if "--tubeflow-compress-cover" in forwarded:
            forwarded.remove("--tubeflow-compress-cover")
            install_cover_compression()
        if "--tubeflow-mix-snapshot" in forwarded:
            forwarded.remove("--tubeflow-mix-snapshot")
            install_mix_snapshot()
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
