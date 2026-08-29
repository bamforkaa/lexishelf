"""Report measured display-form and morphology-index statistics from generated DBs."""

from __future__ import annotations

import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import statistics
import sys
import time

from tools.dataset_paths import dataset_paths
from tools.kaikki_language_config import (
    KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)
from tools.kaikki_form_policy import normalize_key


def percentile(values: list[int], fraction: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))]


def report_database(path: Path, language: str) -> dict[str, object]:
    with closing(sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)) as database:
        schema = database.execute("PRAGMA user_version").fetchone()[0]
        quick_check = database.execute("PRAGMA quick_check").fetchone()[0]
        display_counts = [
            len(json.loads(payload)["f"])
            for (payload,) in database.execute("SELECT payload FROM entries")
        ]
        lemma_counts = [
            count
            for (count,) in database.execute(
                """
                SELECT COUNT(DISTINCT normalized_lemma)
                FROM morphology_forms
                GROUP BY normalized_form
                """
            )
        ]
        ambiguous_lemma_counts = [count for count in lemma_counts if count > 1]
        ambiguous_case_variant_surfaces = database.execute(
            """
            SELECT COUNT(*)
            FROM (
                SELECT normalized_form
                FROM morphology_forms
                GROUP BY normalized_form
                HAVING COUNT(DISTINCT normalized_lemma) > 1
                   AND COUNT(DISTINCT form) > 1
            )
            """
        ).fetchone()[0]
        pathological_ambiguities = [
            {
                "surface": surface,
                "lemmaCount": count,
                "lemmas": lemmas.split("\u001f"),
            }
            for surface, count, lemmas in database.execute(
                """
                SELECT normalized_form,
                       COUNT(*) AS lemma_count,
                       GROUP_CONCAT(lemma, '\u001f')
                FROM (
                    SELECT normalized_form, normalized_lemma, MIN(lemma) AS lemma
                    FROM morphology_forms
                    GROUP BY normalized_form, normalized_lemma
                )
                GROUP BY normalized_form
                HAVING lemma_count > 1
                ORDER BY lemma_count DESC, normalized_form
                LIMIT 20
                """
            )
        ]
        morphology_rows = database.execute(
            "SELECT COUNT(*) FROM morphology_forms"
        ).fetchone()[0]
        morphology_entry_count = database.execute(
            "SELECT COUNT(DISTINCT source_entry_id) FROM morphology_forms"
        ).fetchone()[0]
        morphology_rows_by_pos = {
            (pos or "<none>"): count
            for pos, count in database.execute(
                """
                SELECT json_extract(entries.payload, '$.p'), COUNT(*)
                FROM morphology_forms
                JOIN entries ON entries.entry_id = morphology_forms.source_entry_id
                GROUP BY 1
                ORDER BY 2 DESC, 1
                """
            )
        }
        unique_surfaces = len(lemma_counts)
        samples = [
            {"surface": surface, "lemma": lemma}
            for surface, lemma in database.execute(
                """
                SELECT MIN(form), MIN(lemma)
                FROM morphology_forms
                GROUP BY normalized_form, normalized_lemma
                ORDER BY MIN(entry_order), MIN(form_order), normalized_form, normalized_lemma
                LIMIT 3
                """
            )
        ]
        lookup_samples = [item["surface"] for item in samples]
        lookup_millis: list[float] = []
        for surface in lookup_samples * 20:
            started = time.perf_counter()
            database.execute(
                "SELECT lemma FROM morphology_forms WHERE normalized_form = ? LIMIT 6",
                (normalize_key(surface),),
            ).fetchall()
            lookup_millis.append((time.perf_counter() - started) * 1_000)
    return {
        "language": language,
        "database": str(path),
        "databaseBytes": path.stat().st_size,
        "schema": schema,
        "quickCheck": quick_check,
        "entryCount": len(display_counts),
        "displayFormCount": sum(display_counts),
        "displayAverage": round(statistics.fmean(display_counts), 3) if display_counts else 0,
        "displayP95": percentile(display_counts, 0.95),
        "displayMax": max(display_counts, default=0),
        "morphologyRows": morphology_rows,
        "morphologyEntryCount": morphology_entry_count,
        "morphologyRowsByPos": morphology_rows_by_pos,
        "uniqueSurfaces": unique_surfaces,
        "lemmasPerSurfaceAverage": round(statistics.fmean(lemma_counts), 3) if lemma_counts else 0,
        "lemmasPerSurfaceP95": percentile(lemma_counts, 0.95),
        "lemmasPerSurfaceMax": max(lemma_counts, default=0),
        "ambiguousSurfaceCount": len(ambiguous_lemma_counts),
        "ambiguousSurfaceRatio": round(
            len(ambiguous_lemma_counts) / unique_surfaces,
            6,
        ) if unique_surfaces else 0,
        "lemmasPerAmbiguousSurfaceAverage": round(
            statistics.fmean(ambiguous_lemma_counts),
            3,
        ) if ambiguous_lemma_counts else 0,
        "lemmasPerAmbiguousSurfaceP95": percentile(ambiguous_lemma_counts, 0.95),
        "ambiguousCaseVariantSurfaceCount": ambiguous_case_variant_surfaces,
        "pathologicalAmbiguities": pathological_ambiguities,
        "warmLookupMedianMs": round(statistics.median(lookup_millis), 4) if lookup_millis else None,
        "warmLookupP95Ms": round(sorted(lookup_millis)[int(len(lookup_millis) * 0.95) - 1], 4)
        if lookup_millis else None,
        "samples": samples,
    }


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--languages",
        nargs="+",
        default=KAIKKI_SUPPORTED_LANGUAGE_TAGS + KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS,
    )
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    generated = dataset_paths("kaikki").generated
    reports = []
    for language in args.languages:
        name = f"{language}-morphology.db" if language in KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS else f"{language}.db"
        reports.append(report_database(generated / name, language))
    rendered = json.dumps(reports, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
