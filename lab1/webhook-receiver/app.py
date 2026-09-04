from http.server import BaseHTTPRequestHandler, HTTPServer
import json

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length)
        print("ALERT", flush=True)
        try:
            data = json.loads(body)
            print(json.dumps(data, indent=2), flush=True)
        except Exception:
            print(body, flush=True)
        self.send_response(200)
        self.end_headers()

    def log_message(self, format, *args):
        pass

HTTPServer(('0.0.0.0', 5001), Handler).serve_forever()
