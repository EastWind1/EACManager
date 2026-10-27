package user

type LoginParam struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

type LoginResult struct {
	Token string `json:"token"`
	User  DTO    `json:"user"`
}
