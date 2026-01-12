#!/bin/bash
source /root/miniconda3/etc/profile.d/conda.sh
conda activate base
exec /usr/java/jdk-21.0.6/bin/java -jar java-kit-server-1.0.0.jar