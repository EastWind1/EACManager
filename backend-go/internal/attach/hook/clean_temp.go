package hook

import (
	"backend-go/internal/common/cache"
	"os"

	"github.com/gofiber/fiber/v2/log"
)

// tempCacheName 临时文件缓存名称
const tempCacheName = "temp_file_paths"

// DeleteTempFiles 删除临时文件和文件夹
func DeleteTempFiles(c cache.Cache) {
	temps, ok := c.GetAll(tempCacheName)
	if !ok {
		return
	}
	for key, _ := range *temps {
		err := os.RemoveAll(key)
		if err != nil {
			log.Errorf("删除临时文件失败: %v", err)
		}
	}
}

// RegisterTempFile 注册临时文件或文件夹
func RegisterTempFile(c cache.Cache, path string) {
	c.Put(tempCacheName, path, nil)
}
