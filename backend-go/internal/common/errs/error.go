package errs

import (
	"errors"
	"fmt"
	"runtime/debug"
)

type StackError interface {
	Error() string
	Stack() []byte
}

// BizError 业务异常
type BizError struct {
	msg   string
	err   error
	stack []byte
}

func (e *BizError) Error() string {
	if e.err != nil {
		return e.msg + ": " + e.err.Error()
	}
	return e.msg
}

func (e *BizError) Unwrap() error {
	return e.err
}

func (e *BizError) Stack() []byte {
	return e.stack
}

func NewBizError(message string, e ...error) *BizError {
	err := &BizError{}
	if message != "" {
		err.msg = message
	} else if len(e) > 0 {
		err.msg = e[0].Error()
	} else {
		err.msg = "业务异常"
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

func Wrap(err error) *BizError {
	if err == nil {
		return nil
	}
	return NewBizError("", err)
}

// FileOpError 文件操作异常
type FileOpError struct {
	msg   string
	err   error
	path  string
	stack []byte
}

func (e *FileOpError) Error() string {
	if e.err != nil {
		return fmt.Sprintf("%v : %v - %v", e.msg, e.err.Error(), e.path)
	}
	return e.msg + ": " + e.path
}

func (e *FileOpError) ErrorWithoutPath() string {
	if e.err != nil {
		return fmt.Sprintf("%v : %v", e.msg, e.err.Error())
	}
	return e.msg + ": " + e.path
}

func (e *FileOpError) Unwrap() error {
	return e.err
}

func (e *FileOpError) Stack() []byte {
	return e.stack
}

func NewFileOpError(message string, path string, e ...error) *FileOpError {
	err := &FileOpError{path: path}
	if message != "" {
		err.msg = message
	} else if len(e) > 0 {
		err.msg = e[0].Error()
	} else {
		err.msg = "文件操作异常"
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
