#!/usr/bin/env python3
"""Build what the Server Setup app needs from pack/, so the app never drifts from the pack.
  <out>/server-manifest.tsv   one line per server-side mod: path, sha512, url, name
  <out>/server-files.zip      every non-mod file in the pack (configs, datapacks, Field Guide)
                              plus server/server.properties and server/user_jvm_args.txt
  <out>/pack-info.txt         pack version, Minecraft and NeoForge versions, Twilight Forest jar
Same rule as `packwiz-installer -s server`: files whose .pw.toml says side = "client" are skipped.
Usage: python3 tools/build_server_bundle.py <out folder>"""
import hashlib, os, sys, tomllib, zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PACK = os.path.join(ROOT, 'pack')
OUT = sys.argv[1] if len(sys.argv) > 1 else sys.exit(__doc__)

# The one mod that can't be downloaded automatically (CurseForge only). The app checks it by hash.
TF_FILE = 'twilightforest-1.21.1-4.8.3345-universal.jar'
TF_SHA256 = '493da16d10210f9f53a3c33b3d8fcb4ae9b14016ee420d753c894562bd9aba35'
TF_VERSION = '4.8.3345'

def load(path):
    with open(path, 'rb') as f:
        return tomllib.load(f)

pack = load(os.path.join(PACK, 'pack.toml'))
index = load(os.path.join(PACK, pack['index']['file']))
os.makedirs(OUT, exist_ok=True)

mods, files, skipped = [], [], 0
for entry in index['files']:
    rel = entry['file']
    src = os.path.join(PACK, rel)
    if hashlib.new(index.get('hash-format', 'sha256'), open(src, 'rb').read()).hexdigest() != entry['hash']:
        sys.exit(f'{rel} does not match pack/index.toml; run `packwiz refresh` in pack/')
    if not entry.get('metafile'):
        files.append(rel)
        continue
    meta = load(src)
    if meta.get('side', 'both') == 'client':
        skipped += 1
        continue
    dl = meta['download']
    if dl.get('hash-format') != 'sha512' or 'url' not in dl:
        sys.exit(f'{rel}: the app only handles direct sha512 downloads')
    dest = os.path.join(os.path.dirname(rel), meta['filename']).replace(os.sep, '/')
    mods.append((dest, dl['hash'], dl['url'], meta['name']))

for dest, _, _, name in mods:
    for bad in ('\t', '\n'):
        if bad in dest or bad in name:
            sys.exit(f'tab or newline in {dest}')

with open(os.path.join(OUT, 'server-manifest.tsv'), 'w', newline='\n') as f:
    for dest, sha, url, name in sorted(mods):
        f.write(f'{dest}\t{sha}\t{url}\t{name}\n')

with zipfile.ZipFile(os.path.join(OUT, 'server-files.zip'), 'w', zipfile.ZIP_DEFLATED) as z:
    for rel in sorted(files):
        z.write(os.path.join(PACK, rel), 'pack/' + rel)
    for name in ('server.properties', 'user_jvm_args.txt'):
        z.write(os.path.join(ROOT, 'server', name), 'server/' + name)

with open(os.path.join(OUT, 'pack-info.txt'), 'w', newline='\n') as f:
    f.write(f"pack={pack['version']}\nminecraft={pack['versions']['minecraft']}\n"
            f"neoforge={pack['versions']['neoforge']}\n"
            f"tf_file={TF_FILE}\ntf_sha256={TF_SHA256}\ntf_version={TF_VERSION}\n")

print(f"pack {pack['version']}: {len(mods)} server mods, {skipped} client-only skipped, {len(files)} other files")
