#!/bin/bash
source /root/miniconda3/etc/profile.d/conda.sh
conda activate base
exec /usr/java/jdk1.8.0_411/bin/java -jar java-kit-server-1.0.0.jar --server.port=10054