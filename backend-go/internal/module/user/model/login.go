package model

// LoginParam 登录参数
type LoginParam struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

// LoginResult 登录结果
type LoginResult struct {
	Token string  `json:"token"`
	User  UserDTO `json:"user"`
}
