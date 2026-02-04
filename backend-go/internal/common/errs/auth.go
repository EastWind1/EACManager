package errs

import (
	"errors"
	"runtime/debug"
)

// UnauthError 未认证异常
type UnauthError struct {
	msg   string
	err   error
	stack []byte
}

func (e *UnauthError) Error() string {
	if e.err != nil {
		return e.msg + ": " + e.err.Error()
	}
	return e.msg
}

func (e *UnauthError) Unwrap() error {
	return e.err
}

func (e *UnauthError) Stack() []byte {
	return e.stack
}

func NewUnauthError(message string, e ...error) *UnauthError {
	err := &UnauthError{}
	if message != "" {
		err.msg = message
	} else if len(e) > 0 {
		err.msg = e[0].Error()
	} else {
		err.msg = "认证异常"
	}

	if len(e) > 0 {
		err.err = e[0]
		var se StackError
		if errors.As(e[0], &se) {
			err.stack = se.Stack()
		}
	}
	if err.stack != nil {
		err.stack = debug.Stack()
	}
	return err
}

// AuthError 权限异常
type AuthError struct {
	msg   string
	err   error
	stack []byte
}

func (e *AuthError) Error() string {
	if e.err != nil {
		return e.msg + ": " + e.err.Error()
	}
	return e.msg
}

func (e *AuthError) Unwrap() error {
	return e.err
}

func (e *AuthError) Stack() []byte {
	return e.stack
}

func NewAuthError(message string, e ...error) *AuthError {
	err := &AuthError{}
	if message != "" {
		err.msg = message
	} else if len(e) > 0 {
		err.msg = e[0].Error()
	} else {
		err.msg = "鉴权异常"
	}
	if len(e) > 0 {
		err.err = e[0]
		var se StackError
		if errors.As(e[0], &se) {
			err.stack = se.Stack()
		}
	}
	if err.stack != nil {
		err.stack = debug.Stack()
	}
	return err
}
