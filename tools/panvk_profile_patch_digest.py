#!/usr/bin/env python3
"""Compute a deterministic digest of only the patch families selected by a PanVK profile.

This intentionally differs from upstream compute-patch-series-id.py, which hashes every
patch family in the repository. A profile-scoped digest changes only when an input that
apply-patches.sh actually selects for that profile changes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def selected_families(root: Path, profile_name: str) -> tuple[dict, list[str]]:
    profile_path = root / "profiles" / f"{profile_name}.json"
    profile = json.loads(profile_path.read_text(encoding="utf-8"))
    arch = int(profile["panArch"])
    frontend = profile["frontend"]
    families = ["common", "android", "kbase-common", "app-loader", "wsi"]
    if frontend == "CSF":
        families.extend(["csf", f"csf-v{arch}"])
    else:
        families.append(f"jm-v{arch}")
    if profile_name == "g615-v11-csf":
        families.append("jm-v9")
    return profile, families


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--profile", required=True)
    args = parser.parse_args()

    root = args.root.resolve()
    lock = json.loads((root / "sources.lock").read_text(encoding="utf-8"))
    mesa_commit = lock["mesaCommit"].strip()
    profile, families = selected_families(root, args.profile)
    profile_name = f"{args.profile}.json"

    inputs: list[tuple[str, Path]] = []
    for family in families:
        family_root = root / "patches" / family
        if not family_root.exists():
            continue
        for path in sorted(family_root.rglob("*")):
            if not path.is_file() or path.name.startswith("."):
                continue
            relative = path.relative_to(root).as_posix()
            if path.suffix == ".patch" or "/files/" in relative:
                if "/files/" in relative and path.name.upper().startswith("README"):
                    continue
                inputs.append((relative, path))

    digest = hashlib.sha256()
    digest.update(f"mesaCommit:{mesa_commit}\n".encode())
    canonical_profile = json.dumps(profile, sort_keys=True, indent=2)
    digest.update(f"profile:{profile_name}\n{canonical_profile}\n".encode())
    for relative, path in sorted(inputs, key=lambda item: item[0]):
        digest.update(f"file:{relative}\n".encode())
        digest.update(path.read_bytes())
        digest.update(b"\n")

    print("sha256:" + digest.hexdigest())
    print("families=" + ",".join(families))
    print("files=" + str(len(inputs)))


if __name__ == "__main__":
    main()
