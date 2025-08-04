#!/usr/bin/env python3
"""
Build Worker - Dedicated worker for building CUBRID commits
Can run on multiple machines for horizontal scaling
"""

import json
import os
import subprocess
import tempfile
import shutil
import logging
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
import tarfile
import threading
import queue

# Configuration
CONFIG = {
    'listen_port': 8092,
    'cubrid_src_dir': '/home/cubrid/cubrid',
    'build_dir_prefix': 'build_x86_64',
    'work_dir': '/tmp/build_worker',
    'max_concurrent_builds': 2,
    'git_update_interval': 300,  # Update git repo every 5 minutes
}

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('BuildWorker')

class BuildTask:
    def __init__(self, commit_hash, build_type):
        self.commit_hash = commit_hash
        self.build_type = build_type
        self.status = 'pending'
        self.artifact_path = None
        self.error = None
        self.start_time = None
        self.end_time = None

class BuildWorker:
    def __init__(self, config):
        self.config = config
        self.build_queue = queue.Queue()
        self.active_builds = {}
        self.lock = threading.Lock()
        self.last_git_update = 0
        
        # Start worker threads
        for i in range(config['max_concurrent_builds']):
            thread = threading.Thread(
                target=self._build_thread,
                args=(i,),
                daemon=True
            )
            thread.start()
    
    def submit_build(self, commit_hash, build_type):
        """Submit a build task"""
        task = BuildTask(commit_hash, build_type)
        task_id = f"{commit_hash}_{build_type}"
        
        with self.lock:
            if task_id in self.active_builds:
                # Already building
                return self.active_builds[task_id]
            
            self.active_builds[task_id] = task
            self.build_queue.put(task)
        
        logger.info(f"Build task submitted: {task_id}")
        return task
    
    def get_build_status(self, commit_hash, build_type):
        """Get status of a build task"""
        task_id = f"{commit_hash}_{build_type}"
        
        with self.lock:
            if task_id in self.active_builds:
                return self.active_builds[task_id]
        
        return None
    
    def _build_thread(self, worker_id):
        """Worker thread that processes builds"""
        logger.info(f"Build thread {worker_id} started")
        
        while True:
            try:
                task = self.build_queue.get()
                task_id = f"{task.commit_hash}_{task.build_type}"
                
                logger.info(f"Thread {worker_id} building {task_id}")
                task.status = 'building'
                task.start_time = time.time()
                
                try:
                    # Update git repository if needed
                    self._update_git_repo()
                    
                    # Build the commit
                    artifact_path = self._build_commit(
                        task.commit_hash,
                        task.build_type
                    )
                    
                    task.status = 'completed'
                    task.artifact_path = artifact_path
                    task.end_time = time.time()
                    
                    logger.info(
                        f"Build completed: {task_id} in "
                        f"{task.end_time - task.start_time:.1f}s"
                    )
                    
                except Exception as e:
                    task.status = 'failed'
                    task.error = str(e)
                    task.end_time = time.time()
                    logger.error(f"Build failed: {task_id} - {e}")
                
            except Exception as e:
                logger.error(f"Thread {worker_id} error: {e}")
    
    def _update_git_repo(self):
        """Update git repository if needed"""
        now = time.time()
        if now - self.last_git_update > self.config['git_update_interval']:
            try:
                logger.info("Updating git repository...")
                os.chdir(self.config['cubrid_src_dir'])
                
                # Fetch latest changes
                subprocess.run(['git', 'fetch', '--all'], check=True)
                
                self.last_git_update = now
                logger.info("Git repository updated")
            except Exception as e:
                logger.error(f"Failed to update git: {e}")
    
    def _build_commit(self, commit_hash, build_type):
        """Build CUBRID at specific commit"""
        work_dir = tempfile.mkdtemp(dir=self.config['work_dir'])
        
        try:
            os.chdir(self.config['cubrid_src_dir'])
            
            # Checkout the commit
            logger.info(f"Checking out {commit_hash}")
            subprocess.run(
                ['git', 'checkout', '-f', commit_hash],
                check=True,
                capture_output=True
            )
            
            # Reset submodules
            subprocess.run(
                ['git', 'submodule', 'foreach', 'git', 'reset', '--hard', 'HEAD'],
                check=True,
                capture_output=True
            )
            subprocess.run(
                ['git', 'submodule', 'update'],
                check=True,
                capture_output=True
            )
            
            # Clean build directory
            build_dir_name = f"{self.config['build_dir_prefix']}_{build_type}"
            build_dir = os.path.join(self.config['cubrid_src_dir'], build_dir_name)
            if os.path.exists(build_dir):
                shutil.rmtree(build_dir)
            
            # Remove cubridmanager if it exists (compilation issues)
            cm_dir = os.path.join(self.config['cubrid_src_dir'], 'cubridmanager')
            if os.path.exists(cm_dir):
                shutil.rmtree(cm_dir)
            
            # Build
            build_args = f"-g ninja -m {build_type} build"
            logger.info(f"Building with: ./build.sh {build_args}")
            
            result = subprocess.run(
                ['./build.sh'] + build_args.split(),
                check=True,
                capture_output=True,
                text=True
            )
            
            # Create artifact
            artifact_name = f"cubrid_{commit_hash}_{build_type}.tar.gz"
            artifact_path = os.path.join(work_dir, artifact_name)
            
            logger.info(f"Creating artifact: {artifact_name}")
            with tarfile.open(artifact_path, 'w:gz') as tar:
                tar.add(build_dir, arcname='.')
            
            # Move to permanent location
            final_path = os.path.join(
                self.config['work_dir'],
                'artifacts',
                artifact_name
            )
            os.makedirs(os.path.dirname(final_path), exist_ok=True)
            shutil.move(artifact_path, final_path)
            
            return final_path
            
        except subprocess.CalledProcessError as e:
            logger.error(f"Build command failed: {e.stdout}\n{e.stderr}")
            raise Exception(f"Build failed: {e.stderr}")
        except Exception as e:
            logger.error(f"Build error: {e}")
            raise
        finally:
            # Cleanup
            shutil.rmtree(work_dir, ignore_errors=True)
            
            # Reset git to clean state
            try:
                os.chdir(self.config['cubrid_src_dir'])
                subprocess.run(['git', 'reset', '--hard', 'HEAD'], capture_output=True)
                subprocess.run(['git', 'clean', '-fd'], capture_output=True)
            except:
                pass

# Global worker instance
worker = None

class BuildWorkerHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/build':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                commit_hash = request['commit_hash']
                build_type = request.get('build_type', 'debug')
                
                # Submit build
                task = worker.submit_build(commit_hash, build_type)
                
                # Wait for completion (with timeout)
                timeout = 30 * 60  # 30 minutes
                start_time = time.time()
                
                while task.status in ['pending', 'building']:
                    if time.time() - start_time > timeout:
                        raise Exception("Build timeout")
                    time.sleep(1)
                
                if task.status == 'completed':
                    response = {
                        'status': 'success',
                        'artifact_path': task.artifact_path,
                        'build_time': task.end_time - task.start_time
                    }
                else:
                    response = {
                        'status': 'failed',
                        'error': task.error
                    }
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
                
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {'status': 'error', 'message': str(e)}
                self.wfile.write(json.dumps(response).encode())
        
        elif self.path == '/status':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                task = worker.get_build_status(
                    request['commit_hash'],
                    request.get('build_type', 'debug')
                )
                
                if task:
                    response = {
                        'status': task.status,
                        'artifact_path': task.artifact_path,
                        'error': task.error
                    }
                else:
                    response = {'status': 'not_found'}
                
                self.send_response(200)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
                
            except Exception as e:
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = {'status': 'error', 'message': str(e)}
                self.wfile.write(json.dumps(response).encode())
        
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_GET(self):
        if self.path == '/health':
            # Health check endpoint
            queue_size = worker.build_queue.qsize()
            active_count = len([t for t in worker.active_builds.values() 
                              if t.status == 'building'])
            
            response = {
                'status': 'healthy',
                'queue_size': queue_size,
                'active_builds': active_count,
                'max_concurrent': CONFIG['max_concurrent_builds']
            }
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        logger.debug("%s - %s" % (self.address_string(), format % args))

def main():
    global worker
    
    # Create work directories
    os.makedirs(CONFIG['work_dir'], exist_ok=True)
    os.makedirs(os.path.join(CONFIG['work_dir'], 'artifacts'), exist_ok=True)
    
    # Initialize worker
    worker = BuildWorker(CONFIG)
    
    # Start HTTP server
    server_address = ('', CONFIG['listen_port'])
    httpd = HTTPServer(server_address, BuildWorkerHandler)
    
    logger.info(f"Build Worker listening on port {CONFIG['listen_port']}")
    logger.info(f"CUBRID source: {CONFIG['cubrid_src_dir']}")
    logger.info(f"Max concurrent builds: {CONFIG['max_concurrent_builds']}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        httpd.shutdown()

if __name__ == '__main__':
    main()
