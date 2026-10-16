package util

import (
	"sync"
	"time"
)

var (
	seq        int32 = 1
	preTimeBit int32
	mut        sync.Mutex
)

// GenIntID 生成时间有序 int32 ID（雪花算法）
func GenIntID() int32 {
	mut.Lock()
	defer mut.Unlock()

	// 取 26 位时间
	timeBit := int32((time.Now().UnixMilli() & 0x3FFFFFF) << 5)
	seq++
	// 每毫秒最大 31 个
	if timeBit != preTimeBit || seq >= (1<<5) {
		seq = 1
	}
	preTimeBit = timeBit
	return timeBit | seq
}
