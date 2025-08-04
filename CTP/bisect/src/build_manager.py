#!/usr/bin/env python3
"""
Build Manager - Orchestrates distributed building of CUBRID commits
"""

import json
import os
import subprocess
import threading
import queue
import time
import hashlib
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.request import urlopen, Request
import pickle
import tempfile
import shutil

# Configuration
CONFIG = {
    'listen_port': 8091,
    'cache_dir': '/var/cache/cubrid-builds',
    'build_workers': 4,  # Number of concurrent build workers
    'max_cache_size_gb': 100,
    'build_timeout_minutes': 30,
    'predictor_enabled': True,
    'worker_endpoints': []  # List of remote worker endpoints (optional)
}

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('BuildManager')

class BuildRequest:
    def __init__(self, commit_hash, build_type='debug', priority=0, callback=None):
        self.commit_hash = commit_hash
        self.build_type = build_type
        self.priority = priority
        self.callback = callback
        self.status = 'pending'
        self.artifact_path = None
        self.error = None
        self.build_time = 0
        
    def __lt__(self, other):
        # Higher priority first
        return self.priority > other.priority

class BuildCache:
    def __init__(self, cache_dir, max_size_gb):
        self.cache_dir = cache_dir
        self.max_size_bytes = max_size_gb * 1024 * 1024 * 1024
        self.index_file = os.path.join(cache_dir, 'cache.index')
        self.lock = threading.Lock()
        self.index = self._load_index()
        
    def _load_index(self):
        if os.path.exists(self.index_file):
            try:
                with open(self.index_file, 'rb') as f:
                    return pickle.load(f)
            except:
                pass
        return {}
    
    def _save_index(self):
        os.makedirs(self.cache_dir, exist_ok=True)
        with open(self.index_file, 'wb') as f:
            pickle.dump(self.index, f)
    
    def get(self, commit_hash, build_type):
        key = f"{commit_hash}_{build_type}"
        with self.lock:
            if key in self.index:
                entry = self.index[key]
                if os.path.exists(entry['path']):
                    # Update access time
                    entry['last_access'] = time.time()
                    self._save_index()
                    logger.info(f"Cache hit for {commit_hash} ({build_type})")
                    return entry['path']
                else:
                    # Remove stale entry
                    del self.index[key]
                    self._save_index()
        return None
    
    def put(self, commit_hash, build_type, artifact_path):
        key = f"{commit_hash}_{build_type}"
        cache_path = os.path.join(self.cache_dir, f"{key}.tar.gz")
        
        with self.lock:
            # Copy to cache
            os.makedirs(self.cache_dir, exist_ok=True)
            shutil.copy2(artifact_path, cache_path)
            
            # Update index
            self.index[key] = {
                'path': cache_path,
                'size': os.path.getsize(cache_path),
                'created': time.time(),
                'last_access': time.time()
            }
            
            # Enforce cache size limit
            self._evict_if_needed()
            self._save_index()
            
            logger.info(f"Cached build for {commit_hash} ({build_type})")
            return cache_path
    
    def _evict_if_needed(self):
        total_size = sum(entry['size'] for entry in self.index.values())
        
        if total_size > self.max_size_bytes:
            # Sort by last access time (LRU)
            sorted_entries = sorted(
                self.index.items(),
                key=lambda x: x[1]['last_access']
            )
            
            while total_size > self.max_size_bytes and sorted_entries:
                key, entry = sorted_entries.pop(0)
                try:
                    os.remove(entry['path'])
                    total_size -= entry['size']
                    del self.index[key]
                    logger.info(f"Evicted {key} from cache")
                except:
                    pass

class BuildManager:
    def __init__(self, config):
        self.config = config
        self.build_queue = queue.PriorityQueue()
        self.active_builds = {}
        self.build_cache = BuildCache(
            config['cache_dir'],
            config['max_cache_size_gb']
        )
        self.executor = ThreadPoolExecutor(max_workers=config['build_workers'])
        self.running = True
        
        # Start worker threads
        for i in range(config['build_workers']):
            threading.Thread(
                target=self._build_worker,
                args=(i,),
                daemon=True
            ).start()
    
    def request_build(self, commit_hash, build_type='debug', priority=0, callback=None):
        """Request a build for a specific commit"""
        # Check cache first
        cached_path = self.build_cache.get(commit_hash, build_type)
        if cached_path:
            if callback:
                callback(commit_hash, 'cached', cached_path, None)
            return cached_path
        
        # Check if already building
        key = f"{commit_hash}_{build_type}"
        if key in self.active_builds:
            request = self.active_builds[key]
            if callback:
                # Add callback to existing request
                if request.callback:
                    orig_callback = request.callback
                    request.callback = lambda c, s, p, e: (
                        orig_callback(c, s, p, e),
                        callback(c, s, p, e)
                    )
                else:
                    request.callback = callback
            return None
        
        # Add to queue
        request = BuildRequest(commit_hash, build_type, priority, callback)
        self.active_builds[key] = request
        self.build_queue.put(request)
        logger.info(f"Queued build for {commit_hash} ({build_type}) priority={priority}")
        
        return None
    
    def _build_worker(self, worker_id):
        """Worker thread that processes build requests"""
        logger.info(f"Build worker {worker_id} started")
        
        while self.running:
            try:
                # Get next build request
                request = self.build_queue.get(timeout=1)
                key = f"{request.commit_hash}_{request.build_type}"
                
                logger.info(f"Worker {worker_id} building {request.commit_hash}")
                start_time = time.time()
                
                try:
                    # Perform the build
                    artifact_path = self._build_commit(
                        request.commit_hash,
                        request.build_type
                    )
                    
                    # Cache the result
                    cached_path = self.build_cache.put(
                        request.commit_hash,
                        request.build_type,
                        artifact_path
                    )
                    
                    request.status = 'completed'
                    request.artifact_path = cached_path
                    request.build_time = time.time() - start_time
                    
                    logger.info(f"Build completed for {request.commit_hash} in {request.build_time:.1f}s")
                    
                    # Notify callback
                    if request.callback:
                        request.callback(
                            request.commit_hash,
                            'completed',
                            cached_path,
                            None
                        )
                    
                except Exception as e:
                    request.status = 'failed'
                    request.error = str(e)
                    logger.error(f"Build failed for {request.commit_hash}: {e}")
                    
                    # Notify callback
                    if request.callback:
                        request.callback(
                            request.commit_hash,
                            'failed',
                            None,
                            str(e)
                        )
                
                finally:
                    # Remove from active builds
                    del self.active_builds[key]
                    
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"Worker {worker_id} error: {e}")
    
    def _build_commit(self, commit_hash, build_type):
        """Build CUBRID at a specific commit"""
        # This would be replaced with actual remote worker call if configured
        if self.config.get('worker_endpoints'):
            return self._remote_build(commit_hash, build_type)
        else:
            return self._local_build(commit_hash, build_type)
    
    def _local_build(self, commit_hash, build_type):
        """Build locally (placeholder - would use actual build logic)"""
        # Create temporary directory
        work_dir = tempfile.mkdtemp()
        
        try:
            # Simulate build process
            time.sleep(5)  # Simulate build time
            
            # Create dummy artifact
            artifact_path = os.path.join(work_dir, f"cubrid_{commit_hash}.tar.gz")
            with open(artifact_path, 'w') as f:
                f.write(f"Dummy build for {commit_hash}")
            
            return artifact_path
            
        except Exception:
            shutil.rmtree(work_dir, ignore_errors=True)
            raise
    
    def _remote_build(self, commit_hash, build_type):
        """Delegate build to remote worker"""
        # Round-robin worker selection
        worker_url = self.config['worker_endpoints'][
            hash(commit_hash) % len(self.config['worker_endpoints'])
        ]
        
        request_data = {
            'commit_hash': commit_hash,
            'build_type': build_type
        }
        
        req = Request(
            f"{worker_url}/build",
            data=json.dumps(request_data).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        
        response = urlopen(req)
        result = json.loads(response.read().decode('utf-8'))
        
        if result['status'] == 'success':
            return result['artifact_path']
        else:
            raise Exception(result.get('error', 'Unknown error'))
    
    def predict_next_commits(self, current_commit, commit_range):
        """Predict which commits bisect might test next"""
        if not self.config.get('predictor_enabled'):
            return []
        
        # Simple prediction: pre-build middle commits in remaining range
        # This could be made more sophisticated
        predicted = []
        
        # Get commit list between range
        try:
            result = subprocess.run(
                ['git', 'rev-list', f"{commit_range[0]}..{commit_range[1]}"],
                capture_output=True,
                text=True,
                check=True
            )
            commits = result.stdout.strip().split('\n')
            
            # Pre-build middle commits (likely bisect targets)
            if len(commits) > 2:
                mid = len(commits) // 2
                predicted.extend([
                    commits[mid],
                    commits[mid // 2],
                    commits[mid + mid // 2]
                ])
            
        except:
            pass
        
        return predicted[:3]  # Limit predictions

# Global build manager instance
build_manager = None

class BuildRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/request_build':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                commit_hash = request['commit_hash']
                build_type = request.get('build_type', 'debug')
                priority = request.get('priority', 0)
                
                # Request build
                artifact_path = build_manager.request_build(
                    commit_hash,
                    build_type,
                    priority
                )
                
                if artifact_path:
                    # Already cached
                    response = {
                        'status': 'cached',
                        'artifact_path': artifact_path
                    }
                else:
                    # Building
                    response = {
                        'status': 'building'
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
        
        elif self.path == '/build_status':
            # Check build status
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                request = json.loads(post_data.decode('utf-8'))
                key = f"{request['commit_hash']}_{request.get('build_type', 'debug')}"
                
                if key in build_manager.active_builds:
                    build = build_manager.active_builds[key]
                    response = {
                        'status': build.status,
                        'artifact_path': build.artifact_path,
                        'error': build.error
                    }
                else:
                    # Check cache
                    cached = build_manager.build_cache.get(
                        request['commit_hash'],
                        request.get('build_type', 'debug')
                    )
                    if cached:
                        response = {
                            'status': 'completed',
                            'artifact_path': cached
                        }
                    else:
                        response = {
                            'status': 'not_found'
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
        
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        logger.debug("%s - %s" % (self.address_string(), format % args))

def main():
    global build_manager
    
    # Initialize build manager
    build_manager = BuildManager(CONFIG)
    
    # Start HTTP server
    server_address = ('', CONFIG['listen_port'])
    httpd = HTTPServer(server_address, BuildRequestHandler)
    
    logger.info(f"Build Manager listening on port {CONFIG['listen_port']}")
    logger.info(f"Cache directory: {CONFIG['cache_dir']}")
    logger.info(f"Build workers: {CONFIG['build_workers']}")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        build_manager.running = False
        httpd.shutdown()

if __name__ == '__main__':
    main()
