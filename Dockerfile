FROM litongjava/jdk:8u411-stable-slim

# 第一步：安装 ffmpeg 和相关依赖
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg libmp3lame0 wget curl  ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# 第二步：安装 python3.11 
RUN apt-get update && \
    apt-get install -y --no-install-recommends python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*


# 设置工作目录
WORKDIR /app

# 安装 Python 依赖
COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt --break-system-packages

# 复制 JAR 文件到容器
COPY target/java-linux-1.0.0.jar /app/

# 运行 JAR 文件
CMD ["java", "-jar", "java-linux-1.0.0.jar"]