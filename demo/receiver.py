#!/usr/bin/env python3
"""Минимальный приёмник для демо лабы.

Поднимается на твоём VPS, слушает порт, логирует каждое входящее сообщение
от /spam/send и отвечает 200. Никаких зависимостей - только stdlib.

Запуск:
    python3 receiver.py
    PORT=8081 python3 receiver.py

Потом на стороне приложения:
    export SENDER_TARGET_URL=http://<IP-этого-VPS>:9000/inbox
"""
import json
import os
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "9000"))
_seq = 0


class Handler(BaseHTTPRequestHandler):
    def _read_body(self):
        te = self.headers.get("Transfer-Encoding", "").lower()
        if "chunked" in te:
            parts = []
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                try:
                    size = int(line.split(b";")[0], 16)
                except ValueError:
                    break
                if size == 0:
                    self.rfile.readline()
                    break
                parts.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(parts)
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length) if length else b""

    def do_POST(self):
        global _seq
        raw = self._read_body()
        try:
            body = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            body = {"_raw": raw.decode("utf-8", "replace")}

        _seq += 1
        ts = datetime.now(timezone.utc).isoformat(timespec="seconds")
        print(f"[{ts}] #{_seq} {self.path} from {self.client_address[0]} -> {body}",
              flush=True)

        reply = json.dumps({"received": True, "seq": _seq, "at": ts}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(reply)))
        self.end_headers()
        self.wfile.write(reply)

    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"receiver is up\n")

    def handle_one_request(self):
        try:
            super().handle_one_request()
        except (ConnectionResetError, BrokenPipeError, TimeoutError):
            self.close_connection = True

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"receiver listening on 0.0.0.0:{PORT} (POST /inbox)", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
