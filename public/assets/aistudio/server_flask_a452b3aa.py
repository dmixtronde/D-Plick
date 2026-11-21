import os, sys
import socket
from flask import Flask, render_template
from flask_socketio import SocketIO

# Kéo các biến dùng chung và tín hiệu từ config sang
import config
from config import current_state, signals, app_config

import ctypes

import ctypes

class _POINT(ctypes.Structure):
    _fields_ = [("x", ctypes.c_long), ("y", ctypes.c_long)]

_user32 = ctypes.windll.user32
_user32.SetWindowPos.argtypes = [
    ctypes.c_void_p, ctypes.c_void_p,
    ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int,
    ctypes.c_uint
]


def get_base_path(relative_path):
    """Đảm bảo tìm đúng file khi chạy code thường lẫn khi đã build Nuitka (.exe)"""
    # Lấy chính xác thư mục chứa file DPlick.exe đang chạy
    base_dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    return os.path.join(base_dir, relative_path)


# ==========================================
# KHỞI TẠO FLASK & SOCKET.IO
# ==========================================
flask_app = Flask(
    __name__,
    template_folder=get_base_path('templates'),
    static_folder=get_base_path('static')
)
flask_app.config['SECRET_KEY'] = 'dplick_secret'
socketio = SocketIO(flask_app, cors_allowed_origins="*", async_mode='threading')

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('10.255.255.255', 1))
        IP = s.getsockname()[0]
    except: 
        IP = '127.0.0.1'
    finally: 
        s.close()
    return IP

# ==========================================
# ROUTE QUẢN LÝ GIAO DIỆN WEB
# ==========================================
@flask_app.route('/scanner')
def scanner_view():
    # Lấy trực tiếp từ config.SERVER_PASSWORD
    return render_template('scanner.html', req_pass=config.SERVER_PASSWORD, question=current_state['question'])


@flask_app.route('/')
@flask_app.errorhandler(404)
def index_fallback(e=None):
    html_welcome = """
    <!DOCTYPE html>
    <html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>D-Plick Chào Mừng</title>
        <style>
            body { background: #f8fafc; font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
            .card { background: white; padding: 30px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); text-align: center; max-width: 350px; border-top: 5px solid #0284c7; }
            h2 { color: #0284c7; margin-bottom: 10px; font-size: 22px; }
            p { color: #475569; font-size: 15px; line-height: 1.5; }
            a { display: inline-block; background: #16a34a; color: white; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: bold; margin-top: 15px; box-shadow: 0 4px 6px rgba(22, 163, 74, 0.2); transition: 0.2s; }
            a:active { transform: scale(0.95); }
        </style>
    </head>
    <body>
        <div class="card">
            <div style="font-size: 55px; margin-bottom: 10px;">👋</div>
            <h2>Chào mừng đến D-Plick!</h2>
            <p>Bạn đã kết nối thành công, nhưng đường dẫn chưa đưa bạn vào đúng phòng quét thẻ.</p>
            <p>Hãy bấm vào nút bên dưới để vào thẳng giao diện quét bài:</p>
            <a href="#" onclick="goToScanner()">👉 VÀO PHÒNG QUÉT THẺ</a>
        </div>
        <script>
            function goToScanner() {
                let currentHost = window.location.hostname;
                let currentPort = window.location.port ? ":" + window.location.port : "";
                window.location.href = "https://" + currentHost + currentPort + "/scanner";
            }
        </script>
    </body>
    </html>
    """
    return html_welcome

# ==========================================
# GIAO TIẾP THỜI GIAN THỰC (SOCKET.IO)
# ==========================================
@socketio.on('connect')
def handle_connect():
    socketio.emit('reset_live', {
        'question': current_state['question'],
        'answer': current_state.get('answer', '')
    })


@socketio.on('submit_answer')
def handle_answer(data):
    import re
    try:
        stu_id = int(data['id'])
        ans = str(data['answer']).upper()
        
        # q_id_received có thể là "1" hoặc "1a", "1b"...
        q_id_received = str(data.get('question_id', '-1'))
        
        # Dùng Regex để tách số câu và ký tự phụ
        match = re.match(r'^(\d+)([a-d]?)$', q_id_received)
        if not match:
            return # Dữ liệu rác
            
        base_q = int(match.group(1)) # Số câu gốc (VD: 1)
        sub_letter = match.group(2)  # Ký tự ý (VD: "a", hoặc rỗng "")

        # Kiểm tra xem có đang chiếu đúng câu không
        if current_state['question'] == 0 or base_q != current_state['question']:
            socketio.emit('reset_live', {'question': current_state['question']})
            return

        if not current_state['is_locked']:
            # Phát tín hiệu báo cho Giao diện UI (ui_overlay.py)
            # TRUYỀN THÊM sub_letter để ui_overlay.py biết đang chấm ý nào
            signals.student_scanned.emit(stu_id, ans, sub_letter)
            
            socketio.emit('update_live', {
                'id': stu_id, 
                'answer': ans, 
                'question_id': q_id_received # Phản hồi lại y chang "1a" để điện thoại biết
            })
    except Exception as e:
        print("Lỗi nhận bài:", e)

@socketio.on('change_question')
def handle_change_q(data):
    signals.change_question.emit(data.get('action'))

@socketio.on('finish_scan')
def handle_finish_scan():
    current_state['is_locked'] = True
    signals.trigger_grading.emit()

@socketio.on('unlock_scan')
def handle_unlock_scan():
    current_state['is_locked'] = False
    signals.unlock_board.emit()

@socketio.on('toggle_ranking')
def handle_toggle_ranking():
    signals.toggle_ranking.emit()

@socketio.on('send_student_image')
def handle_send_student_image(data):
    try:
        img_b64 = data.get('image', '')
        if img_b64:
            signals.show_student_image.emit(img_b64)
    except Exception as e:
        print("Lỗi nhận ảnh:", e)

@socketio.on('request_ratio')
def handle_request_ratio():
    socketio.emit('set_ratio', {'ratio': app_config.get("aspect_ratio", "16/9")})



'''

@socketio.on('mouse_move')
def handle_mouse_move(data):
    dx = float(data.get('dx', 0))
    dy = float(data.get('dy', 0))
    if dx != 0 or dy != 0:
        signals.remote_mouse_move.emit(dx, dy)

@socketio.on('mouse_click')
def handle_mouse_click():
    signals.remote_mouse_click.emit()


@socketio.on('mouse_move')
def handle_mouse_move(data):
    dx = int(data.get('dx', 0))
    dy = int(data.get('dy', 0))
    if dx != 0 or dy != 0:
        pt = _POINT()
        _user32.GetCursorPos(ctypes.byref(pt))
        _user32.SetCursorPos(pt.x + dx, pt.y + dy)
'''
@socketio.on('mouse_move')
def handle_mouse_move(data):
    dx = int(data.get('dx', 0))
    dy = int(data.get('dy', 0))
    if dx != 0 or dy != 0:
        pt = _POINT()
        _user32.GetCursorPos(ctypes.byref(pt))
        nx, ny = pt.x + dx, pt.y + dy
        _user32.SetCursorPos(nx, ny)
        if config.LASER_HWND:
            # 0x4015 = SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_ASYNCWINDOWPOS
            _user32.SetWindowPos(config.LASER_HWND, 0, nx - 18, ny - 18, 0, 0, 0x4015)


@socketio.on('mouse_click')
def handle_mouse_click():
    _user32.mouse_event(0x0002, 0, 0, 0, 0)
    _user32.mouse_event(0x0004, 0, 0, 0, 0)
    
def run_server(port):
    crt_path = get_base_path('dplick_ssl.crt')
    key_path = get_base_path('dplick_ssl.key')
    socketio.run(
        flask_app,
        host='0.0.0.0',
        port=port,
        ssl_context=(crt_path, key_path),
        allow_unsafe_werkzeug=True
    )

@socketio.on('switch_sub_question')
def handle_switch_sub(data):
    from config import signals
    signals.switch_sub_question.emit(data.get('sub_letter', ''))


@socketio.on('hide_student_image')
def handle_hide_student_image():
    try:
        signals.hide_student_image.emit()
    except Exception as e:
        print("Lỗi nhận lệnh tắt ảnh:", e)

@socketio.on('toggle_hide_all')
def handle_toggle_hide_all():
    signals.toggle_hide_screen.emit()

@socketio.on('toggle_laser')
def handle_toggle_laser(data):
    signals.toggle_laser.emit(bool(data.get('active', False)))

@socketio.on('random_student')
def handle_random_student():
    signals.trigger_random_student.emit()