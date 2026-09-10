#!/usr/bin/env python3
from pathlib import Path
import hashlib
import os
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_ffmpeg_audio_integration.py <source-dir>")
root = Path(sys.argv[1]).resolve()
aar_rel = Path("app/libs/media3-decoder-ffmpeg-1.11.0-ffmpeg6.0.aar")
aar = root / aar_rel
if not aar.is_file():
    raise SystemExit(f"missing {aar}")
aar_sha = hashlib.sha256(aar.read_bytes()).hexdigest()

build = root / "app/build.gradle.kts"
s = build.read_text()
needle = "    implementation(libs.androidx.media3.exoplayer.hls)\n"
addition = '    implementation(files("libs/media3-decoder-ffmpeg-1.11.0-ffmpeg6.0.aar"))\n'
if addition not in s:
    if s.count(needle) != 1:
        raise SystemExit("unexpected Media3 HLS dependency layout")
    s = s.replace(needle, needle + addition)
build.write_text(s)

controller = root / "app/src/main/java/app/ownplay/mobile/playback/Media3PlaybackController.kt"
s = controller.read_text()
if "import android.util.Log\n" not in s:
    s = s.replace("import android.os.Looper\n", "import android.os.Looper\nimport android.util.Log\n")
if "import androidx.media3.decoder.ffmpeg.FfmpegLibrary\n" not in s:
    s = s.replace(
        "import androidx.media3.common.util.UnstableApi\n",
        "import androidx.media3.common.util.UnstableApi\nimport androidx.media3.decoder.ffmpeg.FfmpegLibrary\n",
    )
needle = "    private fun createPlayer(context: Context): ExoPlayer {\n        val renderersFactory = DefaultRenderersFactory(context)\n"
replacement = "    private fun createPlayer(context: Context): ExoPlayer {\n        if (!FfmpegLibrary.isAvailable()) {\n            Log.w(\"OwnPlayPlayback\", \"Media3 FFmpeg audio decoder is unavailable; using device decoders only.\")\n        }\n        val renderersFactory = DefaultRenderersFactory(context)\n"
if replacement not in s:
    if s.count(needle) != 1:
        raise SystemExit("unexpected createPlayer layout")
    s = s.replace(needle, replacement)
controller.write_text(s)

notice = root / "docs/third_party/FFMPEG_AUDIO_DECODER.md"
notice.parent.mkdir(parents=True, exist_ok=True)
notice.write_text(f"""# Media3 FFmpeg audio decoder provenance\n\nOwnPlay includes an audio-only Media3 FFmpeg decoder extension so Live/VOD playback can fall back to software decoding when the Android device does not expose a compatible MediaCodec decoder.\n\n## Pinned inputs\n\n- AndroidX Media3 version: `1.11.0`\n- AndroidX Media3 commit: `2bc207851df311340767e913931ca7b28cab1794`\n- FFmpeg version: `6.0`\n- FFmpeg commit: `ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2`\n- Android NDK: `26.1.10909125`\n- Native API level: `26` (matches OwnPlay minSdk)\n- AAR: `{aar_rel.as_posix()}`\n- AAR SHA-256: `{aar_sha}`\n\n## Enabled audio decoders\n\n`aac mp3 ac3 eac3 truehd dca vorbis opus amrnb amrwb flac alac pcm_mulaw pcm_alaw`\n\nMedia3 maps `mp3` to MPEG Layer I/II/III audio and `dca` to DTS-family audio. No FFmpeg video decoder is enabled in this artifact.\n\n## Runtime selection\n\nOwnPlay keeps `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON`. Android MediaCodec remains preferred; `FfmpegAudioRenderer` is selected only when the platform audio renderer does not support the input format. Decoder fallback remains enabled.\n\n## Rebuild\n\nRun `tools/build-media3-ffmpeg-audio.sh <output-aar> <work-dir>` on Linux with Android SDK 36, NDK 26.1.10909125 and CMake available. The script pins both upstream commits and checks that all four ABI JNI libraries plus `FfmpegAudioRenderer` are present.\n\n## Licensing\n\nThe AndroidX Media code is Apache-2.0. FFmpeg is separately licensed. This FFmpeg build does not enable GPL/nonfree options and packages the upstream FFmpeg license texts under `app/src/main/assets/licenses/ffmpeg/`. Public distribution remains subject to the applicable FFmpeg/LGPL obligations and should pass the project's release/legal review gate.\n""")

audit = root / "docs/audit/STAGE_12_STABILIZATION.txt"
run_id = os.environ.get("GITHUB_RUN_ID", "local")
with audit.open("a") as f:
    f.write(f"""\n\nPhysical QA round 4 audio decoder integration\n- User required unsupported provider audio to be fixed before further feature work.\n- Added pinned Media3 1.11.0 FFmpeg audio decoder AAR, SHA-256 {aar_sha}.\n- Media3 source commit: 2bc207851df311340767e913931ca7b28cab1794.\n- FFmpeg 6.0 source commit: ea3d24bbe3c58b171e55fe2151fc7ffaca3ab3d2.\n- Enabled audio decoders: aac, MPEG Layer I/II/III, AC-3, E-AC-3, TrueHD, DTS family, Vorbis, Opus, AMR-NB/WB, FLAC, ALAC, mu-law, A-law.\n- Platform MediaCodec stays preferred; FFmpeg is fallback through existing EXTENSION_RENDERER_MODE_ON.\n- Added runtime availability probe, reproducible build script, upstream license assets and provenance documentation.\n- No database/auth/provider credential/signing architecture change. No APK/AAB authorized or produced by this source integration workflow.\n- Helper workflow run: {run_id}. Validation result is recorded externally after the exact candidate completes.\n""")
print(f"Integrated FFmpeg audio AAR SHA-256: {aar_sha}")
