package service

import (
	"backend-go/config"
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/repository"
	"backend-go/internal/common/errs"
	"fmt"
	"io"
	"mime"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2/log"
)

// AttachmentService 附件服务
type AttachmentService struct {
	attachRepo     *repository.AttachmentRepository
	billAttachRepo *repository.BillAttachRelationRepository
	rootPath       string
	tempPath       string
}

func NewAttachmentService(
	cfg *config.AttachmentConfig,
	attachRepo *repository.AttachmentRepository,
	billAttachRepo *repository.BillAttachRelationRepository,
) *AttachmentService {
	rootPath := cfg.Path
	tempPath := filepath.Join(rootPath, cfg.Temp)

	if err := os.MkdirAll(rootPath, 0755); err != nil {
		log.Fatalf("创建附件根目录失败: %v - %v", rootPath, err)
	}
	if err := os.MkdirAll(tempPath, 0755); err != nil {
		log.Fatalf("创建临时目录失败: %v - %v", tempPath, err)
	}

	return &AttachmentService{
		attachRepo:     attachRepo,
		billAttachRepo: billAttachRepo,
		rootPath:       rootPath,
		tempPath:       tempPath,
	}
}

// GetAbsolutePath 获取绝对路径
func (s *AttachmentService) GetAbsolutePath(relativePath string) string {
	relativePath = strings.TrimPrefix(relativePath, "/")
	return filepath.Join(s.rootPath, relativePath)
}

// validPath 验证路径是否合法
func (s *AttachmentService) validPath(path string) error {
	if !strings.HasPrefix(path, s.rootPath) {
		return errs.NewBizError("非法路径")
	}
	return nil
}

// IsTemp 检查路径是否为临时路径
func (s *AttachmentService) IsTemp(relativePath string) bool {
	return strings.HasPrefix(relativePath, s.tempPath)
}

// GetFileType 获取文件类型，若是可执行文件抛出异常
func (s *AttachmentService) GetFileType(path string) (model.AttachmentType, error) {
	mimeType := mime.TypeByExtension(filepath.Ext(path))
	switch mimeType {
	case "application/pdf":
		return model.AttachmentTypePDF, nil
	case "image/jpeg", "image/png", "image/gif":
		return model.AttachmentTypeImage, nil
	case "application/msword":
		return model.AttachmentTypeWord, nil
	case "application/vnd.ms-excel":
		return model.AttachmentTypeExcel, nil
	case "application/x-msdownload", "application/x-executable", "application/x-sh", "application/x-bat":
		return model.AttachmentTypeOther, errs.NewBizError("不支持的文件类型")
	default:
		return model.AttachmentTypeOther, nil
	}
}

// CreateDir 创建目录
func (s *AttachmentService) CreateDir(path string) error {
	if err := s.validPath(path); err != nil {
		return err
	}
	if err := os.MkdirAll(path, 0750); err != nil {
		return errs.NewBizError("创建目录失败", err)
	}
	return nil
}

// CreateFile 创建文件
func (s *AttachmentService) CreateFile(path string) error {
	if err := s.validPath(path); err != nil {
		return err
	}

	if _, err := os.Stat(path); os.IsExist(err) {
		return errs.NewBizError("文件已存在")
	}

	if err := s.CreateDir(filepath.Dir(path)); err != nil {
		return err
	}

	if err := os.WriteFile(path, []byte(""), 0660); err != nil {
		return errs.NewBizError("创建文件失败", err)
	}
	return nil
}

// ConvertPDFToImage 转换 PDF 为图片
func (s *AttachmentService) ConvertPDFToImage(pdfPath string) (string, error) {
	// TODO: 使用第三方工具实现
	return "", errs.NewBizError("暂不支持该功能")
}

// UploadSingle 上传单个文件
func (s *AttachmentService) UploadSingle(fileHeader *multipart.FileHeader, path string) (*model.Attachment, error) {
	if fileHeader == nil {
		return nil, errs.NewBizError("文件为空")
	}
	if err := s.validPath(path); err != nil {
		return nil, err
	}
	fileName := fileHeader.Filename
	if fileName == "" {
		return nil, errs.NewBizError("文件名不能为空")
	}
	file, err := fileHeader.Open()
	if err != nil {
		return nil, errs.NewBizError("打开文件失败", err)
	}
	defer func(file multipart.File) {
		err := file.Close()
		if err != nil {
			log.Errorf("关闭文件失败: %v", err)
		}
	}(file)

	timestamp := time.Now().UnixMilli()
	targetFileName := fmt.Sprintf("%d_%s", timestamp, fileName)

	dst, err := os.Create(targetPath)
	if err != nil {
		return nil, result.NewBizError("创建文件失败", err)
	}
	defer dst.Close()

	if _, err := io.Copy(dst, file); err != nil {
		os.Remove(targetPath)
		return nil, result.NewBizError("保存文件失败", err)
	}

	fileType, err := s.GetFileType(targetPath)
	if err != nil {
		os.Remove(targetPath)
		return nil, err
	}

	relativePath := filepath.Join(s.attachConfig.Temp, targetFileName)
	attachment := &model.Attachment{
		Name:         fileName,
		Type:         fileType,
		RelativePath: relativePath,
	}

	return attachments, nil
}

func (s *AttachmentService) GetFile(relativePath string) ([]byte, string, error) {
	absPath := s.GetAbsolutePath(relativePath)

	if _, err := os.Stat(absPath); os.IsNotExist(err) {
		return nil, "", result.NewBizError("文件不存在")
	}

	data, err := os.ReadFile(absPath)
	if err != nil {
		return nil, "", result.NewBizError("读取文件失败", err)
	}

	mimeType := mime.TypeByExtension(filepath.Ext(relativePath))
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}

	return data, mimeType, nil
}

func (s *AttachmentService) DeleteFile(relativePath string) error {
	absPath := s.GetAbsolutePath(relativePath)

	if _, err := os.Stat(absPath); os.IsNotExist(err) {
		return result.NewBizError("文件不存在")
	}

	return os.Remove(absPath)
}

func (s *AttachmentService) MoveFromTemp(billID int, billNumber string, billType model.BillType, attachmentDTOs []model.AttachmentDTO) ([]*model.Attachment, error) {
	existingRelations, _ := s.billAttachRepo.FindByBillIDAndBillType(billID, billType)
	removedIDs := make(map[int]bool)
	for _, rel := range existingRelations {
		removedIDs[rel.AttachID] = true
	}

	var attachments []*model.Attachment
	for _, dto := range attachmentDTOs {
		if dto.ID == 0 {
			absPath := s.GetAbsolutePath(dto.RelativePath)
			if strings.HasPrefix(absPath, s.tempPath) {
				targetDir := filepath.Join(s.rootPath, billNumber)
				os.MkdirAll(targetDir, 0755)

				fileName := filepath.Base(absPath)
				targetPath := filepath.Join(targetDir, fileName)

				if err := os.Rename(absPath, targetPath); err != nil {
					return nil, result.NewBizError("移动文件失败", err)
				}

				newRelativePath := filepath.Join(billNumber, fileName)
				attachment := &model.Attachment{
					Name:         dto.Name,
					Type:         dto.Type,
					RelativePath: newRelativePath,
				}

				if err := s.attachRepo.Create(attachment); err != nil {
					return nil, err
				}

				relation := &model.BillAttachRelation{
					BillID:   billID,
					BillType: billType,
					AttachID: attachment.ID,
				}
				if err := s.billAttachRepo.Create(relation); err != nil {
					return nil, err
				}

				attachments = append(attachments, attachment)
			}
		} else {
			delete(removedIDs, dto.ID)
		}
	}

	for _, rel := range existingRelations {
		if removedIDs[rel.AttachID] {
			s.billAttachRepo.Delete(rel)
		}
	}

	return s.attachRepo.FindByBill(billID, billType)
}
