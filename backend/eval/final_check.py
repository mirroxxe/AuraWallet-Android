"""ЛР7: підсумкова перевірка AI на контрольному наборі (реальна модель, без mock).

Запуск: cd backend && python -m eval.final_check [--out ../docs/lab7/ai-check.md]
Набір: eval/final_examples.csv — kind = typical | boundary | invalid | previous.
  typical/boundary/invalid — функція ЛР6 (персоналізація; контекст = eval/personal_history.csv);
  previous — базове розпізнавання ЛР2 без контексту (регресія попередньої функції).
Некоректні входи (invalid) очікують відмову (валідація) і не входять до метрики точності.
"""
from __future__ import annotations

import argparse
import csv
import json
import time
from pathlib import Path

from pydantic import ValidationError

from app import service
from app.ai.classifier import TransactionClassifier
from app.config import Settings
from app.schemas import CategorizeRequest
from eval.evaluate import load, version

HERE = Path(__file__).parent


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    args = ap.parse_args()

    settings, clf = Settings(), TransactionClassifier()
    history = [{"text": h["text"], "category": h["category"]} for h in load("personal_history.csv")]
    rows = load("final_examples.csv")

    lines = [
        f"Версія коду: `{version()}` · модель: scikit-learn TF-IDF (char 2–5 + word 1–2) + LogisticRegression(C=12), "
        f"{clf.samples} навчальних прикладів, {len(clf.classes)} класів · AI_MIN_CONFIDENCE={settings.min_confidence} · "
        f"AI_PERSONAL_MIN_SIMILARITY={settings.personal_min_similarity}",
        "",
        "| # | Тип | Вхід | Очікувано | Фактично | Число (джерело) | Час, мс | Висновок |",
        "|---|---|---|---|---|---|---|---|",
    ]
    stats: dict[str, list[int]] = {}
    for i, row in enumerate(rows, 1):
        kind = row["kind"]
        examples = [] if kind == "previous" else history
        if row.get("examples_override"):
            examples = json.loads(row["examples_override"])
        started = time.perf_counter()
        try:
            req = CategorizeRequest(text=row["text"], personal_examples=examples)
            r = service.categorize(req, clf, settings)
            ms = (time.perf_counter() - started) * 1000
            actual = r.category + (" (low)" if r.low_confidence else "")
            number = f"{r.confidence:.2f} ({'схожість' if r.confidence_source == 'personal' else 'модель'})"
            ok = r.category == row["expected"]
        except ValidationError as exc:
            ms = (time.perf_counter() - started) * 1000
            field = ".".join(str(p) for p in exc.errors()[0]["loc"])
            actual, number, ok = f"відмова 422 ({field})", "—", row["expected"] == "reject"
        stats.setdefault(kind, []).append(int(ok))
        lines.append(f"| {i} | {kind} | {row['text']} | {row['expected']} | {actual} | {number} | {ms:.1f} | "
                     f"{'✅' if ok else '❌'} {row['note']} |")

    def frac(kinds):
        hits = [x for k in kinds for x in stats.get(k, [])]
        return f"{sum(hits)}/{len(hits)}"

    lines += [
        "",
        f"- Точність функції ЛР6 (typical + boundary, без invalid): **{frac(['typical', 'boundary'])}**",
        f"- Некоректні входи відхилено: **{frac(['invalid'])}**",
        f"- Попередня функція (ЛР2, без контексту): **{frac(['previous'])}**",
        "",
        "Час — обробка всередині backend (без мережі й мобільного UI), Python, CPU. "
        "Результат характеризує лише ці приклади; confidence/схожість не є гарантією правильності.",
    ]
    text = "\n".join(lines)
    if args.out:
        Path(args.out).write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
