import re
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("runbook-mcp", host="0.0.0.0", port=8002)

RUNBOOKS = [
    {"id": "RB-101", "title": "Docker registry rate limit (toomanyrequests)",
     "keywords": ["toomanyrequests", "rate", "limit", "pull", "registry", "docker", "build", "image"],
     "category": "infrastructure",
     "steps": ["Infrastructure issue, not a code bug: do NOT revert commits.",
               "Re-run the job after 10-15 minutes.",
               "If it repeats, switch FROM to the internal mirror mirror.shiploop.io.",
               "Notify #platform if it blocks a release."]},
    {"id": "RB-102", "title": "Flaky e2e test (Playwright timeout)",
     "keywords": ["playwright", "e2e", "timeout", "locator", "selector", "flaky", "click"],
     "category": "flaky",
     "steps": ["Check the job history: if it failed in several of the last 20 runs, it is flaky.",
               "Retry the job once.",
               "If the test fails 3+ times in a row, add it to the quarantine list and open a ticket for the owner.",
               "Never block a merge because of a quarantined test."]},
    {"id": "RB-103", "title": "Unit test assertion failure after a code change",
     "keywords": ["pytest", "assertion", "assertionerror", "unit", "test", "failed", "assert", "decimal"],
     "category": "code",
     "steps": ["Compare the failing test with the commit diff of the pipeline.",
               "If a commit changed behavior, either fix the code or update the test (author decides).",
               "On main/release branches: if the fix is not ready within 1 hour, revert the commit."]},
    {"id": "RB-104", "title": "Lint failure (ruff / eslint)",
     "keywords": ["lint", "ruff", "eslint", "format", "style"],
     "category": "code",
     "steps": ["Run the formatter locally and push the fix.", "Never disable a rule just to pass CI."]},
    {"id": "RB-105", "title": "Runner out of disk space",
     "keywords": ["space", "disk", "enospc", "runner", "device"],
     "category": "infrastructure",
     "steps": ["Run docker system prune on the runner.", "Notify #platform to rotate the runner."]},
]

SERVICES = {
    "billing-service": {"team": "Billing", "oncall": "dmitry.v", "slack": "#team-billing"},
    "web-frontend": {"team": "Frontend", "oncall": "ivan.p", "slack": "#team-frontend"},
    "platform": {"team": "Platform / DevOps", "oncall": "olga.s", "slack": "#platform"},
}


@mcp.tool()
def search_runbooks(query: str) -> dict:
    """Search the runbook knowledge base by keywords taken from an error message.
    Returns up to 3 best-matching runbooks with category and steps."""
    words = set(re.findall(r"[a-z0-9]+", query.lower()))
    scored = []
    for rb in RUNBOOKS:
        score = len(words & set(rb["keywords"]))
        if score:
            scored.append((score, rb))
    scored.sort(key=lambda x: -x[0])
    return {"query": query, "matches": [rb for _, rb in scored[:3]]}


@mcp.tool()
def get_service_owner(service: str) -> dict:
    """Get the owning team, on-call engineer and Slack channel of a service."""
    s = SERVICES.get(service)
    if not s:
        return {"error": f"unknown service '{service}'", "known": list(SERVICES)}
    return {"service": service, **s}


if __name__ == "__main__":
    mcp.run(transport="streamable-http")
