#!/usr/bin/env python3
"""Local mock for pipeline tests: OpenAI-compatible ASR + chat."""
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = "mock-requests.log"
ASR_TEXT = ("今天 我们 开个 Q 四 产品 复盘 会 张伟说 新手引导 要 在 11 月 10 号 前 上线 "
            "李娜 负责 把 转写 延迟 压到 3 秒 王强 先 投 10 万 测试 两周 以后 复盘")
CORRECTED = ("今天我们开个 Q4 产品复盘会。张伟说，新手引导要在 11 月 10 日前上线；"
             "李娜负责把转写延迟压到 3 秒；王强先投 10 万测试，两周以后复盘。")
ANALYSIS = {
    "title": "Q4 产品复盘会",
    "category": "会议",
    "summary": "会议确定新手引导在 11 月 10 日前上线，由张伟负责；李娜负责把语音转写首段延迟压到 3 秒以内；王强先投放 10 万元做两周小规模测试，之后再复盘获客成本。",
    "key_points": [
        "新手引导需在 11 月 10 日前上线",
        "语音转写首段延迟目标为 3 秒以内",
        "投放先以 10 万元做两周测试",
        "两周后复盘获客成本与留存"
    ],
    "actions": [
        "张伟｜上线新手引导｜11 月 10 日前",
        "李娜｜优化转写首段延迟｜3 秒以内",
        "王强｜执行 10 万元投放测试｜两周后复盘"
    ],
    "decisions": [
        "先做两周小规模投放，不直接放量",
        "新手引导与转写延迟作为本版本优先项"
    ],
    "tags": ["产品复盘", "Q4", "新手引导", "投放"]
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        line = "%s %s\n" % (self.command, self.path)
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(line)
        sys.stderr.write(line)

    def _body(self):
        n = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(n) if n else b""

    def _json(self, obj, code=200):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._json({"ok": True})
        else:
            self._json({"error": "not found"}, 404)

    def do_POST(self):
        body = self._body()
        with open(LOG, "a", encoding="utf-8") as f:
            f.write("POST %s bytes=%d\n" % (self.path, len(body)))
        if "audio/transcriptions" in self.path:
            self._json({"text": ASR_TEXT})
            return
        if "chat/completions" in self.path:
            try:
                req = json.loads(body.decode("utf-8", "replace"))
            except Exception:
                req = {}
            if isinstance(req.get("response_format"), dict):
                self._json({"choices": [{"message": {"content": json.dumps(ANALYSIS, ensure_ascii=False)}}]})
            else:
                self._json({"choices": [{"message": {"content": CORRECTED}}]})
            return
        self._json({"error": "not found"}, 404)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8899
    open(LOG, "w", encoding="utf-8").close()
    print("mock listening on", port, flush=True)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
