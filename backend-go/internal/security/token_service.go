package security

import (
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"crypto/sha256"
	"encoding/hex"
	"strconv"
)

const tokenBlacklistName = "auth-blacklist"

// TokenService 管理单 token：JWT 的 subject 是用户 id，登出后进入短期黑名单。
type TokenService struct {
	cache cache.Cache
	jwt   *JWTService
}

func newTokenService(cache cache.Cache, jwt *JWTService) *TokenService {
	return &TokenService{cache: cache, jwt: jwt}
}

func (s *TokenService) Issue(userID uint) (string, error) {
	token, _, err := s.jwt.GenerateToken(strconv.FormatUint(uint64(userID), 10))
	return token, err
}

func (s *TokenService) Verify(token string) (uint, error) {
	if token == "" || s.IsRevoked(token) {
		return 0, nil
	}
	subject, err := s.jwt.VerifyToken(token)
	if err != nil {
		return 0, err
	}
	id, err := strconv.ParseUint(subject, 10, 64)
	if err != nil || id == 0 {
		return 0, errs.NewUnauthError("Token 不合法")
	}
	return uint(id), nil
}

func (s *TokenService) IsRevoked(token string) bool {
	return token != "" && s.cache.HasFlag(tokenBlacklistName, tokenKey(token))
}

func (s *TokenService) Revoke(token string) {
	if token == "" {
		return
	}
	ttl := s.jwt.Remaining(token)
	if ttl <= 0 {
		return
	}
	s.cache.SetFlag(tokenBlacklistName, tokenKey(token), ttl)
}

func tokenKey(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}
