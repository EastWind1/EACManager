package errs

import (
	"bytes"
	"errors"
	"fmt"
	"runtime"
	"strings"
)

// GetStack 捕获堆栈
func GetStack() []byte {
	// 跳过 NewError + GetStack + runtime.Callers 自身帧
	const skip = 3
	const depth = 16
	pc := make([]uintptr, depth)
	n := runtime.Callers(skip, pc)
	if n == 0 {
		return []byte("unknown")
	}

	frames := runtime.CallersFrames(pc[:n])
	var buf bytes.Buffer

	for frame, more := frames.Next(); more || frame.PC != 0; frame, more = frames.Next() {
		// 过滤第三方库/系统/工具包帧
		if strings.Contains(frame.Function, "github.com/") ||
			strings.Contains(frame.Function, "golang.org/") ||
			strings.Contains(frame.Function, "runtime/") ||
			strings.Contains(frame.Function, "errutil.") {
			continue
		}
		buf.WriteString(fmt.Sprintf("%s(", frame.Function))
		buf.WriteString(")\n")

		// 标准格式行：\t绝对路径/文件名:行号
		buf.WriteString(fmt.Sprintf("\t%s:%d \n",
			frame.File,
			frame.Line,
		))
	}
	return buf.Bytes()
}

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
	} else if len(e) > 0 && e[0] != nil {
		err.msg = e[0].Error()
	} else {
		err.msg = "业务异常"
	}

	if len(e) > 0 {
		err.err = e[0]
		if se, ok := errors.AsType[StackError](e[0]); ok {
			err.stack = se.Stack()
		}
	}
	if err.stack == nil || len(err.stack) == 0 {
		err.stack = GetStack()
	}
	return err
}

func Wrap(err error) error {
	if err == nil {
		return nil
	}
	if bizErr, ok := errors.AsType[*BizError](err); ok {
		return bizErr
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
		return fmt.Sprintf("%v : %v", e.msg, e.path)
	}
	return e.msg + ": " + e.path
}

func (e *FileOpError) ErrorWithoutPath() string {
	if e.err != nil {
		return fmt.Sprintf("%v : %v", e.msg, e.err.Error())
	}
	return e.msg
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
	} else if len(e) > 0 && e[0] != nil {
		err.msg = e[0].Error()
	} else {
		err.msg = "文件操作异常"
	}
	if len(e) > 0 {
		err.err = e[0]
		if se, ok := errors.AsType[StackError](e[0]); ok {
			err.stack = se.Stack()
		}
	}
	if err.stack == nil || len(err.stack) == 0 {
		err.stack = GetStack()
	}
	return err
}
