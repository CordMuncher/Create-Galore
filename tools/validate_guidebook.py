#!/usr/bin/env python3
"""Check the Field Guide JSON: links, page lengths, item and recipe IDs.
Usage: ITEMS=items.tsv RECIPES=recipes/ python3 tools/validate_guidebook.py [category/]
items.tsv (id, kind, name, jar) and recipes/<namespace>/... are extracted from the mod jars; see docs/guidebook.md."""
import json,os,re,sys,glob
import pathlib
ROOT=os.environ.get('BOOK', str(pathlib.Path(__file__).resolve().parent.parent/'pack/patchouli_books/create_galore/en_us'))
items={}
for l in open(os.environ.get('ITEMS','items.tsv'),encoding='utf-8'):
    p=l.rstrip('\n').split('\t'); items[p[0]]=p[2]
RECIPES=os.environ.get('RECIPES','recipes')
flt=sys.argv[1] if len(sys.argv)>1 else ''
errs=[];warns=[]
cats={os.path.splitext(os.path.basename(f))[0] for f in glob.glob(ROOT+'/categories/*.json')}
entries={}
for f in glob.glob(ROOT+'/entries/**/*.json',recursive=True):
    eid=os.path.relpath(f,ROOT+'/entries')[:-5]
    try: entries[eid]=json.load(open(f,encoding='utf-8'))
    except Exception as e: errs.append(f'{eid}: bad JSON {e}')
def strip(t):
    t=re.sub(r'\$\((br2?|p)\)','\n',t); t=re.sub(r'\$\(li\)','\n• ',t)
    return re.sub(r'\$\([^)]*\)','',t)
def lines(t):
    # approx wrap at 26 chars per line (Patchouli page ~116px)
    n=0
    for para in strip(t).split('\n'):
        n+=max(1,-(-len(para)//26))
    return n
def chk_item(eid,s):
    for part in re.split(r',',s):
        part=part.strip(); idd=re.sub(r'[#\[{].*','',part)
        if not idd: continue
        if idd.startswith('#'): continue
        if ':' not in idd: idd='minecraft:'+idd
        if idd not in items: errs.append(f'{eid}: unknown item {idd}')
def chk_recipe(eid,r):
    ns,_,path=r.partition(':')
    if not path: ns,path='minecraft',r
    f=f'{RECIPES}/{ns}/{path}.json'
    if not os.path.exists(f): errs.append(f'{eid}: recipe not found {r}'); return
    try:
        j=json.load(open(f)); t=j.get('type','')
        if 'neoforge:conditions' in j or 'conditions' in j: warns.append(f'{eid}: recipe {r} is conditional: {json.dumps(j.get("neoforge:conditions",j.get("conditions")))[:120]}')
    except Exception: t='?'
    if t not in ('minecraft:crafting_shaped','minecraft:crafting_shapeless','minecraft:smelting','minecraft:blasting','minecraft:smoking','minecraft:campfire_cooking','minecraft:stonecutting','minecraft:smithing_transform'):
        errs.append(f'{eid}: recipe {r} has type {t}, not displayable by Patchouli')
LINK=re.compile(r'\$\(l:([^)#]+)(#[^)]*)?\)')
for eid,e in sorted(entries.items()):
    if flt and not eid.startswith(flt): continue
    for k in ('name','icon','category','pages'):
        if k not in e: errs.append(f'{eid}: missing {k}')
    if len(e.get('name',''))>26: errs.append(f'{eid}: name longer than 26 chars')
    c=e.get('category','').replace('patchouli:','')
    if c not in cats: errs.append(f'{eid}: unknown category {c}')
    if 'icon' in e and not e['icon'].endswith('.png'):
        chk_item(eid,e['icon'])
        if ',' in e['icon']: errs.append(f'{eid}: icon must be a single item')
    for i,p in enumerate(e.get('pages',[])):
        t=p.get('type','').replace('patchouli:','')
        txt=p.get('text','')
        for m in LINK.finditer(txt+p.get('title','')):
            tgt=m.group(1)
            if tgt.startswith('http'): continue
            tgt=tgt.replace('patchouli:','')
            if tgt not in entries: errs.append(f'{eid} p{i}: broken link {tgt}')
        if txt.count('$(l:')!=txt.count('$(/l)'): errs.append(f'{eid} p{i}: unbalanced $(l:...)$(/l)')
        if '—' in txt: warns.append(f'{eid} p{i}: em-dash')
        if t=='text':
            limit=11 if (i==0 or p.get('title')) else 14
            n=lines(txt)
            if n>limit: errs.append(f'{eid} p{i}: text too long (~{n} lines, max {limit}); split into more pages')
        elif t=='spotlight':
            if 'item' not in p: errs.append(f'{eid} p{i}: spotlight needs item')
            else: chk_item(eid,p['item'])
            n=lines(txt)
            if n>7: errs.append(f'{eid} p{i}: spotlight text too long (~{n} lines, max 7)')
        elif t in ('crafting','smelting','blasting','smoking','campfire_cooking','stonecutting','smithing'):
            for k in ('recipe','recipe2'):
                if k in p: chk_recipe(eid,p[k])
            if lines(txt)>4: errs.append(f'{eid} p{i}: recipe page text too long (max ~4 lines)')
        elif t in ('entity',):
            pass
        elif t=='relations':
            for r in p.get('entries',[]):
                if not r.startswith('patchouli:'): errs.append(f'{eid} p{i}: relations entry {r} needs patchouli: prefix')
                elif r[10:] not in entries: errs.append(f'{eid} p{i}: relations entry {r} missing')
        elif t in ('image','link','empty','multiblock','quest'):
            if t=='quest': errs.append(f'{eid} p{i}: no quest pages')
        else: errs.append(f'{eid} p{i}: unknown page type {t}')
for f in glob.glob(ROOT+'/categories/*.json'):
    c=json.load(open(f)); 
    for k in ('name','description','icon'):
        if k not in c: errs.append(f'category {f}: missing {k}')
    if 'icon' in c: chk_item('category '+os.path.basename(f),c['icon'])
print(f'{len(entries)} entries, {len(cats)} categories')
for w in warns: print('WARN',w)
for e in errs: print('ERR',e)
print('OK' if not errs else f'{len(errs)} errors')
