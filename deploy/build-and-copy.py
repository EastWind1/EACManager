import os
import shutil
import subprocess


def run_command(command, cwd=None):
    """ 运行命令 """
    print(f"执行命令: {command}")
    result = subprocess.run(command, shell=True, cwd=cwd)
    if result.returncode != 0:
        raise RuntimeError(f"命令失败: {command}")


def main():
    # 当前目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # 项目根目录
    root_dir = os.path.abspath(os.path.join(script_dir, ".."))
    # 后端目录
    backend_dir = os.path.join(root_dir, "backend")
    # 前端目录
    frontend_dir = os.path.join(root_dir, "frontend")
    # 交付物目录
    deploy_dir = os.path.join(root_dir, "deploy")
    # 后端 target 目录
    backend_target_dir = os.path.join(backend_dir, "target")
    # 前端 dist 目录
    frontend_dist_dir = os.path.join(frontend_dir, "dist")
    # 后端交付物目录
    backend_deploy_dir = os.path.join(deploy_dir, "backend")
    # 前端 html 交付物目录
    frontend_deploy_dir = os.path.join(deploy_dir, "frontend", "nginx", "html")

    # 打包后端
    print("打包后端...")
    os.chdir(backend_dir)
    run_command("mvn clean package")

    # 打包前端
    print("打包前端...")
    os.chdir(frontend_dir)
    run_command("pnpm build")

    # 拷贝交付物
    print("拷贝交付物...")

    # 清理旧目录
    for dir_path in [os.path.join(backend_deploy_dir, "lib"), frontend_deploy_dir]:
        if os.path.exists(dir_path):
            shutil.rmtree(dir_path)

    # 拷贝 jar 和 lib
    for filename in os.listdir(backend_target_dir):
        src_file = os.path.join(backend_target_dir, filename)
        if filename.endswith(".jar"):
            shutil.copy(src_file, os.path.join(backend_deploy_dir, "app.jar"))
        elif filename == "lib" and os.path.isdir(src_file):
            shutil.copytree(src_file, os.path.join(backend_deploy_dir, "lib"))

    # 拷贝前端文件
    if os.path.exists(frontend_dist_dir):
        shutil.copytree(frontend_dist_dir, frontend_deploy_dir)

    print("构建和拷贝完成！")


if __name__ == "__main__":
    main()
