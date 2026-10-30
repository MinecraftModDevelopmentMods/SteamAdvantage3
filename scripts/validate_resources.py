"""Check JSON, registered content coverage, and local/library model and texture references."""
import json,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]; res=root/'src/main/resources'
files={p.relative_to(res).as_posix():p for p in res.rglob('*') if p.is_file()}
for jar in (root/'libs').glob('*.jar'):
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist(): files.setdefault(name,None)
errors=[]
def check(ref,kind,source):
    if ref.startswith('#'): return
    namespace,path=ref.split(':',1) if ':' in ref else ('minecraft',ref)
    if namespace=='minecraft': return
    target=f'assets/{namespace}/{kind}/{path}'+('.png' if kind=='textures' else '.json')
    if target not in files: errors.append(f'{source}: missing {target}')
for p in res.rglob('*.json'):
    obj=json.loads(p.read_text())
    if '/models/' in str(p):
        if 'parent' in obj: check(obj['parent'],'models',p)
        for tex in obj.get('textures',{}).values(): check(tex,'textures',p)
        for override in obj.get('overrides',[]): check(override['model'],'models',p)
    if '/blockstates/' in str(p):
        def walk(value):
            if isinstance(value,dict):
                if 'model' in value: check(value['model'],'models',p)
                for v in value.values(): walk(v)
            if isinstance(value,list):
                for v in value: walk(v)
        walk(obj)
if errors: raise SystemExit('\n'.join(errors))
print('All resource JSON and local/library model and texture references passed.')
