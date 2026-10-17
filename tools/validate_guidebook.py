#!/usr/bin/env python3
"""Check the Field Guide (Modonomicon book): links, graph, item and recipe IDs, page lengths.
Usage: ITEMS=items.tsv RECIPES=recipes/ python3 tools/validate_guidebook.py [category/]
items.tsv (id, kind, name, jar) and recipes/<namespace>/... are extracted from the mod jars; see docs/guidebook.md.
Without ITEMS/RECIPES only the structure, links and graph are checked."""
import json,os,re,sys,glob
import pathlib
ROOT=os.environ.get('BOOK', str(pathlib.Path(__file__).resolve().parent.parent/'pack/config/paxi/datapacks/create-galore-tweaks/data/create_galore/modonomicon/books/field_guide'))
items=None
if os.path.exists(os.environ.get('ITEMS','items.tsv')):
    items={}
    for l in open(os.environ.get('ITEMS','items.tsv'),encoding='utf-8'):
        p=l.rstrip('\n').split('\t'); items[p[0]]=p[2]
RECIPES=os.environ.get('RECIPES','recipes')
flt=sys.argv[1] if len(sys.argv)>1 else ''
errs=[];warns=[]
try: json.load(open(ROOT+'/book.json'))
except Exception as e: errs.append(f'book.json: {e}')
cats={}
for f in glob.glob(ROOT+'/categories/*.json'):
    try: cats[os.path.basename(f)[:-5]]=json.load(open(f,encoding='utf-8'))
    except Exception as e: errs.append(f'category {f}: bad JSON {e}')
entries={}
for f in glob.glob(ROOT+'/entries/**/*.json',recursive=True):
    eid=os.path.relpath(f,ROOT+'/entries')[:-5]
    try: entries[eid]=json.load(open(f,encoding='utf-8'))
    except Exception as e: errs.append(f'{eid}: bad JSON {e}')
def lines(t):
    # approx wrap at 23 chars per line (text box is 112 px wide, see book_text_offset_* in book.json)
    t=re.sub(r'\[#\]\([0-9A-Fa-f]*\)','',t)  # colour markers take no room
    t=re.sub(ICON,'ii',t)  # an icon is about two characters wide
    t=re.sub(r'\]\([^)]*\)','',t).replace('**','').replace('[','').replace('\\\n','\n')
    return sum(max(1,-(-len(para)//23)) for para in t.split('\n'))
def chk_item(eid,it):
    if items is None: return
    for s in (it if isinstance(it,list) else [it]):
        idd=s.get('item') if isinstance(s,dict) else s
        if idd and idd not in items: errs.append(f'{eid}: unknown item {idd}')
def chk_recipe(eid,r):
    if items is None: return
    ns,_,path=r.partition(':')
    f=f'{RECIPES}/{ns}/{path}.json'
    if not os.path.exists(f): errs.append(f'{eid}: recipe not found {r}'); return
    t=json.load(open(f)).get('type','')
    if t not in ('minecraft:crafting_shaped','minecraft:crafting_shapeless'):
        errs.append(f'{eid}: recipe {r} has type {t}, not a crafting recipe')
ICON='[\ue100-\ue1ff]'  # Field Guide icons (tools/art/guide_icons.py), drawn by the create-galore-guide resource pack
ICON_OK=re.compile(r'\[#\]\(FFFFFF\)'+ICON+r'\[#\]\(\)')
ITEM_LINK=re.compile(r'\]\(item://([a-z0-9_.-]+:[a-z0-9_/.-]+)\)')
DATA=pathlib.Path(ROOT).parents[2]  # .../data/create_galore
RP=pathlib.Path(ROOT).parents[6]/'resourcepacks/create-galore-guide/assets'
LINK=re.compile(r'\]\(entry://([^)#]+)(#[^)]*)?\)')
for eid,e in sorted(entries.items()):
    if flt and not eid.startswith(flt): continue
    for k in ('name','icon','category','pages','x','y'):
        if k not in e: errs.append(f'{eid}: missing {k}')
    c=e.get('category','')
    if c not in cats: errs.append(f'{eid}: unknown category {c}')
    if not eid.startswith(c+'/'): errs.append(f'{eid}: file is not in entries/{c}/')
    if 'icon' in e: chk_item(eid,e['icon'])
    for par in e.get('parents',[]):
        pe=par.get('entry','')
        if pe not in entries: errs.append(f'{eid}: parent {pe} missing')
        elif not pe.startswith(c+'/'): errs.append(f'{eid}: parent {pe} is in another category')
    for i,p in enumerate(e.get('pages',[])):
        t=p.get('type','').replace('modonomicon:','')
        txt=p.get('text','')
        for m in LINK.finditer(txt):
            if m.group(1) not in entries: errs.append(f'{eid} p{i}: broken link {m.group(1)}')
        if re.search(ICON,ICON_OK.sub('',txt)): errs.append(f'{eid} p{i}: icon not wrapped in [#](FFFFFF)...[#]() (book text tints it black)')
        if '{{' in txt: errs.append(f'{eid} p{i}: leftover {{{{icon}}}} token')
        for m in ITEM_LINK.finditer(txt): chk_item(eid,m.group(1))
        if '$(' in txt: errs.append(f'{eid} p{i}: leftover Patchouli code')
        if re.search(r'(?<!%)%(?!%)',txt): errs.append(f'{eid} p{i}: write % as %%')
        if '—' in txt: warns.append(f'{eid} p{i}: em-dash')
        # Modonomicon shows no gap for a plain blank line between paragraphs; they need '\\\n\\\n' (lists excepted)
        parts=txt.split('\n\n')
        for a,b in zip(parts,parts[1:]):
            if b and not re.match(r'\s*([-*]|\d+\.) ',b) and not re.match(r'\s*([-*]|\d+\.) ',a.split('\n')[-1]):
                errs.append(f'{eid} p{i}: paragraph break needs a backslash at the end of both lines (a blank line alone shows no gap)'); break
        if t=='text':
            limit=15 if p.get('title') else 17  # Modonomicon: text box is 155 px minus the title (17) or -4 without; 9 px a line
            n=lines(txt)
            if n>limit: warns.append(f'{eid} p{i}: text long (~{n} lines, ~{limit} fit before it shrinks); consider splitting')
        elif t=='spotlight':
            if 'item' not in p: errs.append(f'{eid} p{i}: spotlight needs item')
            else: chk_item(eid,p['item'])
            if lines(txt)>12: warns.append(f'{eid} p{i}: spotlight text long')
        elif t=='crafting_recipe':
            for k in ('recipe_id_1','recipe_id_2'):
                if k in p: chk_recipe(eid,p[k])
        elif t=='multiblock':
            mb=p.get('multiblock_id','').partition(':')
            if not (DATA.parent/mb[0]/'modonomicon/multiblocks'/(mb[2]+'.json')).exists(): errs.append(f'{eid} p{i}: multiblock {p.get("multiblock_id")} not found')
        elif t=='image':
            for im in p.get('images',[]):
                ns,_,path=im.partition(':')
                if not (RP/ns/path).exists(): errs.append(f'{eid} p{i}: image {im} not in the create-galore-guide resource pack')
        elif t in ('entity','empty'): pass
        else: errs.append(f'{eid} p{i}: unknown page type {t}')
# graph: one spot per entry in each category, no cycles
for c in cats:
    seen={}
    for eid,e in entries.items():
        if e.get('category')!=c: continue
        xy=(e.get('x'),e.get('y'))
        if xy in seen: errs.append(f'{eid}: same x,y as {seen[xy]}')
        seen[xy]=eid
def cyc(eid,stack=()):
    if eid in stack: errs.append(f'cycle through {eid}'); return
    for par in entries.get(eid,{}).get('parents',[]): cyc(par['entry'],stack+(eid,))
for eid in entries: cyc(eid)
for c,cj in cats.items():
    for k in ('name','icon'):
        if k not in cj: errs.append(f'category {c}: missing {k}')
    if 'icon' in cj: chk_item('category '+c,cj['icon'])
print(f'{len(entries)} entries, {len(cats)} categories' + ('' if items is not None else ' (no items.tsv: IDs not checked)'))
for w in warns: print('WARN',w)
for e in errs: print('ERR',e)
print('OK' if not errs else f'{len(errs)} errors')
sys.exit(1 if errs else 0)
