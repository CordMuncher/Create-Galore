import zipfile,json,os,re,sys,glob
# usage: extract_mod_data.py <instance>/mods <outdir>
mods=sys.argv[1]; out=sys.argv[2]
items=open(f'{out}/items.tsv','w'); modmap=open(f'{out}/mods.tsv','w')
os.makedirs(f'{out}/recipes',exist_ok=True)
def scan(z,jarname,depth=0):
    names=z.namelist()
    nss=set()
    for n in names:
        m=re.match(r'assets/([^/]+)/lang/en_us\.json$',n)
        if m:
            ns=m.group(1)
            try: d=json.loads(z.read(n).decode('utf-8','replace'))
            except Exception: continue
            for k,v in d.items():
                mm=re.match(r'(item|block)\.([^.]+)\.([^.]+)$',k)
                if mm and mm.group(2)==ns: items.write(f'{ns}:{mm.group(3)}\t{mm.group(1)}\t{v}\t{jarname}\n'); nss.add(ns)
        m=re.match(r'data/([^/]+)/recipes?/(.+\.json)$',n)
        if m:
            p=f'{out}/recipes/{m.group(1)}/{m.group(2)}'; os.makedirs(os.path.dirname(p),exist_ok=True)
            open(p,'wb').write(z.read(n))
        if n.endswith('.jar') and depth<2:
            import io
            try: scan(zipfile.ZipFile(io.BytesIO(z.read(n))),jarname+'!'+os.path.basename(n),depth+1)
            except Exception: pass
    if nss: modmap.write(f'{jarname}\t{",".join(sorted(nss))}\n')
for j in sorted(glob.glob(mods+'/*.jar')):
    scan(zipfile.ZipFile(j),os.path.basename(j))
