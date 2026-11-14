package user

type LoginParam struct {
	Username string `json:"username" binding:"required" validate:"required" label:"用户名"`
	Password string `json:"password" binding:"required" validate:"required" label:"密码"`
}
