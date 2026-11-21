package main

import (
	"backend-go/server"
	// 内嵌时区数据。
	_ "time/tzdata"
)

func main() {
	server.Run()
}
