#!/usr/bin/env python3
"""
Simple callback receiver for testing bisect results
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from datetime import datetime

class BisectCallbackHandler(BaseHTTPRequestHandler):
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
                
                # Expected results based on the original script output
                expected_results = {
                    'shell/_06_issues/_12_2h/bug_bts_7583/cases/bug_bts_7583.sh': '437cc038cbfb9d98ca9777ffca22cb363dce9ce8',
                    'shell/_06_issues/_14_1h/bug_bts_13331/cases/bug_bts_13331.sh': '07afc09e091c6943e51866ef08f2d4b6cf13f16d',
                    'shell/_06_issues/_17_1h/cbrd_20759/hide_utls_cub_admin_unloaddb_password/cases/hide_utls_cub_admin_unloaddb_password.sh': 'e4c81278e30c41bce34e6472b33e15b60bac0269',
                    'shell/_28_features_844/issue_10709_statistic/issue_10709_statistic_3/cases/issue_10709_statistic_3.sh': '437cc038cbfb9d98ca9777ffca22cb363dce9ce8',
                    'shell/_37_elderberry/cbrd_23839/cases/cbrd_23839.sh': 'bc1dcd4e1d45b0333b71b3aab7fc7d30486e9b3f',
                    'shell/_10_plcsql/cbrd_25619/cases/cbrd_25619.sh': '7d4ab76f70aa69c781744cb4fbdf66150707eeb5',
                    'shell/_39_fig_cake/cbrd_24046/cases/cbrd_24046.sh': '437cc038cbfb9d98ca9777ffca22cb363dce9ce8',
                    'shell/_39_fig_cake/cbrd_25035/cases/cbrd_25035.sh': '604c595603d90b2ca8da4aa019e0d4964ace88b4',
                    'shell/_39_fig_cake/cbrd_25230/cases/cbrd_25230.sh': '437cc038cbfb9d98ca9777ffca22cb363dce9ce8',
                    'shell/_39_fig_cake/cbrd_25395/cte/cases/cte.sh': '604c595603d90b2ca8da4aa019e0d4964ace88b4'
                }
                
                for test in result['tests']:
                    print(f"\nTest: {test['name']}")
                    print(f"Status: {test['status']}")
                    if test['status'] == 'found':
                        print(f"First bad commit: {test.get('firstBadCommit', 'N/A')}")
                        print(f"Author: {test.get('author', 'N/A')}")
                        
                        # Check against expected result
                        if test['name'] in expected_results:
                            expected = expected_results[test['name']]
                            if test.get('firstBadCommit', '').startswith(expected[:8]):
                                print(f"✓ Matches expected result")
                            else:
                                print(f"✗ Expected: {expected}")
                    elif test['status'] == 'error':
                        print(f"Error: {test.get('error', 'Unknown error')}")
                    
                    runtime_seconds = test.get('runtimeMs', 0) / 1000
                    print(f"Runtime: {runtime_seconds:.1f} seconds")
                
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
    httpd = HTTPServer(server_address, BisectCallbackHandler)
    print(f"Bisect callback receiver listening on port 8080...")
    print(f"Endpoint: http://localhost:8080/bisect/result")
    print(f"\nThis receiver includes validation against expected results from the original script.")
    httpd.serve_forever()
