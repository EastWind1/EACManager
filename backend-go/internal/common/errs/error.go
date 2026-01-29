package errs

import "fmt"

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

// FileOpError 文件操作异常
type FileOpError struct {
	Message string
	Err     error
	Path    string
}

func (e *FileOpError) Error() string {
	if e.Err != nil {
		return fmt.Sprintf("%v : %v - %v", e.Message, e.Path, e.Err.Error())
	}
	return e.Message + ": " + e.Path
}

func NewFileOpError(message string, path string, e ...error) *FileOpError {
	err := &FileOpError{Message: message, Path: path}
	if len(e) > 0 {
		err.Err = e[0]
	}
	return err
}
