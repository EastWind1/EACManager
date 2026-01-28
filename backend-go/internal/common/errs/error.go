package errs

// BizError 业务异常
type BizError struct {
	Message string
	Err     error
}

func (e *BizError) Error() string {
	if e.Err != nil {
		return e.Message + ": " + e.Err.Error()
	}
	return e.Message
}

func NewBizError(message string, e ...error) *BizError {
	err := &BizError{Message: message}
	if len(e) > 0 {
		err.Err = e[0]
	}
	return err
}
