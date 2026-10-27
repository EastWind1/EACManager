package user

import (
	"backend-go/config"
	"backend-go/pkg/errs"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type JWTService struct {
	secret []byte
	expire int
}

func NewJWTService(cfg *config.JWTConfig) *JWTService {
	return &JWTService{
		secret: []byte(cfg.Secret),
		expire: cfg.Expire,
	}
}

func (s *JWTService) GenerateToken(subject string) (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		ExpiresAt: jwt.NewNumericDate(now.Add(time.Duration(s.expire) * time.Second)),
		IssuedAt:  jwt.NewNumericDate(now),
		Subject:   subject,
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	res, err := token.SignedString(s.secret)
	if err != nil {
		return "", errs.NewBizError("生成 Token 失败", err)
	}
	return res, nil
}

func (s *JWTService) VerifyToken(tokenString string) (string, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (any, error) {
		return s.secret, nil
	})

	if err != nil || !token.Valid {
		return "", errs.NewUnauthError("Token 无效")
	}

	if expireTime, ok := token.Claims.GetExpirationTime(); ok != nil || expireTime.Before(time.Now()) {
		return "", errs.NewUnauthError("Token 过期")
	}
	sub, err := token.Claims.GetSubject()
	if err != nil {
		return "", errs.NewUnauthError("Token 不合法")
	}
	return sub, nil
}
