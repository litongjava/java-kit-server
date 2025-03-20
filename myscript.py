import numpy as np
import matplotlib.pyplot as plt

# 定义函数 f(x) = x^2
def f(x):
    return x**2

# 定义切线方程
def tangent_line(a, x):
    return 2*a*x - a**2

# 生成 x 数据
x = np.linspace(-5, 5, 400)
y = f(x)

# 选取多个切点
a_values = [-2, -1, 0, 1, 2]

# 绘图
plt.figure(figsize=(8, 6))
plt.plot(x, y, label=r'$f(x) = x^2$', color='blue')

# 绘制每个切点的切线
for a in a_values:
    tangent_y = tangent_line(a, x)
    plt.plot(x, tangent_y, '--', label=fr'Tangent at $x={a}$')

    # 标记切点
    plt.scatter(a, f(a), color='red', zorder=3)

# 设置图表属性
plt.xlabel('x')
plt.ylabel('y')
plt.title('Function $f(x) = x^2$ and its Tangents')
plt.axhline(0, color='black', linewidth=0.5)
plt.axvline(0, color='black', linewidth=0.5)
plt.legend()
plt.grid(True)

# 显示图形
plt.show()