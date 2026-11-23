"""Відтворювана оцінка AI-компонента (ЛР6).

Запуск:  cd backend && python -m eval.evaluate            # таблиці в stdout (Markdown)
         cd backend && python -m eval.evaluate --out ../docs/lab6/results.md

Набори даних (синтетичні, у цій папці):
  control_examples.csv   — контрольні приклади ЛР2/ЛР4 (перевірка регресії)
  personal_history.csv   — виправлення, які «користувач» зробив раніше (персональний контекст)
  personal_queries.csv   — нові запити того ж користувача з очікуваною категорією
"""
from __future__ import annotations

import argparse
import csv
import subprocess
from pathlib import Path

from app import service
from app.ai.classifier import TransactionClassifier
from app.config import Settings
from app.schemas import CategorizeRequest

HERE = Path(__file__).parent


def load(name: str) -> list[dict]:
    with (HERE / name).open(encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def supports_personalization() -> bool:
    return "personal_examples" in CategorizeRequest.model_fields


def run(clf, settings, text: str, examples: list[dict] | None = None):
    kwargs: dict = {"text": text}
    if examples is not None and supports_personalization():
        kwargs["personal_examples"] = examples
    return service.categorize(CategorizeRequest(**kwargs), clf, settings)


def version() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=HERE, text=True).strip()
    except Exception:  # noqa: BLE001 — поза git-репозиторієм
        return "unknown"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    args = ap.parse_args()

    settings = Settings()
    clf = TransactionClassifier()
    control, history, queries = load("control_examples.csv"), load("personal_history.csv"), load("personal_queries.csv")
    examples = [{"text": h["text"], "category": h["category"]} for h in history]
    personal = supports_personalization()

    out: list[str] = [
        f"Версія коду: `{version()}` · модель: TF-IDF + LogisticRegression, {clf.samples} прикладів, "
        f"{len(clf.classes)} класів · AI_MIN_CONFIDENCE={settings.min_confidence}"
        + (f" · AI_PERSONAL_MIN_SIMILARITY={settings.personal_min_similarity}" if personal else " · персоналізації немає"),
        "",
        "### Контрольні приклади (без персонального контексту)",
        "| # | Вхід | Очікувано | Фактично | Впевненість | Сума / магазин | ✓ |",
        "|---|---|---|---|---|---|---|",
    ]
    ok = 0
    for i, row in enumerate(control, 1):
        r = run(clf, settings, row["text"])
        hit = r.category == row["expected"]
        ok += hit
        money = f"{r.amount:g} {r.currency}" if r.amount is not None else "—"
        out.append(f"| {i} | {row['text']} | {row['expected']} | {r.category}{' (low)' if r.low_confidence else ''} | "
                   f"{r.confidence:.2f} | {money} / {r.merchant or '—'} | {'✅' if hit else '❌'} |")
    out += ["", f"Точність на контрольних прикладах: **{ok}/{len(control)}**", ""]

    out += ["### Персональні запити", "| # | Вхід | Очікувано | Без контексту | З контекстом | Збіг з історією |", "|---|---|---|---|---|---|"]
    base_ok = pers_ok = 0
    for i, row in enumerate(queries, 1):
        b = run(clf, settings, row["text"])
        p = run(clf, settings, row["text"], examples) if personal else b
        base_ok += b.category == row["expected"]
        pers_ok += p.category == row["expected"]
        info = getattr(p, "personalization", None)
        match = f"«{info.matched_text}» {info.similarity:.2f}" if info else "—"
        out.append(f"| {i} | {row['text']} | {row['expected']} | {b.category} {'✅' if b.category == row['expected'] else '❌'} | "
                   f"{p.category} {'✅' if p.category == row['expected'] else '❌'} | {match} |")
    n = len(queries)
    out += ["", f"Точність без контексту: **{base_ok}/{n} ({base_ok / n:.0%})** · з контекстом: **{pers_ok}/{n} ({pers_ok / n:.0%})**", ""]

    if personal:
        changed = []
        for row in control:
            a, b = run(clf, settings, row["text"]), run(clf, settings, row["text"], examples)
            if (a.category, a.low_confidence) != (b.category, b.low_confidence):
                changed.append(row["text"])
        out += ["### Регресія: контрольні приклади з персональним контекстом",
                f"Змінено результатів: **{len(changed)}/{len(control)}**" + (f" ({', '.join(changed)})" if changed else ""), ""]

    text = "\n".join(out)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
