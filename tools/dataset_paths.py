"""Shared, configurable locations for large dictionary source and generated files."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping

ENVIRONMENT_VARIABLE = "LANG_DATABASE_DIR"
LOCAL_PROPERTY = "dictionaryDataDir"
FALLBACK_DIRECTORY = Path(".local") / "dictionary-data"


@dataclass(frozen=True)
class DatasetPaths:
    root: Path
    dataset: str

    @property
    def source(self) -> Path:
        return self.root / self.dataset / "source"

    @property
    def generated(self) -> Path:
        return self.root / self.dataset / "generated"

    @property
    def packs(self) -> Path:
        return self.root / self.dataset / "packs"


def resolve_dataset_root(
    project_root: Path,
    environment: Mapping[str, str] | None = None,
    local_properties: Path | None = None,
) -> Path:
    """Resolve env > local.properties > project-local fallback without requiring D:."""
    environment = os.environ if environment is None else environment
    configured = environment.get(ENVIRONMENT_VARIABLE, "").strip()
    if configured:
        return _absolute(Path(configured).expanduser(), project_root)

    properties_path = local_properties or project_root / "local.properties"
    property_value = _read_property(properties_path, LOCAL_PROPERTY)
    if property_value:
        return _absolute(Path(property_value).expanduser(), project_root)
    return (project_root / FALLBACK_DIRECTORY).resolve()


def dataset_paths(dataset: str, project_root: Path | None = None) -> DatasetPaths:
    project = (project_root or Path(__file__).resolve().parents[1]).resolve()
    return DatasetPaths(resolve_dataset_root(project), dataset)


def dataset_root_summary(root: Path) -> str:
    return f"Dictionary dataset root:\n{root}"


def _absolute(path: Path, project_root: Path) -> Path:
    return path.resolve() if path.is_absolute() else (project_root / path).resolve()


def _read_property(path: Path, key: str) -> str | None:
    if not path.is_file():
        return None
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        for separator in ("=", ":"):
            marker = line.find(separator)
            if marker > 0 and line[marker - 1] != "\\":
                if line[:marker].strip() == key:
                    return _unescape_java_property(line[marker + 1 :].strip())
        if line.startswith(key) and len(line) > len(key) and line[len(key)].isspace():
            return _unescape_java_property(line[len(key) :].strip())
    return None


def _unescape_java_property(value: str) -> str:
    result: list[str] = []
    index = 0
    while index < len(value):
        if value[index] == "\\" and index + 1 < len(value):
            result.append(value[index + 1])
            index += 2
        else:
            result.append(value[index])
            index += 1
    return "".join(result)
