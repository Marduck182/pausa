"""Servidor local para la vista previa de diseño de Pausa.

Sirve esta carpeta en http://127.0.0.1:4173 y guarda las notas del panel «Pedir cambio» en feedback.jsonl,
una línea por nota, para que se puedan leer después. Solo escucha en 127.0.0.1: no es visible desde la red.

Uso:  python server.py   (PORT=4174 python server.py para otro puerto)
"""

import json
import os
import sys
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
FEEDBACK = HERE / "feedback.jsonl"
HOST, PORT = "127.0.0.1", int(os.environ.get("PORT", "4173"))
MAX_BODY = 8 * 1024
MAX_NOTE = 2000


def read_notes():
    if not FEEDBACK.exists():
        return []
    notes = []
    for line in FEEDBACK.read_text(encoding="utf-8").splitlines():
        try:
            notes.append(json.loads(line))
        except json.JSONDecodeError:
            continue  # una línea dañada no impide leer las demás
    return notes


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(HERE), **kwargs)

    def end_headers(self):
        # Siempre la versión más reciente mientras se itera el diseño.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.split("?")[0] == "/api/feedback":
            self._json(200, read_notes())
        else:
            super().do_GET()

    def do_POST(self):
        if self.path != "/api/feedback":
            self._json(404, {"error": "no existe"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            self._json(413, {"error": "tamaño no válido"})
            return
        try:
            data = json.loads(self.rfile.read(length).decode("utf-8"))
            note = str(data.get("note", "")).strip()[:MAX_NOTE]
            screen = str(data.get("screen", "General")).strip()[:60] or "General"
        except (ValueError, UnicodeDecodeError):
            self._json(400, {"error": "JSON no válido"})
            return
        if not note:
            self._json(400, {"error": "nota vacía"})
            return
        entry = {"at": datetime.now(timezone.utc).isoformat(), "screen": screen, "note": note}
        with FEEDBACK.open("a", encoding="utf-8") as out:
            out.write(json.dumps(entry, ensure_ascii=False) + "\n")
        self._json(201, entry)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s\n" % (fmt % args))


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Vista previa en http://{HOST}:{PORT}  (Ctrl+C para cerrar)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
