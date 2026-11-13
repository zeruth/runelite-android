#!/usr/bin/env python3
"""Thin wrapper: the Swing model contracts now run under run-awt-contracts.py, which discovers the shadow sources."""
import subprocess
import sys
from pathlib import Path

if __name__ == "__main__":
    runner = Path(__file__).resolve().with_name("run-awt-contracts.py")
    sys.exit(subprocess.call([sys.executable, str(runner), "--only", "TestSwingContracts", "--only", "TestPluginSwingGaps", *sys.argv[1:]]))
