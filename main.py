import sys
import os
import random
import collections
import urllib.parse
import io
import json
from functools import lru_cache
from pathlib import Path
from urllib.parse import unquote

# --- 依赖库检查 ---
try:
    from smb.SMBConnection import SMBConnection
except ImportError:
    SMBConnection = None
try:
    from webdav3.client import Client as WebDavClientLib
except ImportError:
    WebDavClientLib = None

from PySide6.QtGui import QGuiApplication, QImage
from PySide6.QtQml import QQmlApplicationEngine
from PySide6.QtQuick import QQuickImageProvider 
from PySide6.QtQuickControls2 import QQuickStyle
from PySide6.QtCore import QObject, Slot, Signal, QUrl, QThread, Property, Qt

CACHE_SIZE = 128 
IMAGE_EXTENSIONS = {'.jpg', '.jpeg', '.png', '.bmp', '.webp', '.gif'}
CONFIG_FILE = "visual_flow_config.json"

# ================= 数据源适配器 =================
class BaseClient:
    def connect(self): pass
    def close(self): pass
    def scan_images(self): yield from []
    def get_image_data(self, path) -> bytes: return b""

class LocalFileClient(BaseClient):
    def __init__(self, root_path):
        self.root = root_path
    def scan_images(self):
        for root, dirs, files in os.walk(self.root):
            for file in files:
                if Path(file).suffix.lower() in IMAGE_EXTENSIONS:
                    yield str(Path(root) / file).replace("\\", "/")
    def get_image_data(self, path):
        return None  # Local files loaded directly by QImage

class SmbClient(BaseClient):
    def __init__(self, server_ip, share_name, username, password, folder_path="/"):
        if not SMBConnection: raise ImportError("Missing pysmb library.")
        self.conn = SMBConnection(username, password, "PyClient", server_ip, use_ntlm_v2=True)
        self.ip = server_ip; self.share = share_name; self.root_folder = folder_path 
    def connect(self): self.conn.connect(self.ip, 139)
    def close(self): self.conn.close()
    def scan_images(self): yield from self._walk(self.root_folder)
    def _walk(self, path):
        try:
            files = self.conn.listPath(self.share, path)
            for f in files:
                if f.filename in ['.', '..']: continue
                full_path = os.path.join(path, f.filename).replace('\\', '/')
                if f.isDirectory: yield from self._walk(full_path)
                elif Path(f.filename).suffix.lower() in IMAGE_EXTENSIONS: yield full_path
        except Exception as e: print(f"SMB Error: {e}")
    def get_image_data(self, path):
        mem = io.BytesIO()
        try: self.conn.retrieveFile(self.share, path, mem); return mem.getvalue()
        except: return None

class WebDavClient(BaseClient):
    def __init__(self, hostname, login, password, root_path="/"):
        if not WebDavClientLib: raise ImportError("Missing webdavclient3 library.")
        options = {'webdav_hostname': hostname, 'webdav_login': login, 'webdav_password': password}
        self.client = WebDavClientLib(options); self.root = root_path
    def scan_images(self): yield from self._walk(self.root)
    def _walk(self, path):
        try:
            files = self.client.list(path, get_info=True)
            for f in files[1:]: 
                if f['isdir']: yield from self._walk(f['path'])
                elif Path(f['path']).suffix.lower() in IMAGE_EXTENSIONS: yield f['path']
        except Exception as e: print(f"WebDAV Error: {e}")
    def get_image_data(self, path):
        mem = io.BytesIO()
        try: self.client.download_from(buff=mem, remote_path=path); return mem.getvalue()
        except: return None

# ================= 复合客户端 (管理多个源) =================
class CompositeClient:
    def __init__(self):
        self.clients = [] # List of BaseClient
    
    def add_client(self, client):
        self.clients.append(client)

    def connect_all(self):
        for c in self.clients:
            try: c.connect()
            except Exception as e: print(f"Client Connect Error: {e}")

    def close_all(self):
        for c in self.clients:
            try: c.close()
            except: pass
    
    def get_client(self, index):
        if 0 <= index < len(self.clients):
            return self.clients[index]
        return None

# ================= Qt 核心组件 =================

class LruImageProvider(QQuickImageProvider):
    def __init__(self):
        super().__init__(QQuickImageProvider.ImageType.Image)
        self.composite_client = None

    def set_composite_client(self, client):
        self.composite_client = client
        self._load_image_cached.cache_clear() 

    def requestImage(self, id, size, requestedSize):
        # ID 格式: "client_index|encoded_path"
        # 例如: "0|C%3A%2FPhotos%2Fimg.jpg"
        try:
            decoded_id = unquote(id)
            idx_str, encoded_path = decoded_id.split('|', 1)
            client_idx = int(idx_str)
            file_path = urllib.parse.unquote(encoded_path)
            
            img = self._load_image_cached(client_idx, file_path)
            size.setWidth(img.width())
            size.setHeight(img.height())
            return img
        except Exception as e:
            print(f"Request Image Error: {id} -> {e}")
            return QImage()

    @lru_cache(maxsize=CACHE_SIZE)
    def _load_image_cached(self, client_idx, path):
        if not self.composite_client: return QImage()
        
        client = self.composite_client.get_client(client_idx)
        if not client: return QImage()

        image_data = client.get_image_data(path)
        image = QImage()
        
        if image_data: 
            image.loadFromData(image_data)
        else:
            # 如果 retrieve 返回 None，说明是本地文件或不可读，尝试直接加载路径
            if isinstance(client, LocalFileClient): 
                image = QImage(path)
        
        if image.isNull():
            # 占位图
            image = QImage(100, 100, QImage.Format_ARGB32)
            image.fill(Qt.black)
            
        return image

class ScannerWorker(QThread):
    # 返回列表结构: [(client_index, file_path), ...]
    images_found = Signal(list)
    
    def __init__(self, composite_client):
        super().__init__()
        self.composite_client = composite_client
        self.running = True

    def run(self):
        self.composite_client.connect_all()
        
        # 遍历所有客户端
        for idx, client in enumerate(self.composite_client.clients):
            if not self.running: break
            try:
                batch = []
                for full_path in client.scan_images():
                    if not self.running: break
                    # 将 客户端索引 和 路径 打包
                    batch.append((idx, full_path))
                    if len(batch) >= 50: 
                        self.images_found.emit(batch)
                        batch = []
                if batch: self.images_found.emit(batch)
            except Exception as e:
                print(f"Scanner Error on client {idx}: {e}")

    def stop(self): 
        self.running = False

class ImageProvider(QObject):
    countChanged = Signal(int)
    sessionStarted = Signal(bool, str) 

    def __init__(self, lru_provider):
        super().__init__()
        self.lru_provider = lru_provider
        self.composite_client = None
        self.scanner = None
        self._all_images = [] # 存储 tuples: (client_idx, path)
        self._play_queue = collections.deque()

    @Slot(str)
    def initSource(self, json_config):
        config = json.loads(json_config)
        # 支持新版列表配置，也兼容旧版单对象配置
        source_configs = []
        raw_cfg = config.get("sourceConfig", [])
        
        if isinstance(raw_cfg, dict):
            source_configs.append(raw_cfg)
        elif isinstance(raw_cfg, list):
            source_configs = raw_cfg
        
        self.close()
        self._all_images.clear()
        self._play_queue.clear()
        self.countChanged.emit(0)

        self.composite_client = CompositeClient()

        try:
            for cfg in source_configs:
                c_type = cfg.get("type", "LOCAL")
                if c_type == "LOCAL":
                    path = cfg.get("path", "")
                    if os.path.exists(path):
                        self.composite_client.add_client(LocalFileClient(path))
                elif c_type == "SMB":
                    self.composite_client.add_client(SmbClient(
                        cfg["ip"], cfg["share"], cfg["user"], cfg["password"], cfg.get("path", "/")
                    ))
                elif c_type == "WEBDAV":
                    self.composite_client.add_client(WebDavClient(
                        cfg["host"], cfg["user"], cfg["password"], cfg.get("path", "/")
                    ))
            
            if not self.composite_client.clients:
                raise ValueError("No valid sources configured")

            self.lru_provider.set_composite_client(self.composite_client)
            self.scanner = ScannerWorker(self.composite_client)
            self.scanner.images_found.connect(self.on_images_found)
            self.scanner.start()
            self.sessionStarted.emit(True, "Success")
            
        except Exception as e:
            import traceback
            traceback.print_exc()
            self.sessionStarted.emit(False, str(e))

    @Slot(list)
    def on_images_found(self, new_images):
        # new_images is list of (idx, path)
        random.shuffle(new_images)
        self._all_images.extend(new_images)
        self._play_queue.extend(new_images)
        self.countChanged.emit(len(self._all_images))

    @Slot(result=str)
    def getNextImage(self):
        if not self._play_queue:
            if not self._all_images: return ""
            reshuffled = self._all_images[:]
            random.shuffle(reshuffled)
            self._play_queue.extend(reshuffled)
        
        # item is (client_index, path_string)
        client_idx, path = self._play_queue.popleft()
        
        # 构造给 QML 的 URL: image://lru/INDEX|URL_ENCODED_PATH
        encoded_path = urllib.parse.quote(path)
        return f"image://lru/{client_idx}|{encoded_path}"

    @Property(int, notify=countChanged)
    def totalImages(self): return len(self._all_images)
    
    @Slot(str)
    def saveSettings(self, json_str):
        try:
            with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
                f.write(json_str)
        except Exception as e:
            print(f"Save Config Error: {e}")

    @Slot(result=str)
    def loadSettings(self):
        if os.path.exists(CONFIG_FILE):
            try:
                with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
                    return f.read()
            except Exception as e:
                print(f"Load Config Error: {e}")
        return "{}" 

    def close(self):
        if self.scanner: self.scanner.stop(); self.scanner.wait(); self.scanner = None
        if self.composite_client: self.composite_client.close_all(); self.composite_client = None

if __name__ == "__main__":
    app = QGuiApplication(sys.argv)
    QQuickStyle.setStyle("Basic")
    engine = QQmlApplicationEngine()

    lru_provider = LruImageProvider()
    engine.addImageProvider("lru", lru_provider)

    provider = ImageProvider(lru_provider)
    engine.rootContext().setContextProperty("Backend", provider)

    qml_file = Path(__file__).parent / "Main.qml"
    engine.load(qml_file)
    if not engine.rootObjects(): sys.exit(-1)
    
    ret = app.exec()
    provider.close()
    sys.exit(ret)