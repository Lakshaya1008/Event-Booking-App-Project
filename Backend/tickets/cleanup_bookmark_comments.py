import re
from pathlib import Path

root = Path(r"C:\Users\LAKSHAYA\Desktop\CODING\java\Projects\project 2 Event booking App\Event-Booking-App-Project - new\Backend\tickets\src\main\java")

single_start = re.compile(r"^[ \t]*//[ \t]*(FIX|BUG|BEFORE:|AFTER:|NOTE:|TODO|REMOVE)\b.*$", re.IGNORECASE)
single_marker = re.compile(
    r"^[ \t]*//.*\b(Session\s*\d+|S-\d+|FIX-[A-Z0-9-]+|BUG-[A-Z0-9-]+|H-\d+|REQUIRES:|See\s+EventRepository_ADD_METHOD\.java)\b.*$",
    re.IGNORECASE,
)
inline_marker = re.compile(r"\s+//\s*(FIX|BUG|BEFORE:|AFTER:|NOTE:|TODO|REMOVE)\b.*$", re.IGNORECASE)
inline_id = re.compile(
    r"\s+//.*\b(Session\s*\d+|S-\d+|FIX-[A-Z0-9-]+|BUG-[A-Z0-9-]+|H-\d+|REQUIRES:|See\s+EventRepository_ADD_METHOD\.java)\b.*$",
    re.IGNORECASE,
)

block_re = re.compile(r"/\*.*?\*/", re.DOTALL)
block_markers = re.compile(
    r"\b(FIX|BUG|BEFORE|AFTER|session|fix applied|was missing|previously|now correctly|added in|REQUIRES:)\b",
    re.IGNORECASE,
)

changed = []
for path in root.rglob("*.java"):
    original = path.read_text(encoding="utf-8")

    def replace_block(match: re.Match[str]) -> str:
        block = match.group(0)
        return "" if block_markers.search(block) else block

    text = block_re.sub(replace_block, original)

    new_lines = []
    for line in text.splitlines():
        if single_start.match(line) or single_marker.match(line):
            continue
        line = inline_marker.sub("", line)
        line = inline_id.sub("", line)
        new_lines.append(line.rstrip())

    text = "\n".join(new_lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    if original.endswith("\n"):
        text += "\n"

    if text != original:
        path.write_text(text, encoding="utf-8")
        changed.append(path)

for p in changed:
    print(p)
print(f"Changed files: {len(changed)}")

