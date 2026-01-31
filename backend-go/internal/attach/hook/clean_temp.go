package hook

import (
	"backend-go/internal/common/cache"
	"os"
	"os/signal"
	"syscall"

	"github.com/gofiber/fiber/v2/log"
)

// tempCacheName 临时文件缓存名称
const tempCacheName = "temp_file_paths"

// deleteTempFiles 删除临时文件和文件夹
func deleteTempFiles(c cache.Cache) {
	temps, ok := c.GetAll(tempCacheName)
	if !ok {
		return
	}
	for key, _ := range *temps {
		err := os.RemoveAll(key)
		if err != nil {
			log.Errorf("删除临时文件失败: %v", err)
		} else {
			log.Infof("清除临时文件夹 %v", key)
		}
	}
}

// RegisterTempFile 注册临时文件或文件夹
func RegisterTempFile(c cache.Cache, path string) {
	c.Put(tempCacheName, path, nil)
}

// SetupCleanOnExit 注册退出清理临时文件
func SetupCleanOnExit(c cache.Cache) {
	sigChan := make(chan os.Signal, 2)
	signal.Notify(
		sigChan,
		syscall.SIGINT,  // Ctrl+C（跨平台）
		syscall.SIGTERM, // kill 命令（Linux/Mac）
		syscall.SIGQUIT, // Linux/Mac 退出信号
	)

	go func() {
		<-sigChan
		log.Infof("准备清理临时文件")
		deleteTempFiles(c)
		close(sigChan)
		os.Exit(0)
	}()
}
