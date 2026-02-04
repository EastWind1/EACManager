package service

import (
	"backend-go/config"
	"backend-go/internal/common/errs"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// JWTService JWT服务
type JWTService struct {
	secret []byte
	expire int
}

// NewJWTService 创建JWT服务实例
func NewJWTService(cfg *config.JWTConfig) *JWTService {
	return &JWTService{
		secret: []byte(cfg.Secret),
		expire: cfg.Expire,
	}
}

// GenerateToken 生成JWT令牌
func (s *JWTService) GenerateToken(username string, subject string) (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Duration(s.expire) * time.Second)),
		IssuedAt:  jwt.NewNumericDate(now),
		Subject:   subject,
		Audience:  []string{username},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	res, err := token.SignedString(s.secret)
	if err != nil {
		return "", errs.NewBizError("生成 Token 失败", err)
	}
	return res, nil
}

type TokenInfo struct {
	Username string
	Subject  string
}

// VerifyToken 验证JWT令牌
func (s *JWTService) VerifyToken(tokenString string) (*TokenInfo, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		return s.secret, nil
	})

	if err != nil || !token.Valid {
		return nil, errs.NewUnauthError("Token 无效")
	}

	if expireTime, ok := token.Claims.GetExpirationTime(); ok != nil || expireTime.Before(time.Now()) {
		return nil, errs.NewUnauthError("Token 过期")
	}

	aud, err := token.Claims.GetAudience()
	if err != nil || len(aud) < 1 {
		return nil, errs.NewUnauthError("Token 不合法")
	}
	sub, err := token.Claims.GetSubject()
	if err != nil {
		return nil, errs.NewUnauthError("Token 不合法")
	}
	return &TokenInfo{
		Username: aud[0],
		Subject:  sub,
	}, nil
}
