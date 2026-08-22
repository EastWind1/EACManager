package attach

import (
	"archive/zip"
	"backend-go/pkg/errs"
	"io"
	"math"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"

	"github.com/gabriel-vasile/mimetype"
	"github.com/ledongthuc/pdf"
)

// Exists 检查文件是否存在
func Exists(path string) bool {
	_, err := os.Stat(path)
	return !os.IsNotExist(err)
}

// IsDir 判断是否为目录
func IsDir(path string) bool {
	fileInfo, err := os.Stat(path)
	if err != nil {
		return false
	}
	return fileInfo.IsDir()
}

// CreateParentDirs 创建父目录
func CreateParentDirs(path string) error {
	dir := filepath.Dir(path)
	if dir == "" {
		return errs.NewFileOpError("路径为空", "")
	}
	if err := os.MkdirAll(dir, 0755); err != nil {
		return errs.NewFileOpError("", path, err)
	}
	return nil
}

// CreateFile 创建空文件
func CreateFile(path string) error {
	file, err := os.Create(path)
	if err != nil {
		return errs.NewFileOpError("", path, err)
	}
	if err = file.Close(); err != nil {
		return errs.NewFileOpError("", path, err)
	}
	return nil
}

// CopyFile 复制文件
func CopyFile(src, dst string) error {
	srcFile, err := os.Open(src)
	if err != nil {
		return errs.Wrap(err)
	}
	defer srcFile.Close()

	dstFile, err := os.Create(dst)
	if err != nil {
		return errs.NewFileOpError("", dst, err)
	}
	defer dstFile.Close()

	_, err = io.Copy(dstFile, srcFile)
	if err != nil {
		return errs.NewFileOpError("", src, err)
	}
	return nil
}

// MoveFile 移动文件
func MoveFile(src, dst string) error {
	if err := CopyFile(src, dst); err != nil {
		return err
	}
	if err := os.Remove(src); err != nil {
		return errs.NewFileOpError("", src, err)
	}
	return nil
}

// GetFileType 获取文件类型，若是可执行文件抛出异常
func GetFileType(path string) (Type, error) {
	mimeType, err := mimetype.DetectFile(path)
	if err != nil {
		return Other, errs.NewFileOpError("获取文件类型失败", "", err)
	}
	switch mimeType.String() {
	case "application/pdf":
		return PDF, nil
	case "image/jpeg", "image/png", "image/gif":
		return Image, nil
	case "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
		return Word, nil
	case "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
		return Excel, nil
	case "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat":
		return Other, errs.NewFileOpError("不支持的文件类型", "")
	default:
		return Other, nil
	}
}

// ConvertPDFToImage 转换 PDF 为图片
func ConvertPDFToImage(pdfPath string, target string) error {
	// TODO: 使用第三方工具实现
	return errs.NewBizError("暂不支持该功能")
}

// ExtractPDFText 从 PDF 提取文本
// 重写为基于坐标合并同一行文本块：获取每个字符的精确坐标后，
// 根据 X 间距判断是否插入空格，避免 PDF 库将空格分割文本拆分为独立块导致信息丢失。
func ExtractPDFText(pdfPath string) ([]string, error) {
	f, r, err := pdf.Open(pdfPath)
	if err != nil {
		return nil, errs.NewFileOpError("PDF 打开失败", pdfPath, err)
	}
	defer f.Close()
	var lines []string
	// Y 坐标容差：同一行的文本块最大 Y 差值
	const yDiff = 5.0
	// X 间距阈值：超过此值认为有词间距
	const xDiff = 1.0

	for i := 1; i <= r.NumPage(); i++ {
		p := r.Page(i)
		if p.V.IsNull() {
			continue
		}

		// 获取页面上所有文本片段及其精确坐标（字符级）
		content := p.Content()
		if len(content.Text) == 0 {
			continue
		}

		// 基于坐标合并为文本行
		var line strings.Builder
		var lastY, lastRight float64
		started := false
		for _, t := range content.Text {
			if t.S == "" || t.S == "\n" {
				continue
			}
			if !started {
				line.WriteString(t.S)
				lastY = t.Y
				lastRight = t.X + t.W
				started = true
				continue
			}

			curYDiff := math.Abs(t.Y - lastY)
			if curYDiff <= yDiff {
				if t.X > lastRight+xDiff {
					line.WriteString(" ")
				}
				line.WriteString(t.S)
				if t.X+t.W > lastRight {
					lastRight = t.X + t.W
				}
			} else {
				lines = append(lines, line.String())
				line.Reset()
				line.WriteString(t.S)
				lastY = t.Y
				lastRight = t.X + t.W
			}
		}
		if started {
			lines = append(lines, line.String())
		}
	}
	return lines, nil
}

type UploadResult struct {
	FileName string
	Type     Type
	Path     string
}

// Upload 上传单个文件
func Upload(fileHeader *multipart.FileHeader, tempDirPath string) (*UploadResult, error) {
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
	defer file.Close()
	err = os.MkdirAll(filepath.Dir(tempDirPath), 0750)
	if err != nil {
		return nil, errs.NewFileOpError("创建目录失败", "", err)
	}
	targetFile, err := os.CreateTemp(tempDirPath, "*-"+fileName)
	if err != nil {
		return nil, errs.NewFileOpError("创建文件失败", "", err)
	}
	defer targetFile.Close()
	_, err = io.Copy(targetFile, file)
	if err != nil {
		return nil, errs.NewFileOpError("保存文件失败", "", err)
	}

	fileType, err := GetFileType(targetFile.Name())
	if err != nil {
		return nil, errs.NewFileOpError("", targetFile.Name(), err)
	}

	return &UploadResult{
		FileName: fileName,
		Type:     fileType,
		Path:     targetFile.Name(),
	}, nil
}

// Zip 压缩目录或文件至指定路径
func Zip(src string, target string) (string, error) {
	if !Exists(src) {
		return "", errs.NewFileOpError("源不存在", src)
	}
	err := CreateParentDirs(target)
	if err != nil {
		return "", err
	}
	var zipFile *os.File
	var e error
	if target == "" {
		zipFile, e = os.CreateTemp("", "*.zip")
		if e != nil {
			return "", errs.NewFileOpError("", "", e)
		}
		target = zipFile.Name()
	} else {
		if !Exists(target) {
			err = CreateFile(target)
			if err != nil {
				return "", err
			}
		}
		zipFile, e = os.Open(target)
		if e != nil {
			return "", errs.NewFileOpError("", "", e)
		}

	}
	defer zipFile.Close()
	zipWriter := zip.NewWriter(zipFile)
	defer zipWriter.Close()

	e = filepath.Walk(src, func(filePath string, fileInfo os.FileInfo, err error) error {
		if err != nil {
			return err
		}

		relPath, _ := filepath.Rel(src, filePath)
		zipInnerPath := strings.ReplaceAll(relPath, string(filepath.Separator), "/")
		if fileInfo.IsDir() {
			zipInnerPath += "/" // 目录结尾加/
		}
		if fileInfo.IsDir() {
			_, err = zipWriter.Create(zipInnerPath)
			return err
		}
		srcFile, err := os.Open(filePath)
		if err != nil {
			return err
		}
		defer srcFile.Close()
		zipFileWriter, err := zipWriter.Create(zipInnerPath)
		if err != nil {
			return err
		}
		_, err = io.Copy(zipFileWriter, srcFile)
		return err
	})

	if e != nil {
		return "", errs.NewFileOpError("", src, e)
	}
	return target, nil
}
