#!/usr/bin/env bash
# Schneller Vorschau-Durchlauf: bash iter.sh NAME [prozent] [samples]
cd "$(dirname "$0")"
B="/d/Media/Programme/PortableApps.com/PortableApps/Blender5/blender.exe"
D="C:/Users/keine/AppData/Local/Temp/claude/621edbc86207d79a/4e50b0a4-1af4-458f-9417-fb5d43f333eb/scratchpad"
timeout 280 "$B" --factory-startup -b --python make_wichtel.py --python render_preview.py -- "$D/$1.png" "${2:-50}" "${3:-48}" CYCLES 2>&1 | grep -E "gerendert|Error|error" | head -3
"$D/venv/Scripts/python.exe" -I "$D/flat.py" "$D/$1.png" "$D/${1}_flat.png"
