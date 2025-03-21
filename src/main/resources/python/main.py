import warnings
warnings.filterwarnings('ignore', message='.*FigureCanvasAgg is non-interactive.*')

import matplotlib.pyplot as plt

# 重写 plt.show() 为一个空函数（或仅做显示，不清除图像）
plt.show = lambda: None

import os
# 1. 强制使用Agg后端
os.environ['MPLBACKEND'] = 'Agg'

# 2. 读入并执行你的脚本
code = open(r'#(script_path)', 'r', encoding='utf-8').read()
exec(code, {'__name__': '__main__'})

# 此时所有图形依然保留在内存中，可以遍历保存
for i, num in enumerate(plt.get_fignums(), start=1):
    plt.figure(num)  # 激活对应的图像
    plt.savefig(rf'#(script_dir)/images/{i}_plot.png')