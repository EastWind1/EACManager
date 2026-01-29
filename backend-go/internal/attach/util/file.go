package util

import (
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/model"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"io"
	"mime/multipart"
	"os"
	"path/filepath"

	"github.com/gabriel-vasile/mimetype"
	"github.com/gofiber/fiber/v2/log"
)

// GetFileType 获取文件类型，若是可执行文件抛出异常
func GetFileType(path string) (model.AttachType, error) {
	mimeType, err := mimetype.DetectFile(path)
	if err != nil {
		return model.AttachTypeOther, errs.NewBizError("获取文件类型失败", err)
	}
	switch mimeType.String() {
	case "application/pdf":
		return model.AttachTypePDF, nil
	case "image/jpeg", "image/png", "image/gif":
		return model.AttachTypeImage, nil
	case "application/msword":
		return model.AttachTypeWord, nil
	case "application/vnd.ms-excel":
		return model.AttachTypeExcel, nil
	case "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat":
		return model.AttachTypeOther, errs.NewBizError("不支持的文件类型")
	default:
		return model.AttachTypeOther, nil
	}
}

// ConvertPDFToImage 转换 PDF 为图片
func ConvertPDFToImage(pdfPath string) (string, error) {
	// TODO: 使用第三方工具实现
	return "", errs.NewBizError("暂不支持该功能")
}

type UploadResult struct {
	FileName string
	Type     model.AttachType
	Path     string
}

// Upload 上传单个文件
func Upload(c *cache.Cache, fileHeader *multipart.FileHeader, tempDirPath string) (*UploadResult, error) {
	if fileHeader == nil {
		return nil, errs.NewFileOpError("文件为空", "")
	}
	fileName := fileHeader.Filename
	if fileName == "" {
		return nil, errs.NewFileOpError("文件名不能为空", "")
	}
	if tempDirPath == "" {
		tempDirPath = os.TempDir()
	}

	file, err := fileHeader.Open()
	if err != nil {
		return nil, errs.NewFileOpError("打开文件失败", "", err)
	}
	defer func(file multipart.File) {
		err = file.Close()
		if err != nil {
			log.Errorf("关闭文件失败：%v", err)
		}
	}(file)
	err = os.MkdirAll(filepath.Dir(tempDirPath), 0750)
	if err != nil {
		return nil, errs.NewFileOpError("创建目录失败", "", err)
	}
	targetFile, err := os.CreateTemp(tempDirPath, "*-"+fileName)
	hook.RegisterTempFile(c, targetFile.Name())
	if err != nil {
		return nil, errs.NewBizError("创建文件失败", err)
	}
	defer func(targetFile *os.File) {
		err = targetFile.Close()
		if err != nil {
			log.Errorf("关闭文件失败：%v", err)
		}
	}(targetFile)
	_, err = io.Copy(targetFile, file)
	if err != nil {
		return nil, errs.NewFileOpError("保存文件失败", "", err)
	}

	fileType, err := GetFileType(targetFile.Name())
	if err != nil {
		return nil, err
	}

	return &UploadResult{
		FileName: fileName,
		Type:     fileType,
		Path:     targetFile.Name(),
	}, nil
}
