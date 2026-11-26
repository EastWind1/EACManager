package security

import (
	"backend-go/config"
	"strings"
	"testing"
	"time"
)

type testCache struct {
	flags map[string]bool
}

func newTestCache() *testCache {
	return &testCache{flags: make(map[string]bool)}
}

func (c *testCache) GetJSON(string, string, any) bool           { return false }
func (c *testCache) SetJSON(string, string, any, time.Duration) {}

func (c *testCache) SetFlag(name string, key string, _ time.Duration) {
	c.flags[name+":"+key] = true
}

func (c *testCache) HasFlag(name string, key string) bool {
	return c.flags[name+":"+key]
}

func (c *testCache) Delete(name string, key string) {
	delete(c.flags, name+":"+key)
}

func (c *testCache) DeleteName(string) {}
func (c *testCache) Clear()            { clear(c.flags) }

func TestTokenSubjectIsUserID(t *testing.T) {
	jwtSvc := newJWTService(&config.JWTConfig{
		Secret: strings.Repeat("a", 32),
		Expire: 60,
	})
	tokenSvc := newTokenService(newTestCache(), jwtSvc)

	token, err := tokenSvc.Issue(42)
	if err != nil {
		t.Fatal(err)
	}
	id, err := tokenSvc.Verify(token)
	if err != nil {
		t.Fatal(err)
	}
	if id != 42 {
		t.Fatalf("subject should be user id, got %d", id)
	}

	tokenSvc.Revoke(token)
	id, err = tokenSvc.Verify(token)
	if err != nil {
		t.Fatal(err)
	}
	if id != 0 {
		t.Fatalf("revoked token should be rejected, got %d", id)
	}
}
