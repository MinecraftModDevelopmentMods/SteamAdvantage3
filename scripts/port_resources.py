"""Rebuild static 1.21.1 resources from the retained SA2 artwork."""
import json, shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OLD=ROOT/'SA2/src/main/resources/assets/steamadvantage'
RES=ROOT/'src/main/resources'
ASSET=RES/'assets/steamadvantage'
MACHINES=['steam_boiler_coal','steam_boiler_electric','steam_boiler_geothermal','steam_boiler_oil','steam_tank','steam_furnace','steam_crusher','steam_drill','steam_elevator','steam_still','steam_pump','steam_switch']
BLOCKS=MACHINES+['steam_pipe','steam_track','platform','drillbit','pump_pipe_steam']
def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+'\n')
def modern(value):
    if isinstance(value,dict):
        return {k:modern(v) for k,v in value.items()}
    if isinstance(value,list): return [modern(v) for v in value]
    if isinstance(value,str):
        value=value.replace('steamadvantage:blocks/','steamadvantage:block/').replace('steamadvantage:items/','steamadvantage:item/').replace('steamadvantage:materials/','steamadvantage:block/materials/').replace('poweradvantage:blocks/','poweradvantage:block/').replace('poweradvantage:materials/','poweradvantage:block/materials/')
        if value=='builtin/generated': value='minecraft:item/generated'
    return value
for folder,dest in [('blocks','block'),('items','item'),('materials','block/materials'),('entity','entity'),('gui','gui')]:
    source=OLD/'textures'/folder
    if source.exists(): shutil.copytree(source,ASSET/'textures'/dest,dirs_exist_ok=True)
for p in (OLD/'models').rglob('*.json'):
    obj=modern(json.loads(p.read_text()))
    if p.name=='musket.json':
        obj['parent']='steamadvantage:item/musket_unloaded'
        for override in obj.get('overrides',[]): override['predicate']={'steamadvantage:'+k:v for k,v in override['predicate'].items()}
    write(ASSET/'models'/p.relative_to(OLD/'models'),obj)
for name in MACHINES:
    model='steam_switch_off' if name=='steam_switch' else name
    write(ASSET/f'blockstates/{name}.json',{'variants':{f'facing={face}':dict(model=f'steamadvantage:block/{model}',y=rot) for face,rot in [('north',0),('east',90),('south',180),('west',270)]}})
    write(ASSET/f'models/item/{name}.json',{'parent':f'steamadvantage:block/{model}'})
for name,model in [('platform','platform4'),('drillbit','drillbit'),('pump_pipe_steam','pump_pipe_steam')]:
    write(ASSET/f'blockstates/{name}.json',{'variants':{'':{'model':f'steamadvantage:block/{model}'}}})
    write(ASSET/f'models/item/{name}.json',{'parent':f'steamadvantage:block/{model}'})
# Pipe model rotations and the complete six-connection truth table come from the local dependency.
import zipfile
with zipfile.ZipFile(ROOT/'libs/PowerAdvantage-1.21.1-3.0.0.jar') as jar:
    template=json.loads(jar.read('assets/poweradvantage/blockstates/fluid_pipe.json'))
    for name in ['steam_pipe','steam_track']:
        raw=json.dumps(template).replace('poweradvantage:block/fluid_pipe','steamadvantage:block/'+name)
        write(ASSET/f'blockstates/{name}.json',json.loads(raw))
        write(ASSET/f'models/item/{name}.json',{'parent':f'steamadvantage:block/{name}_ns'})
for path in (OLD/'lang').glob('*.lang'):
    lang={}
    for line in path.read_text().splitlines():
        if '=' not in line or line.startswith('#'): continue
        k,v=line.split('=',1)
        if k=='language_support_goals': continue
        if k.startswith('tile.'): k='block.'+k[5:]
        if k.endswith('.name'): k=k[:-5]
        if k.startswith('enchantment.') and not k.startswith('enchantment.steamadvantage.'): k='enchantment.steamadvantage.'+k[12:]
        lang[k]=v
    lang['itemGroup.steamadvantage']='SteamAdvantage'
    target=ASSET/'lang'/f'{path.stem.lower()}.json'
    if target.exists():
        for key,value in json.loads(target.read_text()).items():
            if key.startswith('screen.steamadvantage.') or key=='itemGroup.steamadvantage.materials': lang.setdefault(key,value)
    write(ASSET/'lang'/f'{path.stem.lower()}.json',lang)
for name in BLOCKS:
    write(RES/f'data/steamadvantage/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'steamadvantage:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})
for tag in ['mineable/pickaxe','needs_iron_tool']:
    write(RES/f'data/minecraft/tags/block/{tag}.json',{'replace':False,'values':['steamadvantage:'+x for x in BLOCKS]})
write(RES/'pack.mcmeta',{'pack':{'pack_format':34,'description':'SteamAdvantage 3 resources'}})
# The musket-only enchantments are datapack registry entries in 1.21.1.
write(RES/'data/steamadvantage/tags/item/muskets.json',{'values':['steamadvantage:musket']})
for name,level in [('rapid_reload',1),('powderless',1),('high_explosive',5),('recoil',5)]:
    write(RES/f'data/steamadvantage/enchantment/{name}.json',{'description':{'translate':'enchantment.steamadvantage.'+name},'supported_items':'#steamadvantage:muskets','primary_items':'#steamadvantage:muskets','weight':2,'max_level':level,'min_cost':{'base':10,'per_level_above_first':10},'max_cost':{'base':40,'per_level_above_first':10},'anvil_cost':4,'slots':['mainhand'],'effects':{}})
for tag in ['in_enchanting_table','tradeable','on_traded_equipment','on_random_loot','non_treasure']:
    write(RES/f'data/minecraft/tags/enchantment/{tag}.json',{'replace':False,'values':['steamadvantage:'+x for x in ['rapid_reload','powderless','high_explosive','recoil']]})
write(RES/'data/steamadvantage/tags/fluid/boiler_fuels.json',{'replace':False,'values':[{'id':'#c:fuels','required':False},{'id':'#c:oil','required':False},{'id':'#c:biofuel','required':False}]})
# Legacy ore dictionary references become conventional item tags; no BaseMetals dependency.
def item(name): return {'item':name}
def tag(name): return {'tag':name}
def shaped(name,pattern,key,count=1):
    write(RES/f'data/steamadvantage/recipe/{name}.json',{'type':'minecraft:crafting_shaped','pattern':pattern,'key':key,'result':{'id':'steamadvantage:'+name,'count':count}})
def machine(name,x,y=None):
    pattern=['gXg','pmp']; key={'g':item('steamadvantage:steam_governor'),'X':x,'p':tag('c:plates/iron'),'m':item('poweradvantage:steel_frame')}
    if y: pattern.insert(0,' Y ');key['Y']=y
    shaped(name,pattern,key)
shaped('steam_governor',[' t ','srs','btb'],{'t':item('minecraft:iron_nugget'),'s':item('minecraft:stick'),'r':item('minecraft:stick'),'b':tag('c:ingots/brass')})
shaped('steam_pipe',['xxx','   ','xxx'],{'x':tag('c:ingots/brass')},6)
shaped('steam_drill_bit',[' g ',' i ','did'],{'g':tag('c:gears'),'i':tag('c:ingots/steel'),'d':item('minecraft:diamond')})
machine('steam_crusher',item('minecraft:piston'),tag('c:storage_blocks/steel'))
machine('steam_furnace',item('minecraft:furnace'))
machine('steam_boiler_coal',item('steamadvantage:steam_pipe'))
machine('steam_boiler_electric',tag('c:wires'),item('minecraft:bucket'))
machine('steam_boiler_geothermal',item('steamadvantage:steam_pipe'),item('minecraft:obsidian'))
machine('steam_boiler_oil',item('minecraft:furnace'),item('minecraft:bucket'))
machine('steam_drill',item('steamadvantage:steam_drill_bit'))
machine('steam_elevator',item('minecraft:piston'),tag('c:gears'))
machine('steam_still',item('minecraft:bucket'),item('minecraft:bucket'))
machine('steam_pump',item('minecraft:piston'),item('minecraft:bucket'))
shaped('steam_tank',['xgx','xpx','xxx'],{'x':tag('c:plates/copper'),'g':item('steamadvantage:steam_governor'),'p':item('steamadvantage:steam_pipe')})
shaped('steam_switch',[' L ','pfp'],{'L':item('minecraft:lever'),'p':item('steamadvantage:steam_pipe'),'f':item('poweradvantage:steel_frame')})
shaped('blackpowder_cartridge',['L','g','p'],{'L':tag('c:nuggets/lead'),'g':item('minecraft:gunpowder'),'p':item('minecraft:paper')})
shaped('musket',['fss','w  '],{'f':item('minecraft:flint_and_steel'),'s':tag('c:ingots/steel'),'w':tag('minecraft:planks')})
write(RES/'data/steamadvantage/recipe/steam_track.json',{'type':'minecraft:crafting_shapeless','ingredients':[item('steamadvantage:steam_pipe'),item('poweradvantage:steel_frame')],'result':{'id':'steamadvantage:steam_track'}})
for name in ['steam_governor','steam_pipe','blackpowder_cartridge']:
    write(RES/f'data/c/tags/item/{dict(steam_governor="governors",steam_pipe="conduits/steam",blackpowder_cartridge="ammo/blackpowder")[name]}.json',{'values':['steamadvantage:'+name]})

write(ASSET/'models/block/pump_pipe_steam.json',{'parent':'poweradvantage:block/fluid_pipe_ns','textures':{'texture':'steamadvantage:block/steam_pipe','particle':'steamadvantage:block/steam_pipe'}})

for name in MACHINES:
    path=ASSET/f'blockstates/{name}.json'
    obj=json.loads(path.read_text())
    model='steam_switch_off' if name=='steam_switch' else name
    obj['variants']['facing=up']={'model':f'steamadvantage:block/{model}','x':270}
    obj['variants']['facing=down']={'model':f'steamadvantage:block/{model}','x':90}
    write(path,obj)
write(ASSET/'blockstates/platform.json',{'variants':{f'height={i}':{'model':'steamadvantage:block/'+('platform_shaft' if i==0 else 'platform'+str(i))} for i in range(5)}})
write(ASSET/'blockstates/drillbit.json',{'variants':{f'facing={d}':dict(model='steamadvantage:block/drillbit',**rot) for d,rot in [('north',{}),('east',{'y':90}),('south',{'y':180}),('west',{'y':270}),('up',{'x':270}),('down',{'x':90})]}})

def condition_recipe(name,modes):
    p=RES/f'data/steamadvantage/recipe/{name}.json'
    obj=json.loads(p.read_text()); obj['neoforge:conditions']=[{'type':'steamadvantage:recipe_mode','modes':modes}]; write(p,obj)
condition_recipe('steam_governor',['NORMAL'])
p=RES/'data/steamadvantage/recipe/steam_governor.json'
o=json.loads(p.read_text());o['key']['t']=tag('c:gears');o['key']['s']=tag('c:rods');o['key']['r']=tag('c:rods');o['neoforge:conditions'][0]['modes']=['TECH_PROGRESSION','APOCALYPTIC'];write(p.with_name('steam_governor_technical.json'),o)
condition_recipe('musket',['NORMAL','TECH_PROGRESSION'])
p=RES/'data/steamadvantage/recipe/steam_drill.json'
o=json.loads(p.read_text());o['key']['X']=item('minecraft:diamond_pickaxe');o['neoforge:conditions']=[{'type':'steamadvantage:recipe_mode','modes':['NORMAL']}];write(p.with_name('steam_drill_pickaxe.json'),o)
for name,count in [('steam_crusher',2),('steam_furnace',2),('steam_boiler_coal',2),('steam_boiler_electric',2),('steam_boiler_geothermal',2),('steam_drill',2),('steam_elevator',3),('steam_tank',1)]:
    write(RES/f'data/steamadvantage/recipe/salvage_{name}.json',{'type':'mmdlib:crushing','ingredient':item('steamadvantage:'+name),'result':{'id':'steamadvantage:steam_governor','count':count},'neoforge:conditions':[{'type':'steamadvantage:recipe_mode','modes':['APOCALYPTIC']}]})
write(RES/'data/steamadvantage/recipe/salvage_governor.json',{'type':'mmdlib:crushing','ingredient':item('steamadvantage:steam_governor'),'result':{'id':'mmdlib:iron_gear','count':2},'neoforge:conditions':[{'type':'steamadvantage:recipe_mode','modes':['APOCALYPTIC']}]})
# Generated shafts are technical blocks, with no item drops in the original mod.
for name in ['drillbit','pump_pipe_steam','platform']:
    write(RES/f'data/steamadvantage/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[]})

for name in ['musket','steam_machine']:
    write(RES/f'data/steamadvantage/damage_type/{name}.json',{'message_id':name,'exhaustion':0.1,'scaling':'when_caused_by_living_non_player'})
