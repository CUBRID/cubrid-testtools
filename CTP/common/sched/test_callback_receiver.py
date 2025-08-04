#!/usr/bin/env python3

"""
Test receiver for CTP bisect results
Supports both HTTP callback mode and file-based monitoring mode
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from datetime import datetime
import os
import time
import threading
import argparse
import glob

class BisectResultHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/bisect/result':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                result = json.loads(post_data.decode('utf-8'))
                print_http_result(result)
                
                # Send success response
                self.send_response(200)
                self.send_header('Content-type', 'text/plain')
                self.end_headers()
                self.wfile.write(b"OK")
                
            except Exception as e:
                print(f"Error processing HTTP callback: {e}")
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

def print_http_result(result):
    """Print HTTP callback result in a nice format"""
    print(f"\n{'='*80}")
    print(f"🔄 HTTP CALLBACK RECEIVED at {datetime.now()}")
    print(f"{'='*80}")
    print(f"Commit range: {result['commitFormer']} -> {result['commitLatter']}")
    print(f"Worker IP: {result['workerIp']}")
    print(f"Generated at: {result['generatedAt']}")
    print(f"\nTest Results:")
    print(f"{'='*80}")
    
    for i, test in enumerate(result['tests'], 1):
        print(f"\n[{i}] {test['name']}")
        print(f"    Status: {test['status']}")
        if test['status'] == 'found':
            print(f"    ✅ First bad commit: {test.get('firstBadCommit', 'N/A')}")
            print(f"    👤 Author: {test.get('author', 'N/A')}")
        elif test['status'] == 'error':
            print(f"    ❌ Error: {test.get('error', 'Unknown error')}")
        print(f"    ⏱️  Runtime: {test['runtimeMs']/1000:.1f} seconds")
    
    print(f"\n{'='*80}\n")

def parse_dat_file(filepath):
    """Parse a .dat file and return key-value pairs"""
    data = {}
    try:
        with open(filepath, 'r') as f:
            for line in f:
                line = line.strip()
                if '=' in line:
                    key, value = line.split('=', 1)
                    data[key] = value
    except Exception as e:
        print(f"Error reading {filepath}: {e}")
    return data

def monitor_file_results(result_dir):
    """Monitor file-based results"""
    print(f"📁 FILE MONITOR: Watching {result_dir} for bisect results...")
    processed_files = set()
    
    while True:
        try:
            # Look for new main result files
            main_files = glob.glob(os.path.join(result_dir, "bisect_main_*.dat"))
            
            for main_file in main_files:
                if main_file not in processed_files:
                    main_data = parse_dat_file(main_file)
                    if main_data:
                        main_id = main_data.get('MAIN_ID', 'unknown')
                        print_file_result(main_data, main_id, result_dir)
                        processed_files.add(main_file)
            
            time.sleep(2)  # Check every 2 seconds
            
        except KeyboardInterrupt:
            print("\n📁 FILE MONITOR: Stopped")
            break
        except Exception as e:
            print(f"📁 FILE MONITOR ERROR: {e}")
            time.sleep(5)

def print_file_result(main_data, main_id, result_dir):
    """Print file-based result in a nice format"""
    print(f"\n{'='*80}")
    print(f"📄 FILE RESULT DETECTED at {datetime.now()}")
    print(f"{'='*80}")
    print(f"Main ID: {main_id}")
    print(f"Commit range: {main_data.get('COMMIT_FORMER', 'N/A')} -> {main_data.get('COMMIT_LATTER', 'N/A')}")
    print(f"Build type: {main_data.get('BUILD_TYPE', 'N/A')}")
    print(f"Worker IP: {main_data.get('WORKER_IP', 'N/A')}")
    print(f"Origin IP: {main_data.get('ORIGIN_IP', 'N/A')}")
    print(f"Start time: {main_data.get('START_TIME', 'N/A')}")
    print(f"End time: {main_data.get('END_TIME', 'N/A')}")
    
    # Look for test result files
    test_files = glob.glob(os.path.join(result_dir, f"bisect_result_{main_id}_*.dat"))
    test_files.sort()
    
    if test_files:
        print(f"\nTest Results:")
        print(f"{'='*80}")
        
        for i, test_file in enumerate(test_files, 1):
            test_data = parse_dat_file(test_file)
            print(f"\n[{i}] {test_data.get('TEST_NAME', 'Unknown test')}")
            print(f"    Status: {test_data.get('STATUS', 'unknown')}")
            
            if test_data.get('STATUS') == 'found':
                print(f"    ✅ First bad commit: {test_data.get('FIRST_BAD_COMMIT', 'N/A')}")
                print(f"    👤 Author: {test_data.get('COMMIT_AUTHOR', 'N/A')}")
            elif test_data.get('STATUS') == 'error':
                print(f"    ❌ Error: {test_data.get('ERROR_MESSAGE', 'Unknown error')}")
            
            runtime_ms = test_data.get('RUNTIME_MS', '0')
            try:
                runtime_sec = int(runtime_ms) / 1000
                print(f"    ⏱️  Runtime: {runtime_sec:.1f} seconds")
            except:
                print(f"    ⏱️  Runtime: {runtime_ms} ms")
    else:
        print(f"\n⚠️  No test result files found for main_id {main_id}")
    
    print(f"\n{'='*80}\n")

def start_http_server(port=8080):
    """Start HTTP server for callback mode"""
    server_address = ('', port)
    httpd = HTTPServer(server_address, BisectResultHandler)
    print(f"🌐 HTTP SERVER: Listening on port {port}")
    print(f"🌐 HTTP SERVER: Endpoint http://localhost:{port}/bisect/result")
    print(f"🌐 HTTP SERVER: Press Ctrl+C to stop")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print(f"\n🌐 HTTP SERVER: Stopped")

def main():
    parser = argparse.ArgumentParser(description='CTP Bisect Result Test Receiver')
    parser.add_argument('--mode', choices=['http', 'file', 'both'], default='both',
                       help='Monitor mode: http (callbacks), file (file monitoring), or both (default: both)')
    parser.add_argument('--port', type=int, default=8080,
                       help='HTTP server port (default: 8080)')
    parser.add_argument('--result-dir', default='/tmp/cubrid-bisect-results',
                       help='Directory to monitor for result files (default: /tmp/cubrid-bisect-results)')
    
    args = parser.parse_args()
    
    print(f"\n{'='*80}")
    print(f"🧪 CTP BISECT RESULT TEST RECEIVER")
    print(f"{'='*80}")
    print(f"Mode: {args.mode}")
    if args.mode in ['http', 'both']:
        print(f"HTTP Port: {args.port}")
    if args.mode in ['file', 'both']:
        print(f"Result Directory: {args.result_dir}")
    print(f"{'='*80}\n")
    
    if args.mode == 'http':
        start_http_server(args.port)
    elif args.mode == 'file':
        if not os.path.exists(args.result_dir):
            print(f"⚠️  Creating result directory: {args.result_dir}")
            os.makedirs(args.result_dir, exist_ok=True)
        monitor_file_results(args.result_dir)
    elif args.mode == 'both':
        if not os.path.exists(args.result_dir):
            print(f"⚠️  Creating result directory: {args.result_dir}")
            os.makedirs(args.result_dir, exist_ok=True)
        
        # Start file monitor in a separate thread
        file_thread = threading.Thread(target=monitor_file_results, args=(args.result_dir,))
        file_thread.daemon = True
        file_thread.start()
        
        # Start HTTP server in main thread
        start_http_server(args.port)

if __name__ == '__main__':
    main()
