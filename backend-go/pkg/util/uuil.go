package util

import (
	"sync"
	"time"
)

var (
	seq int32
	mut sync.Mutex
)

// GenIntID 生成有序 int32 ID
func GenIntID() int32 {
	mut.Lock()
	defer mut.Unlock()

	timeBit := int32((time.Now().Unix() & 0x3FFFFFF) << 5)
	seq++
	if seq >= (1 << 5) {
		seq = 0
	}
	return timeBit | seq
}
