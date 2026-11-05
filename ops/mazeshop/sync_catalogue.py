#!/usr/bin/env python3
"""Apply owner-supplied Mazeshop catalogue through authenticated app APIs on the Pi."""
import argparse, hashlib, html, json, mimetypes, os, re, sys, urllib.request, urllib.error, uuid
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--directory',type=Path,required=True,help='Manifest, templates and prepared assets directory')
parser.add_argument('--apply',action='store_true',help='Apply after validation; otherwise print the intended counts')
args=parser.parse_args(); root=args.directory
catalogue=json.loads((root/'catalogue.json').read_text()); assets=json.loads((root/'assets-manifest.json').read_text())
products=catalogue['products']
assert catalogue['schema_version']==1
assert len({p['slug'] for p in products})==len(products)
for p in products:
    assert 1<=len(p['photo_keys'])<=5
    assert p['status'] in ('draft','enabled')
    assert p['status']=='draft' or (p['price_source'] and float(p['price'])>0)
    for key in p['photo_keys']:
        f=root/'assets'/assets[key]['filename']
        assert f.exists() and hashlib.sha256(f.read_bytes()).hexdigest()==assets[key]['sha256']
print('Validated',len(products),'products;',sum(p['status']=='enabled' for p in products),'with source prices;',len(assets),'web images',flush=True)
if not args.apply:sys.exit(0)
if os.geteuid()!=0:raise SystemExit('Run on the Pi as root to read its local credentials; credentials never leave loopback.')
credentials=json.loads(Path('/etc/shoptopus/credentials.json').read_text())
base='http://127.0.0.1:18080';token=None
state_path=Path('/etc/shoptopus/mazeshop-catalogue-state.json')
state=json.loads(state_path.read_text()) if state_path.exists() else {'assets':{},'products':{},'categories':{},'attributes':{}}
def save_state():
    tmp=state_path.with_suffix('.tmp');tmp.write_text(json.dumps(state,indent=2));tmp.chmod(0o600);tmp.replace(state_path)
def api(path,body=None,method=None,content_type=None):
    headers={}
    if token:headers['Authorization']='Bearer '+token
    if body is not None:
        if isinstance(body,bytes):headers['Content-Type']=content_type
        else:body=json.dumps(body).encode();headers['Content-Type']='application/json'
    request=urllib.request.Request(base+'/api/admin-ui'+path,data=body,headers=headers,method=method)
    try:
        with urllib.request.urlopen(request,timeout=60) as response:return json.load(response)['data']
    except urllib.error.HTTPError as error:
        try:
            detail=json.loads(error.read())
            safe={k:detail.get(k) for k in ('user_message','developer_message','errors') if k in detail}
        except Exception:safe={'status':error.code}
        raise RuntimeError(str(error.code)+' '+path+': '+json.dumps(safe,ensure_ascii=False)[:2000]) from None
token=api('/auth/login',{'email':credentials['admin_username'],'password':credentials['admin_password']})['auth']['token']
def rows(path):
    result=[];page=1
    while True:
        data=api(path+f'?per_page=100&page={page}')
        result.extend(data['items'])
        if page>=data.get('meta',{}).get('total_pages',1):return result
        page+=1
def slug(text):return re.sub('[^a-z0-9]+','-',text.lower()).strip('-')
photo_owners={}
for product in products:
    for key in product['photo_keys']:photo_owners.setdefault(key,[]).append(product['slug'])
def image(key,owner=None):
    item=assets[key]; digest=item['sha256']
    shared=len(photo_owners.get(key,[]))>1
    if shared and owner is None:owner=photo_owners[key][0]
    if owner and (shared or owner.startswith('category:')):digest += ':'+owner
    if digest not in state['assets']:
        boundary='Mazeshop'+uuid.uuid4().hex
        path=root/'assets'/item['filename']
        body=(f'--{boundary}\r\nContent-Disposition: form-data; name="folder"\r\n\r\nmazeshop\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\nContent-Type: image/jpeg\r\n\r\n'.encode()+path.read_bytes()+f'\r\n--{boundary}--\r\n'.encode())
        state['assets'][digest]=api('/media/images',body,content_type='multipart/form-data; boundary='+boundary)
        save_state()
    return state['assets'][digest]
def image_url(key):
    data=image(key)
    return data.get('url') or data.get('path')
def render(name):
    return re.sub(r'\{\{photo:(\d+)\}\}',lambda m:html.escape(image_url(m[1]),quote=True),(root/name).read_text())
categories={x['slug']:x for x in rows('/categories')}
category_copy={
 'Trading Card Binders':'Make a collection personal with engraved artwork, a chosen colour and a little detail of your own.',
 'Lighting':'Decorative acrylic and wooden lights with intricate engraving, sculptural shapes and plenty of character.',
 'Tableware':'Coasters and placemats with natural textures, geometric patterns and personalised details.',
 'Puzzles & Games':'Wooden puzzles, tabletop pieces and little challenges made for taking your time.',
 'Model Kits':'Precisely cut wooden models to assemble and display.',
 'Wall Art & Signs':'Decorative wall pieces, personal signs and engraved designs for a space of your own.',
 'Gifts & Keepsakes':'Small thoughtful pieces, personalised gifts and useful things with character.',
 'Creative Kits':'Hands-on projects for creating something personal.'}
for name in dict.fromkeys(p['category'] for p in products):
    s=slug(name)
    if s not in categories:categories[s]=api('/categories',{'name':{'en':name},'slug':s,'enabled':True,'description':{'en':category_copy[name]}})
    state['categories'][name]=str(categories[s]['id']);save_state()
    category_photo={'Trading Card Binders':'552','Lighting':'708','Tableware':'652','Puzzles & Games':'403','Model Kits':'0','Wall Art & Signs':'858','Gifts & Keepsakes':'531','Creative Kits':'368'}[name]
    cover=dict(image(category_photo,'category:'+name));cover['name']=name;cover['alt']=name
    api('/categories/'+state['categories'][name],{'menu_image':cover,'header_image':cover},'PATCH')
existing={p['slug']:p for p in rows('/products')}
for number,p in enumerate(products,1):
    signature=hashlib.sha256(json.dumps(p,sort_keys=True).encode()).hexdigest()
    prior=state['products'].get(p['slug'],{})
    if p['slug'] in existing and prior.get('signature')==signature:
        continue
    gallery=[]
    for n,key in enumerate(p['photo_keys']):
        photo=dict(image(key,p['slug']));photo.update(name=p['name']+' — view '+str(n+1),alt=p['name']+' — view '+str(n+1),sort_order=n)
        gallery.append(photo)
    body={'name':{'en':p['name']},'slug':p['slug'],'short_description':{'en':p['short_description']},'description':{'en':p['description']},'price':p['price'] or '0.00','status':1 if p['status']=='enabled' else 0,'stock':p['stock'],'sku':'MAZE-'+p['slug'][:35].upper()+'-'+hashlib.sha256(p['slug'].encode()).hexdigest()[:8].upper(),'customizable':bool(p.get('customization_text')),'customization_text':{'en':p.get('customization_text') or ''},'category_ids':[state['categories'][p['category']]],'gallery_photos':gallery,'cover_photo_id':gallery[0]['id']}
    if p['slug'] in existing:result=api('/products/'+str(existing[p['slug']]['id']),body,'PATCH')
    else:
        result=api('/products',body);existing[p['slug']]=result
    state['products'][p['slug']]={'id':str(result['id']),'signature':signature};save_state()
    print('Saved',number,'/',len(products),p['slug'],flush=True)
# Only the combinations evidenced by the supplied colour charts are created.
attributes={a['slug']:a for a in rows('/attributes')}
def attribute(name,s,type_,options):
    if s not in attributes:attributes[s]=api('/attributes',{'name':{'en':name},'slug':s,'type':type_,'enabled':True,'show_in_filters':True})
    aid=str(attributes[s]['id']);detail=api('/attributes/'+aid)
    known={o['slug']:o for o in detail['options']}
    result={}
    for label,value in options.items():
        oslug=slug(label)
        if oslug not in known:
            detail=api('/attributes/'+aid+'/options',{'name':{'en':label},'slug':oslug,'value':value,'enabled':True})
            known={o['slug']:o for o in detail['options']}
        result[label]=str(known[oslug]['id'])
    return aid,result
sizes,size_opts=attribute('Binder format','binder-format',0,{'4-pocket':'4-pocket','9-pocket':'9-pocket'})
colours,colour_opts=attribute('Binder colour','binder-colour',2,{'Teal':'#159d9b','Navy':'#213d54','Black':'#242525','Pink':'#d8a8a4','Purple':'#653b8d','Green':'#235b45','Red':'#ba3037','Orange':'#d26a27','Blue':'#3978a5','White':'#ecf0e9','Yellow':'#e5b70d','Lime':'#99ba38'})
binder=next(p for p in products if 'binder_options' in p)
binder_id=state['products'][binder['slug']]['id']
variants={v['sku']:v for v in api('/products/'+binder_id+'/variants')['items']}
for size,allowed in binder['binder_options'].items():
    for colour in allowed:
        sku='MAZE-BINDER-'+size.upper()+'-'+colour.upper()
        body={'slug':'binder-'+size+'-'+colour.lower(),'sku':sku,'enabled':True,'stock':0,'price':binder['price'],'description':{'en':size+' · '+colour},'attribute_options':{sizes:size_opts[size],colours:colour_opts[colour]}}
        if sku not in variants:api('/products/'+binder_id+'/variants',body)
        elif variants[sku]['attribute_options'] != body['attribute_options']:
            api('/products/'+binder_id+'/variants/'+str(variants[sku]['id']),{'attribute_options':body['attribute_options']},'PATCH')
print('Configured',sum(len(v) for v in binder['binder_options'].values()),'chart-backed binder variants',flush=True)
# Preserve unrelated authentication, Gmail and payment settings.
settings=api('/shop-settings');shop=settings['shop']
shop.update(name='Mazeshop',tagline='Personalised gifts & laser-crafted design. Made in Chepstow.',contact_phone='+44 7715 295672',contact_address='Bulwark, Chepstow, Wales, United Kingdom',social_platforms=[{'platform':'facebook','url':'https://www.facebook.com/profile.php?id=61572260662151'},{'platform':'instagram','url':'https://www.instagram.com/maze_shop52/'},{'platform':'pinterest','url':'https://uk.pinterest.com/rolandtreiber/'},{'platform':'etsy','url':'https://www.etsy.com/uk/shop/MazeshopUK'}])
logo={'url':'/brand/mazeshop-logo.svg','path':'/brand/mazeshop-logo.svg','alt':'Mazeshop','width':270,'height':52}
shop.update(logo=logo,logo_light=logo,logo_dark=logo)
shop['theme']={'mode':'light','light_palette':{'background':'#f6f6ef','surface':'#ffffff','text':'#20231e','muted':'#62685b','accent':'#d4f357','accent_contrast':'#20231e','border':'#dedfd4'},'dark_palette':{'background':'#20231e','surface':'#2a2e26','text':'#f6f6ef','muted':'#bcc3b0','accent':'#d4f357','accent_contrast':'#20231e','border':'#444b3d'},'typography':{'body_font':'sans','display_font':'sans','base_font_size':16,'heading_scale':'display'},'shape':{'radius_scale':'sharp'},'layout':{'content_width':'wide'},'buttons':{'button_style':'solid'}}
api('/shop-settings',{'shop':shop},'PATCH')
pages={p['slug']:p for p in rows('/pages')}
for s,title,home in [('home','Mazeshop',True),('about','About',False)]:
    body={'title':{'en':title},'slug':s,'status':1,'show_in_menu':not home,'home_page':home,'add_to_footer':not home}
    if s in pages:page=api('/pages/'+str(pages[s]['id']),body,'PATCH')
    else:page=api('/pages',body);pages[s]=page
    page_id=str(page['id']);contents=api('/content-targets/page/'+page_id+'/contents')['items']
    blocks=([{'language':'en','type':0,'text':render('home-top.html'),'sort_order':0},
             {'language':'en','type':10,'text':'Workshop favourites','sort_order':1,'data':{'title':'Workshop favourites','max_count':4,'products':[{'id':state['products'][p['slug']]['id'],'enabled':True} for p in products if p['status']=='enabled']}},
             {'language':'en','type':0,'text':render('home-bottom.html'),'sort_order':2}]
            if home else [{'language':'en','type':0,'text':render('about.html'),'sort_order':0}])
    for b in blocks:
        match=next((c for c in contents if c['sort_order']==b['sort_order']),None)
        if match:api('/contents/'+str(match['id']),b,'PATCH')
        else:api('/content-targets/page/'+page_id+'/contents',b)
    print('Updated page',s,flush=True)
api('/pages/menu',{'tree':[{'kind':'shop','children':[]},{'kind':'page','page_id':str(pages['about']['id']),'children':[]}]},'PUT')
state['completed']=True;save_state()
print('Completed catalogue, home/about pages, social links and theme.',flush=True)
