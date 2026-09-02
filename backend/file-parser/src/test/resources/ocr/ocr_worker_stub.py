#!/usr/bin/env python3
"""测试用 OCR worker：不加载模型，只回显协议。"""
import json
import sys

print(json.dumps({"ok": True, "event": "ready"}), flush=True)
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    req = json.loads(line)
    if req.get("cmd") == "shutdown":
        break
    print(
        json.dumps(
            {
                "id": req.get("id"),
                "ok": True,
                "text": "stub:" + req.get("image", ""),
                "confidence": 0.99,
            }
        ),
        flush=True,
    )
