#!/usr/bin/python3
"""Restore both databases into throwaway databases, never over the running shop."""
import os
from pathlib import Path
import subprocess
import tempfile
import uuid

env=os.environ.copy()
for name in ['restic.env','s3.env']:
    env.update(line.split('=',1) for line in (Path('/etc/shoptopus')/name).read_text().splitlines() if line and not line.startswith('#'))
with tempfile.TemporaryDirectory(prefix='shoptopus-restore-',dir='/var/backups/shoptopus') as temp:
    for database in ['shoptopus','shoptopus_logs']:
        history='flyway_schema_history_'+('main' if database=='shoptopus' else 'logs')
        version_query=f"SELECT version FROM {history} WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
        version=subprocess.check_output(['runuser','-u','postgres','--','psql','-tAc',version_query,database],text=True).strip()
        assert version, 'Live migration baseline is missing'
        dump=Path(temp)/(database+'.dump')
        with dump.open('wb') as out:
            subprocess.run(['restic','dump','latest',f'/var/backups/shoptopus/staging/{database}.dump'],env=env,stdout=out,check=True)
        target='shoptopus_restore_'+uuid.uuid4().hex[:12]
        subprocess.run(['runuser','-u','postgres','--','createdb','--owner',database,target],check=True)
        try:
            with dump.open('rb') as source:
                subprocess.run(['runuser','-u','postgres','--','/usr/lib/postgresql/16/bin/pg_restore','--exit-on-error','--no-owner','--no-acl','--role',database,'--dbname',target],stdin=source,stdout=subprocess.DEVNULL,check=True)
            result=subprocess.check_output(['runuser','-u','postgres','--','psql','-tAc',version_query,target],text=True).strip()
            assert result==version, (database,result)
            if database=='shoptopus':
                count=subprocess.check_output(['runuser','-u','postgres','--','psql','-tAc',"SELECT COUNT(*) FROM users WHERE id='11111111-1111-1111-1111-111111111111'",target],text=True).strip()
                assert count=='1'
            print(f'PASS: offsite {database} backup restored into disposable database; migration {version} verified.')
        finally:
            subprocess.run(['runuser','-u','postgres','--','dropdb',target],check=True)
