FROM python:3.12-slim

ENV DEBIAN_FRONTEND=noninteractive

WORKDIR /app

RUN pip install --no-cache-dir onnxruntime rapidocr_api -i https://mirrors.aliyun.com/pypi/simple

RUN pip uninstall -y opencv-python && \
    pip install --no-cache-dir opencv-python-headless -i https://mirrors.aliyun.com/pypi/simple

EXPOSE 9003

CMD ["bash", "-c", "rapidocr_api -ip 0.0.0.0 -p 9003 -workers 2"]