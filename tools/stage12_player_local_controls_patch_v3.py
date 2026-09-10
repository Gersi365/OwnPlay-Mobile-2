from pathlib import Path
import runpy

runpy.run_path("/tmp/stage12_player_local_controls_patch_v2.py", run_name="__main__")

path = Path("app/src/main/java/app/ownplay/mobile/playback/ui/PlayerLocalControls.kt")
text = path.read_text()
old = "import androidx.compose.ui.input.pointer.consume\n"
if text.count(old) != 1:
    raise SystemExit("Expected exactly one obsolete consume import")
path.write_text(text.replace(old, "", 1))
