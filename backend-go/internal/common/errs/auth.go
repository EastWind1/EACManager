package errs

// UnauthError 未认证异常
type UnauthError struct {
	Message string
	Err     error
}

func (e *UnauthError) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}

func NewUnauthError(message string, e ...error) *AuthError {
	err := &AuthError{Message: message}
	if len(e) > 0 {
		err.Err = e[0]
	}
	return err
}

// AuthError 权限异常
type AuthError struct {
	Message string
	Err     error
}

func (e *AuthError) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}

func NewAuthError(message string, e ...error) *AuthError {
	err := &AuthError{Message: message}
	if len(e) > 0 {
		err.Err = e[0]
	}
	return err
}
