"""Stage built dictionary packs on one explicitly named AVD for SAF installation."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
from typing import Mapping

from tools.build_dictionary_packs import PACKS, pack_file_name
from tools.dataset_paths import dataset_paths, dataset_root_summary

REMOTE_DIRECTORY = "/sdcard/Download/LocalVocabularyPacks"


def expected_pack_paths(project_root: Path) -> tuple[Path, ...]:
    return tuple(
        dataset_paths(definition.dataset, project_root).packs / pack_file_name(definition)
        for definition in PACKS
    )


def select_avd_serial(avds: Mapping[str, str], expected_name: str) -> str:
    matches = [serial for serial, name in avds.items() if name == expected_name]
    if len(matches) == 1:
        return matches[0]
    connected = ", ".join(f"{serial}={name or '<not an AVD>'}" for serial, name in avds.items())
    if not matches:
        raise RuntimeError(
            f"AVD '{expected_name}' is not connected. Connected devices: {connected or 'none'}",
        )
    raise RuntimeError(f"More than one connected device reports AVD name '{expected_name}'")


def resolve_adb(explicit: Path | None, environment: Mapping[str, str]) -> Path:
    if explicit is not None:
        return explicit.resolve()
    executable = "adb.exe" if os.name == "nt" else "adb"
    path_command = shutil.which(executable)
    if path_command:
        return Path(path_command).resolve()
    for variable in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        sdk = environment.get(variable, "").strip()
        if sdk:
            candidate = Path(sdk) / "platform-tools" / executable
            if candidate.is_file():
                return candidate.resolve()
    local_app_data = environment.get("LOCALAPPDATA", "").strip()
    if local_app_data:
        candidate = Path(local_app_data) / "Android" / "Sdk" / "platform-tools" / executable
        if candidate.is_file():
            return candidate.resolve()
    raise FileNotFoundError("adb was not found; pass --adb or configure the Android SDK path")


def connected_avds(adb: Path) -> dict[str, str]:
    output = _run(adb, "devices")
    devices: dict[str, str] = {}
    for line in output.splitlines()[1:]:
        columns = line.split()
        if len(columns) < 2 or columns[1] != "device":
            continue
        serial = columns[0]
        devices[serial] = _run(
            adb,
            "-s",
            serial,
            "shell",
            "getprop",
            "ro.boot.qemu.avd_name",
        ).strip()
    return devices


def stage_packs(adb: Path, serial: str, packs: tuple[Path, ...]) -> None:
    _run(adb, "-s", serial, "shell", "mkdir", "-p", REMOTE_DIRECTORY)
    for pack in packs:
        print(f"Staging {pack.name}")
        _run(adb, "-s", serial, "push", str(pack), f"{REMOTE_DIRECTORY}/{pack.name}")


def _run(executable: Path, *arguments: str) -> str:
    try:
        result = subprocess.run(
            [str(executable), *arguments],
            check=True,
            capture_output=True,
            text=True,
        )
    except subprocess.CalledProcessError as error:
        detail = error.stderr.strip() or error.stdout.strip() or str(error)
        raise RuntimeError(detail) from error
    if result.stdout.strip():
        print(result.stdout.strip())
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--avd-name",
        required=True,
        help="Exact connected AVD name, for example Medium_Phone_Manual",
    )
    parser.add_argument("--adb", type=Path, help="Optional explicit adb executable path")
    arguments = parser.parse_args()

    project_root = Path(__file__).resolve().parents[1]
    packs = expected_pack_paths(project_root)
    print(dataset_root_summary(dataset_paths(PACKS[0].dataset, project_root).root), flush=True)
    missing = [pack for pack in packs if not pack.is_file()]
    if missing:
        formatted = "\n".join(f"- {pack}" for pack in missing)
        parser.exit(
            1,
            "error: required packs are missing. Run "
            "'python -m tools.build_dictionary_packs' first:\n"
            f"{formatted}\n",
        )

    try:
        adb = resolve_adb(arguments.adb, os.environ)
        serial = select_avd_serial(connected_avds(adb), arguments.avd_name)
        print(f"Selected AVD: {arguments.avd_name} ({serial})")
        stage_packs(adb, serial, packs)
    except (FileNotFoundError, RuntimeError) as error:
        parser.exit(1, f"error: {error}\n")
    print(
        "Staging complete. This did not activate any pack. In the app, open Settings > "
        "로컬 pack 설치 and select each file from Download/LocalVocabularyPacks so the "
        "production validation and activation path runs.",
    )


if __name__ == "__main__":
    main()
