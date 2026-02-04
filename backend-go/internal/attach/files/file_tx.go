package files

import (
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/model"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"fmt"
	"os"
	"path/filepath"

	"github.com/gofiber/fiber/v2/log"
)

// validSrc 检查源文件存在且为文件
func validSrc(origin string) error {
	if origin == "" {
		return errs.NewFileOpError("源路径不能为空", "")
	}
	if !Exists(origin) {
		return errs.NewFileOpError("源文件不存在", origin)
	}
	if IsDir(origin) {
		return errs.NewFileOpError("源不是文件", origin)
	}
	return nil
}

// validTarget 检查目标文件
//
// exist 表示期望的存在状态
func validTarget(target string, exist bool) error {
	if target == "" {
		return errs.NewFileOpError("目标路径不能为空", "")
	}

	if exist {
		if !Exists(target) {
			return errs.NewFileOpError("目标文件不存在", target)
		}
	} else {
		if Exists(target) {
			return errs.NewFileOpError("目标文件已存在", target)
		}
	}
	return nil
}
func execSingleOp(cache cache.Cache, op *model.FileOp) (*model.FileOp, error) {
	if op == nil {
		return nil, errs.NewFileOpError("操作为空", "")
	}
	var err error
	var res *model.FileOp
	switch op.Type {
	case model.FileOpCreate:
		if err = validTarget(op.Target, false); err != nil {
			break
		}
		if err = CreateParentDirs(op.Target); err != nil {
			break
		}
		if err = CreateFile(op.Target); err != nil {
			break
		}
		res = op
	case model.FileOpCopy:
		if err = validSrc(op.Origin); err != nil {
			break
		}
		if err = validTarget(op.Target, false); err != nil {
			break
		}
		if err = CreateParentDirs(op.Target); err != nil {
			break
		}
		if err = CopyFile(op.Origin, op.Target); err != nil {
			break
		}
		res = op
	case model.FileOpMove:
		if err = validSrc(op.Origin); err != nil {
			break
		}
		if err = validTarget(op.Target, false); err != nil {
			break
		}
		if err = CreateParentDirs(op.Target); err != nil {
			break
		}
		if err = MoveFile(op.Origin, op.Target); err != nil {
			break
		}
		res = op
	case model.FileOpDelete:
		if err = validTarget(op.Target, true); err != nil {
			break
		}
		// 软删除：移动到临时文件
		filename := filepath.Base(op.Target)
		var tempFile *os.File
		tempFile, err = os.CreateTemp("", filename)
		if err != nil {
			break
		}
		hook.RegisterTempFile(cache, tempFile.Name())
		defer tempFile.Close()
		if err = MoveFile(op.Target, tempFile.Name()); err != nil {
			break
		}
		res = &model.FileOp{
			Type:   model.FileOpMove,
			Origin: op.Target,
			Target: tempFile.Name(),
		}
	default:
		err = errs.NewFileOpError("不支持的操作类型", fmt.Sprintf("%d", op.Type))
	}
	return res, err
}

// Exec 执行文件操作
func Exec(cache cache.Cache, ops *[]model.FileOp) error {
	if ops == nil {
		return errs.NewFileOpError("操作列表为空", "")
	}
	var executedOps []model.FileOp
	// 执行操作
	var err error
	for _, op := range *ops {
		var execOp *model.FileOp
		execOp, err = execSingleOp(cache, &op)
		if err != nil {
			break
		}
		executedOps = append(executedOps, *execOp)
	}
	if err != nil {
		// 回滚已执行的操作
		rollback(&executedOps)
		return err
	}
	return nil
}

// rollback 回滚文件操作
func rollback(executedOps *[]model.FileOp) {
	if executedOps == nil {
		return
	}
	var rollbackErrs []error
	// 倒序回滚
	for i := len(*executedOps) - 1; i >= 0; i-- {
		op := (*executedOps)[i]

		switch op.Type {
		case model.FileOpCreate, model.FileOpCopy:
			if err := os.Remove(op.Target); err != nil {
				rollbackErrs = append(rollbackErrs, err)
			}
		case model.FileOpMove:
			if err := MoveFile(op.Target, op.Origin); err != nil {
				rollbackErrs = append(rollbackErrs, err)
			}
		default:
			rollbackErrs = append(rollbackErrs, errs.NewFileOpError("不支持的回滚操作"+op.Type.String(), ""))
		}
	}
	if len(rollbackErrs) > 0 {
		log.Errorf("文件操作回滚失败: %v", rollbackErrs)
	}
}
