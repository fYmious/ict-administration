from mcp.server.fastmcp import FastMCP

mcp = FastMCP("ci-mcp", host="0.0.0.0", port=8001)

PIPELINES = {
    1001: {
        "project": "shiploop/monorepo", "ref": "main",
        "commits": [
            {"sha": "a1b2c3d", "author": "dmitry.v", "date": "2026-10-21T09:12:00Z",
             "message": "billing: switch invoice rounding to banker's rounding",
             "files": ["services/billing/invoice.py"]},
            {"sha": "e4f5a6b", "author": "anna.k", "date": "2026-10-21T08:40:00Z",
             "message": "docs: update billing README",
             "files": ["services/billing/README.md"]},
        ],
    },
    1002: {
        "project": "shiploop/monorepo", "ref": "release/2.4",
        "commits": [
            {"sha": "9c8d7e6", "author": "olga.s", "date": "2026-10-21T10:05:00Z",
             "message": "docker: bump base image python:3.12 -> 3.13",
             "files": ["Dockerfile"]},
        ],
    },
    1003: {
        "project": "shiploop/monorepo", "ref": "main",
        "commits": [
            {"sha": "3b2a1f0", "author": "ivan.p", "date": "2026-10-21T11:20:00Z",
             "message": "ui: new checkout modal layout",
             "files": ["web/src/checkout/Modal.tsx"]},
        ],
    },
}

JOBS = {
    4001: {"pipeline_id": 1001, "name": "lint", "stage": "lint", "status": "success",
           "service": "billing-service", "last_20_runs_failed": 0},
    4002: {"pipeline_id": 1001, "name": "unit-tests-billing", "stage": "test", "status": "failed",
           "service": "billing-service", "last_20_runs_failed": 0},
    4003: {"pipeline_id": 1001, "name": "build-image", "stage": "build", "status": "skipped",
           "service": "billing-service", "last_20_runs_failed": 0},
    4011: {"pipeline_id": 1002, "name": "build-image", "stage": "build", "status": "failed",
           "service": "platform", "last_20_runs_failed": 1},
    4021: {"pipeline_id": 1003, "name": "e2e-checkout", "stage": "e2e", "status": "failed",
           "service": "web-frontend", "last_20_runs_failed": 6},
}

LOGS = {
    4002: """$ pytest services/billing -x
============ test session starts ============
collected 58 items
services/billing/tests/test_invoice.py::test_invoice_rounding FAILED
================= FAILURES =================
_____ test_invoice_rounding _____
    def test_invoice_rounding():
        invoice = build_invoice(items=[Item(price=Decimal("10.125"), qty=1)])
>       assert invoice.total == Decimal("10.13")
E       AssertionError: assert Decimal('10.12') == Decimal('10.13')
services/billing/tests/test_invoice.py:41: AssertionError
======= 1 failed, 57 passed in 12.48s =======
ERROR: Job failed: exit code 1""",
    4011: """$ docker build -t registry.shiploop.io/monorepo/api:2.4.0 .
#1 [internal] load metadata for docker.io/library/python:3.13-slim
#1 ERROR: failed to authorize: failed to fetch anonymous token
toomanyrequests: You have reached your pull rate limit.
ERROR: Job failed: exit code 1""",
    4021: """$ npx playwright test e2e/checkout.spec.ts
Running 12 tests using 4 workers
  ok  cart.spec.ts:8 add item to cart (1.2s)
  ok  login.spec.ts:5 login (0.9s)
  FAIL checkout.spec.ts:23 pay with card (30.1s)
Error: locator.click: Timeout 30000ms exceeded.
  waiting for locator('[data-testid="pay-button"]')
11 passed, 1 failed (retries: 0)
ERROR: Job failed: exit code 1""",
}


@mcp.tool()
def get_failed_jobs(pipeline_id: int) -> dict:
    """List failed jobs of a CI pipeline (job id, name, stage, service,
    and how many of the last 20 runs of this job failed)."""
    p = PIPELINES.get(pipeline_id)
    if not p:
        return {"error": f"pipeline {pipeline_id} not found"}
    failed = [{"job_id": jid, **j} for jid, j in JOBS.items()
              if j["pipeline_id"] == pipeline_id and j["status"] == "failed"]
    return {"pipeline_id": pipeline_id, "ref": p["ref"], "failed_jobs": failed}


@mcp.tool()
def get_job_log(job_id: int) -> dict:
    """Get the tail of the log of a CI job by its job id."""
    log = LOGS.get(job_id)
    if log is None:
        return {"error": f"no log for job {job_id}"}
    return {"job_id": job_id, "log": log}


@mcp.tool()
def get_pipeline_commits(pipeline_id: int) -> dict:
    """Get commits (sha, author, message, changed files) included in a pipeline."""
    p = PIPELINES.get(pipeline_id)
    if not p:
        return {"error": f"pipeline {pipeline_id} not found"}
    return {"pipeline_id": pipeline_id, "ref": p["ref"], "commits": p["commits"]}


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
