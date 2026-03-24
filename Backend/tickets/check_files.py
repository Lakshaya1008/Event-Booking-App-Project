import re

with open('files.txt', 'r', encoding='utf-8') as f:
    files = [line.strip().replace('\\\\', '/') for line in f if line.strip()]
    basenames = [f.split('/')[-1] for f in files]

with open(r'C:\Users\LAKSHAYA\.gemini\antigravity\brain\52afaf54-a9fc-470f-810e-9fed44d5bf0c\codebase_analysis.md', 'r', encoding='utf-8') as f:
    md_content = f.read()

missing = []
for f, bname in zip(files, basenames):
    if bname not in md_content:
        missing.append(bname)

with open('missing.txt', 'w', encoding='utf-8') as f:
    for m in missing:
        f.write(m + '\n')
