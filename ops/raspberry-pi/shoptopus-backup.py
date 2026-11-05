#!/usr/bin/python3
"""Quiesce this shop, back up both databases and media, then resume service."""
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

def command(*args, **kw):
    return subprocess.run(args, check=True, text=True, **kw)

def main():
    lock = open('/run/lock/shoptopus-backup.lock', 'w')
    fcntl.flock(lock, fcntl.LOCK_EX)
    if shutil.disk_usage('/var/backups/shoptopus').free < 2 * 1024**3:
        raise RuntimeError('Less than 2 GiB available for a safe backup')
    env = os.environ.copy()
    env.update(RESTIC_REPOSITORY='/var/backups/shoptopus/restic', RESTIC_PASSWORD_FILE='/etc/shoptopus/restic-password', RESTIC_CACHE_DIR='/var/backups/shoptopus/cache')
    active = subprocess.run(['systemctl','is-active','--quiet','shoptopus']).returncode == 0
    stage = Path('/var/backups/shoptopus/staging')
    stage.mkdir(mode=0o700,exist_ok=True)
    try:
        if active:
            command('systemctl','stop','shoptopus')
        for database in ['shoptopus','shoptopus_logs']:
            with (stage / (database+'.dump')).open('wb') as out:
                subprocess.run(['runuser','-u','postgres','--','/usr/lib/postgresql/16/bin/pg_dump','--format=custom','--no-owner','--no-acl',database],check=True,stdout=out)
            command('/usr/lib/postgresql/16/bin/pg_restore','--list',str(stage/(database+'.dump')),stdout=subprocess.DEVNULL)
        command('restic','backup','--tag','shoptopus','--host','homelab','/etc/shoptopus','/var/lib/shoptopus/media',str(stage),env=env)
        command('restic','forget','--tag','shoptopus','--keep-daily','7','--keep-weekly','4','--keep-monthly','3',env=env)
    finally:
        shutil.rmtree(stage)
        if active:
            command('systemctl','start','shoptopus')
    # Upload the consistent local snapshot after resuming the app, keeping downtime short.
    offsite=Path('/etc/shoptopus/s3.env')
    if offsite.exists():
        remote=env.copy()
        remote.update(line.split('=',1) for line in offsite.read_text().splitlines() if line and not line.startswith('#'))
        command('restic','copy','--from-repo','/var/backups/shoptopus/restic',
                '--from-password-file','/etc/shoptopus/restic-password','latest',env=remote)
        command('restic','forget','--tag','shoptopus','--keep-daily','7','--keep-weekly','4','--keep-monthly','3',env=remote)

if __name__ == '__main__':
    main()
