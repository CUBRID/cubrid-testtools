#!/usr/bin/env python3
"""
Bisect Producer - Receives bisect requests and coordinates the bisect process
"""

import json
import os
import subprocess
import tempfile
import time
import threading
import logging
import shutil
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.request import urlopen, Request
from datetime import datetime

# Configuration
CONFIG = {
    'listen_port': 8089,
    'cubrid_src_dir': '/home/cubrid/cubrid',
    'shell_tc_dir': '/home/cubrid/cubrid-testcases-private-ex',
    'build_arg': '-g ninja -m debug build',
    'build_dir': 'build_x86_64_debug',
    'work_dir': '/tmp/bisect_work',
    'consumer_port': 8090  # Port on consumer for receiving test requests
}

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('BisectProducer')

class BisectRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/bisect':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                logger.info(f"Received bisect request: {request['commitFormer']} -> {request['commitLatter']}")
                
                # Start bisect in a separate thread
                thread = threading.Thread(
                    target=process_bisect_request,
                    args=(request,)
                )
                thread.start()
                
                # Send immediate response
                self.send_response(202)  # Accepted
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {'status': 'accepted', 'message': 'Bisect request received and processing'}
                self.wfile.write(json.dumps(response).encode())
                
            except Exception as e:
                logger.error(f"Error processing request: {e}")
                self.send_response(400)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {'status': 'error', 'message': str(e)}
                self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        # Use our logger instead of default
        logger.debug("%s - %s" % (self.address_string(), format % args))

def process_bisect_request(request):
    """Process a bisect request by running git bisect for each test"""
    commit_former = request['commitFormer']
    commit_latter = request['commitLatter']
    build_type = request.get('buildType', 'debug')
    tests = request['tests']
    callback_url = request['callbackUrl']
    worker_ip = request.get('workerIp', 'localhost')
    
    results = []
    
    for test in tests:
        logger.info(f"Starting bisect for test: {test}")
        result = run_bisect_for_test(
            commit_former, commit_latter, build_type, test, worker_ip
        )
        results.append(result)
    
    # Send results back to callback URL
    send_results_callback(request, results)

def run_bisect_for_test(commit_former, commit_latter, build_type, test_path, worker_ip):
    """Run git bisect to find the first bad commit for a test"""
    start_time = time.time()
    
    try:
        # Create working directory
        work_dir = tempfile.mkdtemp(dir=CONFIG['work_dir'])
        logger.info(f"Working directory: {work_dir}")
        
        # Create judge script
        judge_script = create_judge_script(work_dir, test_path, build_type, worker_ip)
        
        # Change to source directory
        os.chdir(CONFIG['cubrid_src_dir'])
        
        # Reset any previous bisect
        subprocess.run(['git', 'bisect', 'reset'], capture_output=True)
        subprocess.run(['git', 'submodule', 'foreach', 'git', 'reset', '--hard', 'HEAD'], 
                      capture_output=True)
        subprocess.run(['git', 'submodule', 'update'], capture_output=True)
        
        # Start bisect
        logger.info(f"Starting bisect: {commit_former} -> {commit_latter}")
        subprocess.run(['git', 'bisect', 'start', commit_latter, commit_former], 
                      capture_output=True, check=True)
        
        # Run bisect with judge script
        result = subprocess.run(
            ['git', 'bisect', 'run', judge_script],
            capture_output=True, text=True
        )
        
        # Parse output
        bisect_output = result.stdout
        logger.debug(f"Bisect output: {bisect_output}")
        
        # Check if we found the first bad commit
        if 'is the first bad commit' in bisect_output:
            # Extract commit hash
            import re
            commit_pattern = re.compile(r'([0-9a-f]{40}) is the first bad commit')
            match = commit_pattern.search(bisect_output)
            
            if match:
                first_bad_commit = match.group(1)
                
                # Get commit author
                author_result = subprocess.run(
                    ['git', 'log', '-1', '--format=%an <%ae>', first_bad_commit],
                    capture_output=True, text=True
                )
                author = author_result.stdout.strip()
                
                return {
                    'name': test_path,
                    'status': 'found',
                    'firstBadCommit': first_bad_commit,
                    'author': author,
                    'runtimeMs': int((time.time() - start_time) * 1000)
                }
        
        # If we didn't find a bad commit
        return {
            'name': test_path,
            'status': 'error',
            'error': 'No bad commit found in range',
            'runtimeMs': int((time.time() - start_time) * 1000)
        }
        
    except Exception as e:
        logger.error(f"Error during bisect: {e}")
        return {
            'name': test_path,
            'status': 'error',
            'error': str(e),
            'runtimeMs': int((time.time() - start_time) * 1000)
        }
    finally:
        # Cleanup
        try:
            subprocess.run(['git', 'bisect', 'reset'], capture_output=True)
            if 'work_dir' in locals() and os.path.exists(work_dir):
                shutil.rmtree(work_dir)
        except:
            pass

def create_judge_script(work_dir, test_path, build_type, worker_ip):
    """Create a judge script that builds and sends test to consumer"""
    script_path = os.path.join(work_dir, 'judge.sh')
    
    # Extract test info
    tc_dir = CONFIG['shell_tc_dir'] + '/' + os.path.dirname(test_path)
    tc_script = os.path.basename(test_path)
    tc_name = tc_script.replace('.sh', '')
    
    script_content = f'''#!/bin/bash
set -e  # exit immediately on error

echo "Building CUBRID at commit $(git rev-parse HEAD)"
cd {CONFIG['cubrid_src_dir']}

# Reset and update submodules
git submodule foreach git reset --hard HEAD
git submodule update

# Clean build
rm -rf cubridmanager/*  # temporary: cubridmanager fails on Rocky 8
rm -rf {CONFIG['build_dir']}

# Build
./build.sh {CONFIG['build_arg']}

# Create build package
BUILD_PACKAGE="{work_dir}/cubrid_$(git rev-parse --short HEAD).tar.gz"
cd {CONFIG['build_dir']}
tar czf "$BUILD_PACKAGE" .

# Send test request to consumer
echo "Sending test request to consumer at {worker_ip}:{CONFIG['consumer_port']}"

# Create test request JSON
cat > {work_dir}/test_request.json << EOF
{{
  "buildPackage": "$BUILD_PACKAGE",
  "testPath": "{test_path}",
  "testDir": "{tc_dir}",
  "testScript": "{tc_script}",
  "testName": "{tc_name}"
}}
EOF

# Send request to consumer and get result
RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \\
    -d @{work_dir}/test_request.json \\
    http://{worker_ip}:{CONFIG['consumer_port']}/test)

echo "Consumer response: $RESPONSE"

# Parse result
if echo "$RESPONSE" | grep -q '"status":"fail"'; then
    exit 1  # Test failed (bad commit)
elif echo "$RESPONSE" | grep -q '"status":"pass"'; then
    exit 0  # Test passed (good commit)
else
    echo "Error: Invalid response from consumer"
    exit 128  # Abort bisect
fi
'''
    
    with open(script_path, 'w') as f:
        f.write(script_content)
    
    os.chmod(script_path, 0o755)
    return script_path

def send_results_callback(request, results):
    """Send bisect results back to the callback URL"""
    try:
        response_data = {
            'commitFormer': request['commitFormer'],
            'commitLatter': request['commitLatter'],
            'workerIp': request.get('workerIp', 'localhost'),
            'generatedAt': datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'),
            'tests': results
        }
        
        json_data = json.dumps(response_data, indent=2)
        logger.info(f"Sending results to {request['callbackUrl']}")
        logger.debug(f"Results: {json_data}")
        
        req = Request(
            request['callbackUrl'],
            data=json_data.encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        
        response = urlopen(req)
        logger.info(f"Callback response: {response.status}")
        
    except Exception as e:
        logger.error(f"Failed to send callback: {e}")

def main():
    """Main entry point"""
    # Create work directory if needed
    os.makedirs(CONFIG['work_dir'], exist_ok=True)
    
    # Start HTTP server
    server_address = ('', CONFIG['listen_port'])
    httpd = HTTPServer(server_address, BisectRequestHandler)
    
    logger.info(f"Bisect Producer listening on port {CONFIG['listen_port']}")
    logger.info(f"CUBRID source: {CONFIG['cubrid_src_dir']}")
    logger.info(f"Shell TC dir: {CONFIG['shell_tc_dir']}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        httpd.shutdown()

if __name__ == '__main__':
    main()
