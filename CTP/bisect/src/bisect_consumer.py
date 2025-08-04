#!/usr/bin/env python3
"""
Bisect Consumer - Receives test requests from producer and executes tests
"""

import json
import os
import subprocess
import tempfile
import shutil
import logging
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.request import urlopen
import tarfile

# Configuration
CONFIG = {
    'listen_port': 8090,
    'work_dir': '/tmp/bisect_consumer',
    'cubrid_install_dir': '/tmp/cubrid_test'
}

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('BisectConsumer')

class TestRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/test':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                logger.info(f"Received test request for: {request['testPath']}")
                
                # Run test
                result = run_test(request)
                
                # Send response
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(result).encode())
                
            except Exception as e:
                logger.error(f"Error processing test request: {e}")
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                result = {'status': 'error', 'message': str(e)}
                self.wfile.write(json.dumps(result).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        logger.debug("%s - %s" % (self.address_string(), format % args))

def run_test(request):
    """Run a single test with the provided build"""
    work_dir = None
    try:
        # Create work directory
        work_dir = tempfile.mkdtemp(dir=CONFIG['work_dir'])
        logger.info(f"Working directory: {work_dir}")
        
        # Download build package if it's a URL
        build_package = request['buildPackage']
        if build_package.startswith('http'):
            local_package = os.path.join(work_dir, 'cubrid.tar.gz')
            logger.info(f"Downloading build from: {build_package}")
            response = urlopen(build_package)
            with open(local_package, 'wb') as f:
                f.write(response.read())
            build_package = local_package
        
        # Extract and install CUBRID
        install_dir = os.path.join(work_dir, 'cubrid')
        os.makedirs(install_dir)
        
        logger.info("Extracting CUBRID build...")
        with tarfile.open(build_package, 'r:gz') as tar:
            tar.extractall(install_dir)
        
        # Set environment for CUBRID
        env = os.environ.copy()
        env['CUBRID'] = install_dir
        env['CUBRID_DATABASES'] = os.path.join(install_dir, 'databases')
        env['PATH'] = os.path.join(install_dir, 'bin') + ':' + env.get('PATH', '')
        
        # Run the test
        test_dir = request['testDir']
        test_script = request['testScript']
        test_name = request['testName']
        result_file = f"{test_name}.result"
        
        logger.info(f"Running test: {test_script}")
        
        # Change to test directory and run test
        os.chdir(test_dir)
        
        # Remove old result file if exists
        if os.path.exists(result_file):
            os.remove(result_file)
        
        # Run test script
        result = subprocess.run(
            ['bash', test_script],
            env=env,
            capture_output=True,
            text=True
        )
        
        logger.debug(f"Test stdout: {result.stdout}")
        logger.debug(f"Test stderr: {result.stderr}")
        
        # Check result file
        if os.path.exists(result_file):
            with open(result_file, 'r') as f:
                result_content = f.read()
            
            if 'NOK' in result_content:
                logger.info(f"Test {test_name} FAILED")
                return {'status': 'fail', 'test': test_name}
            else:
                logger.info(f"Test {test_name} PASSED")
                return {'status': 'pass', 'test': test_name}
        else:
            logger.error(f"No result file generated for test {test_name}")
            return {'status': 'error', 'message': 'No result file generated'}
        
    except Exception as e:
        logger.error(f"Error running test: {e}")
        return {'status': 'error', 'message': str(e)}
    finally:
        # Cleanup
        if work_dir and os.path.exists(work_dir):
            try:
                shutil.rmtree(work_dir)
            except:
                pass

def main():
    """Main entry point"""
    # Create work directory if needed
    os.makedirs(CONFIG['work_dir'], exist_ok=True)
    
    # Start HTTP server
    server_address = ('', CONFIG['listen_port'])
    httpd = HTTPServer(server_address, TestRequestHandler)
    
    logger.info(f"Bisect Consumer listening on port {CONFIG['listen_port']}")
    logger.info(f"Work directory: {CONFIG['work_dir']}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        httpd.shutdown()

if __name__ == '__main__':
    main()
