import warnings
warnings.filterwarnings('ignore', message='.*FigureCanvasAgg is non-interactive.*')

import os
# 1. 强制使用Agg后端
os.environ['MPLBACKEND'] = 'Agg'

# 2. 读入并执行你的脚本
code = open(r'#(script_path)', 'r', encoding='utf-8').read()
exec(code, {'__name__': '__main__'})

# 3. 执行完后再保存图片
import matplotlib.pyplot as plt
plt.savefig(r'images/#(temp_id).png')