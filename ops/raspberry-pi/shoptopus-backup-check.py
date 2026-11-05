#!/usr/bin/python3
"""Check and reclaim unreferenced data in local and configured offsite backups."""
import fcntl
import os
from pathlib import Path
import subprocess

lock=open('/run/lock/shoptopus-backup.lock','w')
fcntl.flock(lock,fcntl.LOCK_EX)
env=os.environ.copy()
for configuration in ['/etc/shoptopus/restic.env','/etc/shoptopus/s3.env']:
    path=Path(configuration)
    if path.exists():
        env.update(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))
        subprocess.run(['restic','check','--read-data-subset=10%'],env=env,check=True)
        subprocess.run(['restic','prune'],env=env,check=True)
