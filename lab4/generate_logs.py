import json
import random
from datetime import datetime, timedelta, timezone

random.seed(42)

services = ["api", "auth", "payments", "orders", "database"]
levels = ["INFO", "INFO", "INFO", "WARN", "ERROR"]

messages = {
    "api": [
        "GET /api/products returned 200",
        "Request processing completed",
        "Request to inventory service timed out",
        "Failed to serialize response",
    ],
    "auth": [
        "User login successful",
        "Access token refreshed",
        "Invalid password provided",
        "Authentication service unavailable",
    ],
    "payments": [
        "Payment processed successfully",
        "Payment gateway response delayed",
        "Payment transaction failed",
        "Connection to payment provider refused",
    ],
    "orders": [
        "Order created successfully",
        "Order status updated",
        "Failed to reserve inventory",
        "Order processing took longer than expected",
    ],
    "database": [
        "Database connection established",
        "Query executed successfully",
        "Database connection pool exhausted",
        "Query execution failed",
    ],
}

errors = [
    ("payments", "ERROR", "Payment transaction failed"),
    ("auth", "ERROR", "Authentication service unavailable"),
    ("database", "ERROR", "Database connection pool exhausted"),
    ("orders", "ERROR", "Failed to reserve inventory"),
    ("api", "ERROR", "Failed to serialize response"),
]

logs = []
start = datetime(2026, 10, 15, 8, 0, tzinfo=timezone.utc)

for i in range(30):
    timestamp = start + timedelta(minutes=i * 7)
    service = random.choice(services)
    level = random.choice(levels)
    message = random.choice(messages[service])

    if i in [4, 9, 15, 21, 27]:
        service, level, message = errors[i % len(errors)]

    logs.append({
        "timestamp": timestamp.isoformat(),
        "level": level,
        "service": service,
        "message": message,
        "trace_id": f"trace-{1000 + i:04d}",
    })

with open("bulk.ndjson", "w", encoding="utf-8") as f:
    for log in logs:
        f.write(json.dumps({"index": {"_index": "app-logs"}}) + "\n")
        f.write(json.dumps(log, ensure_ascii=False) + "\n")

print(f"Generated {len(logs)} log records in bulk.ndjson")
