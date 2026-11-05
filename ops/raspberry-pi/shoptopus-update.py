#!/usr/bin/python3
"""Pull only cryptographically attested releases from the trusted main workflow."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

REPO='Shoptopus/mazeshop-spring'
IDENTITY=f'https://github.com/{REPO}/.github/workflows/pi-release.yml@refs/heads/main'
STATE=Path('/var/lib/shoptopus-deploy')
CURRENT=Path('/opt/shoptopus/current.jar')

def request(url):
    if not url.startswith('https://'):
        raise ValueError('HTTPS required')
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Shoptopus-deployer','Accept':'application/vnd.github+json'}),timeout=60)

def download(url,path,limit):
    size=0
    with request(url) as source, path.open('wb') as output:
        while chunk:=source.read(1024*1024):
            size+=len(chunk)
            if size>limit:
                raise RuntimeError('Release asset exceeds size limit')
            output.write(chunk)

def healthy():
    for _ in range(60):
        try:
            with urllib.request.urlopen('http://127.0.0.1:18080/api/storefront/config',timeout=5) as response:
                body=json.load(response)
                if response.status==200 and isinstance(body,dict) and 'data' in body:
                    return True
        except (OSError,ValueError):
            pass
        time.sleep(2)
    return False

def run(*args):
    subprocess.run(args,check=True)

def main():
    STATE.mkdir(exist_ok=True)
    with open('/run/lock/shoptopus-update.lock','w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        with request(f'https://api.github.com/repos/{REPO}/releases?per_page=30') as response:
            releases=json.load(response)
        releases=[r for r in releases if not r['draft'] and not r['prerelease'] and re.fullmatch(r'pi-production-[a-f0-9]{40}',r['tag_name'])]
        if not releases:
            print('No production release published yet; keeping the installed build.')
            return
        release=max(releases,key=lambda r:r['published_at'])
        tag=release['tag_name']
        installed=STATE/'installed-release'
        failed=STATE/'failed-release'
        if (installed.exists() and installed.read_text().strip()==tag) or (failed.exists() and failed.read_text().strip()==tag):
            print('Release already installed or requires operator review:',tag)
            return
        assets={a['name']:a for a in release['assets']}
        required=['shoptopus.jar','shoptopus.jar.sigstore.json']
        if any(name not in assets for name in required):
            raise RuntimeError('Release is missing its JAR or signed provenance bundle')
        commit=tag.removeprefix('pi-production-')
        with tempfile.TemporaryDirectory(prefix='download-',dir=STATE) as temp:
            temp=Path(temp)
            for name in required:
                asset=assets[name]
                expected_prefix=f'https://github.com/{REPO}/releases/download/{tag}/'
                if not asset['browser_download_url'].startswith(expected_prefix):
                    raise RuntimeError('Unexpected release asset URL')
                download(asset['browser_download_url'],temp/name,256*1024**2 if name.endswith('.jar') else 10*1024**2)
            jar=temp/'shoptopus.jar'
            run('/opt/shoptopus/gh','attestation','verify',str(jar),'--bundle',str(temp/'shoptopus.jar.sigstore.json'),'--repo',REPO,'--cert-identity',IDENTITY,'--source-ref','refs/heads/main','--source-digest',commit,'--deny-self-hosted-runners')
            # No root execution of downloaded code: only the sandboxed application user runs the JAR.
            release_path=Path('/opt/shoptopus/releases')/(tag+'.jar')
            shutil.copyfile(jar,release_path)
            release_path.chmod(0o644)
        previous=CURRENT.resolve(strict=True)
        (STATE/'maintenance').touch()
        try:
            run('/usr/local/sbin/shoptopus-backup')
            run('systemctl','stop','shoptopus')
            pending=CURRENT.with_suffix('.pending')
            pending.unlink(missing_ok=True)
            pending.symlink_to(release_path)
            os.replace(pending,CURRENT)
            run('systemctl','reset-failed','shoptopus')
            run('systemctl','start','shoptopus')
            if not healthy():
                raise RuntimeError('New release did not pass startup health checks')
            installed.write_text(tag+'\n')
            failed.unlink(missing_ok=True)
            (STATE/'maintenance').unlink(missing_ok=True)
            print('Installed and health checked:',tag)
        except Exception:
            failed.write_text(tag+'\n')
            run('systemctl','stop','shoptopus')
            pending=CURRENT.with_suffix('.pending')
            pending.unlink(missing_ok=True)
            pending.symlink_to(previous)
            os.replace(pending,CURRENT)
            run('systemctl','reset-failed','shoptopus')
            run('systemctl','start','shoptopus')
            if healthy():
                (STATE/'maintenance').unlink(missing_ok=True)
            # Database migrations are not automatically undone. Restore the predeployment backup if incompatible.
            raise

if __name__=='__main__':
    main()
