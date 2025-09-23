# main.py  —— 执行用户脚本并收集所有 matplotlib 图像到 images 目录

import os
import warnings

# 0) 必须在导入 matplotlib 之前设置后端
os.environ['MPLBACKEND'] = 'Agg'

# 屏蔽非交互式后端和缺字形相关的噪音警告
warnings.filterwarnings('ignore', message=r'.*FigureCanvasAgg is non-interactive.*')
warnings.filterwarnings('ignore', message=r'Glyph .* missing from font\(s\)')

import matplotlib as mpl
from matplotlib import font_manager as fm
import matplotlib.pyplot as plt

# -------- 字体自动设置（默认仅英文）--------
def _has_font_by_name(names):
    installed = {f.name.lower(): True for f in fm.fontManager.ttflist}
    for name in names:
        if installed.get(name.lower()):
            return True, name
    return False, None

def setup_matplotlib_fonts(prefer='en'):
    """
    prefer:
      - 'en'   : 始终使用英文常用字体（DejaVu Sans），并静默缺字形警告
      - 'auto' : 检测有无中文字体，有则用中文字体，无则回退英文并静默缺字形警告
      - 'cjk'  : 强制尝试中文字体（找不到则回退英文）
    """
    mpl.rcParams['axes.unicode_minus'] = False

    if prefer not in ('en', 'auto', 'cjk'):
        prefer = 'en'

    cjk_candidates = [
        # macOS
        'PingFang SC', 'Hiragino Sans GB', 'Songti SC', 'Heiti TC',
        # Windows
        'Microsoft YaHei', 'SimHei', 'SimSun',
        # Linux / 通用
        'Noto Sans CJK SC', 'WenQuanYi Micro Hei',
    ]

    if prefer in ('auto', 'cjk'):
        found, name = _has_font_by_name(cjk_candidates)
        if found and name:
            mpl.rcParams['font.sans-serif'] = [name]
            return {'chinese_enabled': True, 'font_family': name}

    # 英文回退
    mpl.rcParams['font.sans-serif'] = ['DejaVu Sans']
    return {'chinese_enabled': False, 'font_family': 'DejaVu Sans'}

# 1) 启用字体策略（当前环境不支持中文，按需只用英文）
setup_info = setup_matplotlib_fonts(prefer='auto')

# 2) 将 plt.show() 改为 no-op，避免阻塞；用户脚本里调用也不会中断
plt.show = lambda *args, **kwargs: None

# 3) 读取并执行你的用户脚本（由 Java 侧注入路径）
script_path = r'#(script_path)'
script_dir  = r'#(script_dir)'

# 确保 images 目录存在（Java 已创建，这里再保险一次）
images_dir = os.path.join(script_dir, 'images')
os.makedirs(images_dir, exist_ok=True)

# 4) 执行用户代码
code = open(script_path, 'r', encoding='utf-8').read()
exec_globals = {'__name__': '__main__'}
exec(code, exec_globals)

# 5) 保存所有图像
for i, num in enumerate(plt.get_fignums(), start=1):
    plt.figure(num)
    plt.savefig(os.path.join(images_dir, f'{i}_plot.png'))