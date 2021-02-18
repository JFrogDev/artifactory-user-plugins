#!/usr/bin/env python
#
# Run: python3 testserver.py

from http.server import BaseHTTPRequestHandler, HTTPServer
import time
import json

hostName = "localhost"
serverPort = 8888

class MyServer(BaseHTTPRequestHandler):

    def do_POST(self):
        # refuse to receive non-json content
        if self.headers.get('content-type') != 'application/json':
            self.send_response(400)
            self.end_headers()
            return

        length = int(self.headers.get('content-length'))
        message = json.loads(self.rfile.read(length))

        response = 0
        body = {}

        # curl -i -v -XPOST -H'Content-Type: application/json' http://localhost:8888/validateIP -d @testserver/testIP_true.json
        # curl -i -v -XPOST -H'Content-Type: application/json' http://localhost:8888/validateIP -d @testserver/testIP_false.json
        if self.path.upper() == "/validateIP".upper():
            if (message['Transaction_IP'] == "172.17.0.1" and
                message['AppName'] == "null" and
                message['Email'] == "null"):
                response = 200
                body = {'status': 'Approved', 'country': 'null', 'country2letter': 'null', 'domain': 'null', 'region': 'null', 'message': 'null'}
            else:
                response = 403
                body = {'status': 'DECLINE', 'country': 'null', 'country2letter': 'null', 'domain': 'null', 'region': 'null', 'message': 'null'}

        # curl -i -v -XPOST -H'Content-Type: application/json' http://localhost:8888/validateEntitlements -d @testserver/testEntitlements_true.json
        # curl -i -v -XPOST -H'Content-Type: application/json' http://localhost:8888/validateEntitlements -d @testserver/testEntitlements_false.json
        elif self.path.upper() == "/validateEntitlements".upper():
            if (message['Email'] == "null" and
                message['RepositoryName'] == "test-docker" and
                message['ModuleName'] == "test-image" and
                message['ProductNameSpace'] == "null" and
                message['Version'] == "latest"):
                response = 200
                body = {'status': 'Approved'}
            else:
                response = 403
                body = {'status': 'DECLINE'}


        self.send_response(response)
        self.send_header("Content-type", "application/json")
        self.end_headers()
        self.wfile.write(bytes(json.dumps(body), "utf8"))

if __name__ == "__main__":
    webServer = HTTPServer((hostName, serverPort), MyServer)
    print("Server started http://%s:%s" % (hostName, serverPort))

    try:
        webServer.serve_forever()
    except KeyboardInterrupt:
        pass

    webServer.server_close()
    print("Server stopped.")
