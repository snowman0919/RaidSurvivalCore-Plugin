#!/usr/bin/env bash
set -euo pipefail

SERVER_PLUGINS="${1:-/home/mc_admin/mc-server/plugins}"
PLUGIN_DIR="$SERVER_PLUGINS/RaidSurvivalCore"
JAR_SOURCE="${2:-build/libs/RaidSurvivalCore-1.0.3.jar}"
STAMP="$(date +%Y%m%d-%H%M%S)"

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "Missing jar: $JAR_SOURCE" >&2
  exit 1
fi

mkdir -p "$PLUGIN_DIR"

find "$SERVER_PLUGINS" -maxdepth 1 -type f -name 'RaidSurvivalCore*.jar' -print0 | while IFS= read -r -d '' old; do
  mv "$old" "$old.bak-$STAMP"
done

cp "$JAR_SOURCE" "$SERVER_PLUGINS/RaidSurvivalCore-1.0.3.jar"
chmod 0644 "$SERVER_PLUGINS/RaidSurvivalCore-1.0.3.jar"

install -m 0644 -D src/main/resources/shop.yml "$PLUGIN_DIR/shop.yml"

if [[ -f "$PLUGIN_DIR/economy.yml" ]]; then
  cp "$PLUGIN_DIR/economy.yml" "$PLUGIN_DIR/economy.yml.bak-$STAMP"
fi
install -m 0644 -D src/main/resources/economy.yml "$PLUGIN_DIR/economy.yml"

if [[ -f "$PLUGIN_DIR/config.yml" ]]; then
  cp "$PLUGIN_DIR/config.yml" "$PLUGIN_DIR/config.yml.bak-$STAMP"
  python3 - "$PLUGIN_DIR/config.yml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def append_block(header, block):
    global text
    if header not in text:
        text = text.rstrip() + "\n" + block.strip() + "\n"

append_block("ender-chest-loot:", """
ender-chest-loot:
  enabled: true
  pvp-only: true
  drop-chance: 0.25
  max-stacks: 2
  stack-fraction: 0.5
""")
append_block("advancements:", """
advancements:
  disable-announcements: true
""")

if "death-location:" in text and "time-zone:" not in text:
    lines = text.splitlines()
    out = []
    in_death = False
    inserted = False
    for line in lines:
        out.append(line)
        if line.startswith("death-location:"):
            in_death = True
            inserted = False
            continue
        if in_death and not inserted and line.startswith("  item-despawn-minutes:"):
            out.append("  time-zone: Asia/Seoul")
            inserted = True
            in_death = False
    text = "\n".join(out) + "\n"

path.write_text(text, encoding="utf-8")
PY
else
  install -m 0644 -D src/main/resources/config.yml "$PLUGIN_DIR/config.yml"
fi

echo "RaidSurvivalCore 1.0.3 deployed to $SERVER_PLUGINS"
echo "Restart the server or run /raidcore reload after replacing the jar."
