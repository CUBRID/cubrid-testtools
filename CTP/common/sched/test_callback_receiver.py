#!/usr/bin/env python3

"""
Simple HTTP server to receive bisect results from CTP
This is for testing the callback functionality
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from datetime import datetime

class BisectResultHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/bisect/result':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                result = json.loads(post_data.decode('utf-8'))
                
                print(f"\n{'='*80}")
                print(f"Received bisect results at {datetime.now()}")
                print(f"{'='*80}")
                print(f"Commit range: {result['commitFormer']} -> {result['commitLatter']}")
                print(f"Worker IP: {result['workerIp']}")
                print(f"Generated at: {result['generatedAt']}")
                print(f"\nTest Results:")
                print(f"{'='*80}")
                
                for test in result['tests']:
                    print(f"\nTest: {test['name']}")
                    print(f"Status: {test['status']}")
                    if test['status'] == 'found':
                        print(f"First bad commit: {test.get('firstBadCommit', 'N/A')}")
                        print(f"Author: {test.get('author', 'N/A')}")
                    elif test['status'] == 'error':
                        print(f"Error: {test.get('error', 'Unknown error')}")
                    print(f"Runtime: {test['runtimeMs']/1000:.1f} seconds")
                
                print(f"\n{'='*80}\n")
                
                # Send success response
                self.send_response(200)
                self.send_header('Content-type', 'text/plain')
                self.end_headers()
                self.wfile.write(b"OK")
                
            except Exception as e:
                print(f"Error processing result: {e}")
                self.send_response(400)
                self.send_header('Content-type', 'text/plain')
                self.end_headers()
                self.wfile.write(f"Error: {str(e)}".encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        # Suppress default logging
        pass

if __name__ == '__main__':
    server_address = ('', 8080)
    httpd = HTTPServer(server_address, BisectResultHandler)
    print(f"Bisect result receiver listening on port 8080...")
    print(f"Endpoint: http://localhost:8080/bisect/result")
    httpd.serve_forever()
