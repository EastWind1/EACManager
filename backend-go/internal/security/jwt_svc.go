package security

import (
	"backend-go/config"
	"backend-go/pkg/errs"
	"crypto/rand"
	"encoding/hex"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

type tokenClaims struct {
	jwt.RegisteredClaims
}

type JWTService struct {
	secret []byte
	// token 有效期，单位秒
	expire int
}

func newJWTService(cfg *config.JWTConfig) *JWTService {
	return &JWTService{
		secret: []byte(cfg.Secret),
		expire: cfg.Expire,
	}
}

func newTokenID() string {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return time.Now().Format("20060102150405.000000000")
	}
	return hex.EncodeToString(buf)
}

func (s *JWTService) GenerateToken(subject string) (string, string, error) {
	now := time.Now()
	id := newTokenID()
	claims := tokenClaims{
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(now.Add(time.Duration(s.expire) * time.Second)),
			IssuedAt:  jwt.NewNumericDate(now),
			ID:        id,
			Subject:   subject,
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	res, err := token.SignedString(s.secret)
	if err != nil {
		return "", "", errs.NewBizError("生成 Token 失败", err)
	}
	return res, id, nil
}

func (s *JWTService) VerifyToken(tokenString string) (string, error) {
	token, err := s.parse(tokenString)
	if err != nil {
		return "", err
	}
	claims, ok := token.Claims.(*tokenClaims)
	if !ok {
		return "", errs.NewUnauthError("Token 不合法")
	}
	sub, err := claims.GetSubject()
	if err != nil || sub == "" {
		return "", errs.NewUnauthError("Token 不合法")
	}
	return sub, nil
}

// Remaining 返回 token 距离过期的剩余时间；无法验证或已过期时返回 0。
func (s *JWTService) Remaining(tokenString string) time.Duration {
	token, err := s.parse(tokenString)
	if err != nil {
		return 0
	}
	expireTime, err := token.Claims.GetExpirationTime()
	if err != nil || expireTime == nil {
		return 0
	}
	remaining := time.Until(expireTime.Time)
	if remaining < 0 {
		return 0
	}
	return remaining
}

func (s *JWTService) parse(tokenString string) (*jwt.Token, error) {
	token, err := jwt.ParseWithClaims(tokenString, &tokenClaims{}, func(token *jwt.Token) (any, error) {
		if token.Method.Alg() != jwt.SigningMethodHS256.Alg() {
			return nil, errs.NewUnauthError("Token 无效")
		}
		return s.secret, nil
	})
	if err != nil || !token.Valid {
		return nil, errs.NewUnauthError("Token 无效")
	}
	if expireTime, err := token.Claims.GetExpirationTime(); err != nil || expireTime.Before(time.Now()) {
		return nil, errs.NewUnauthError("Token 过期")
	}
	return token, nil
}
