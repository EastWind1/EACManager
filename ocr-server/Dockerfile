FROM python:3.14-slim

ENV DEBIAN_FRONTEND=noninteractive

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple && \
    pip uninstall opencv-python -y && \
    pip install --no-cache opencv-python-headless -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY src ./src

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH=/app

EXPOSE 9003

CMD ["uvicorn", "ocr_server:app", "--host", "0.0.0.0", "--port", "9003", "--workers", "2"]
