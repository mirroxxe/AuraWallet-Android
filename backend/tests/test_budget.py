"""Тести функції «Бюджет місяця» (ЛР3)."""
from datetime import date

import pytest
from pydantic import ValidationError

from app.schemas import BudgetSummaryRequest
from app.service import budget_summary

TODAY = date(2026, 11, 18)  # у листопаді 30 днів → 13 днів до кінця, включно з сьогодні


def summary(budget, items, today=TODAY):
    return budget_summary(BudgetSummaryRequest(monthly_budget=budget, transactions=items, today=today))


def test_basic_summary():
    r = summary(1000, [{"amount": 85, "category": "coffee"}, {"amount": 486.3, "category": "groceries"}])
    assert r.spent == 571.3 and r.remaining == 428.7 and r.percent_used == 57.1
    assert r.status == "ok" and r.days_left == 13 and r.daily_allowance == round(428.7 / 13, 2)
    assert [c.category for c in r.by_category] == ["groceries", "coffee"]
    assert abs(sum(c.share for c in r.by_category) - 1) < 1e-3


@pytest.mark.parametrize("spent,status", [(799.9, "ok"), (800, "warning"), (999.99, "warning"), (1000, "exceeded"), (1500, "exceeded")])
def test_thresholds(spent, status):
    assert summary(1000, [{"amount": spent, "category": "shopping"}]).status == status


def test_income_not_counted():
    r = summary(1000, [{"amount": 38000, "category": "salary", "is_income": True}, {"amount": 100, "category": "cafes"}])
    assert r.spent == 100 and r.income == 38000 and r.counted_transactions == 1


def test_empty_list():
    r = summary(18000, [])
    assert r.spent == 0 and r.by_category == [] and r.status == "ok" and r.daily_allowance > 0


def test_exceeded_daily_allowance_is_zero():
    r = summary(100, [{"amount": 150, "category": "travel"}])
    assert r.remaining == -50 and r.daily_allowance == 0 and "вичерпано" in r.message


def test_last_day_of_month():
    assert summary(1000, [], today=date(2026, 2, 28)).days_left == 1


def test_warning_message():
    assert "80%" in summary(1000, [{"amount": 850, "category": "coffee"}]).message


@pytest.mark.parametrize("payload", [
    {"monthly_budget": 0, "transactions": []},
    {"monthly_budget": -5, "transactions": []},
    {"monthly_budget": 1000, "transactions": [{"amount": 0, "category": "coffee"}]},
    {"monthly_budget": 1000, "transactions": [{"amount": 10, "category": "unknown"}]},
    {"transactions": []},
])
def test_invalid_requests(payload):
    with pytest.raises(ValidationError):
        BudgetSummaryRequest(**payload)
