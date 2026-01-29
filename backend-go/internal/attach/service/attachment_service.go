package service

import (
	"backend-go/config"
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/repository"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"context"
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

// FileTxUtil 文件事务工具
func FileTxUtil(ops []model.FileOperation) error {
	// TODO: 实现文件事务
	for _, op := range ops {
		switch op.Type {
		case model.FileOpTypeMove:
			if err := os.Rename(op.Origin, op.Target); err != nil {
				return err
			}
		case model.FileOpTypeDelete:
			if err := os.Remove(op.Target); err != nil {
				return err
			}
		}
	}
	return nil
}

// AttachmentService 附件服务
type AttachmentService struct {
	attachRepo     *repository.AttachmentRepository
	billAttachRepo *repository.BillAttachRelationRepository
	rootPath       string
	tempPath       string
	cache          *cache.Cache
	cfg            *config.AttachmentConfig
}

// tempPrefix 临时文件夹前缀
const tempPrefix = "eac-"

func NewAttachmentService(
	cfg *config.AttachmentConfig,
	cache *cache.Cache,
	attachRepo *repository.AttachmentRepository,
	billAttachRepo *repository.BillAttachRelationRepository,
) *AttachmentService {
	rootPath := cfg.Path
	if err := os.MkdirAll(rootPath, 0755); err != nil {
		log.Fatalf("创建附件根目录失败: %v - %v", rootPath, err)
	}
	tempPath, err := os.MkdirTemp(os.TempDir(), tempPrefix+"*")
	hook.RegisterTempFile(cache, tempPath)
	if err != nil {
		log.Fatalf("创建临时目录失败: %v - %v", tempPath, err)
	}
	return &AttachmentService{
		attachRepo:     attachRepo,
		billAttachRepo: billAttachRepo,
		rootPath:       rootPath,
		tempPath:       tempPath,
		cache:          cache,
		cfg:            cfg,
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
func (s *AttachmentService) GetFileType(path string) (model.AttachType, error) {
	mimeType := mime.TypeByExtension(filepath.Ext(path))
	switch mimeType {
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

// UploadTemps 上传临时文件
func (s *AttachmentService) UploadTemps(files []*multipart.FileHeader) ([]*model.Attachment, error) {
	var attachments []*model.Attachment
	for _, fileHeader := range files {
		attachment, err := s.UploadSingle(fileHeader, "")
		if err != nil {
			return nil, err
		}
		attachments = append(attachments, attachment)
	}
	return attachments, nil
}

// GetByBill 获取业务单据附件
func (s *AttachmentService) GetByBill(billID int, billType model.BillType) ([]*model.Attachment, error) {
	return s.MoveFromTemp(billID, "", billType, []model.AttachmentDTO{})
}

// GetResource 获取文件资源
func (s *AttachmentService) GetResource(dto *model.AttachmentDTO) ([]byte, string, error) {
	var relativePath string

	if dto.ID != 0 {
		ctx := context.Background()
		attachment, err := s.attachRepo.FindByID(ctx, dto.ID)
		if err != nil {
			return nil, "", errs.NewBizError("附件不存在")
		}
		if attachment == nil {
			return nil, "", errs.NewBizError("附件不存在")
		}
		relativePath = attachment.RelativePath
	} else {
		relativePath = dto.RelativePath
	}

	return s.GetFile(relativePath)
}

// UpdateRelativeAttach 根据目标附件集合更新业务单据关联附件
func (s *AttachmentService) UpdateRelativeAttach(billID int, billNumber string, billType model.BillType, attachmentDTOs []model.AttachmentDTO) ([]*model.Attachment, error) {
	return s.MoveFromTemp(billID, billNumber, billType, attachmentDTOs)
}

func (s *AttachmentService) GetFile(relativePath string) ([]byte, string, error) {
	absPath := s.GetAbsolutePath(relativePath)

	if _, err := os.Stat(absPath); os.IsNotExist(err) {
		return nil, "", errs.NewBizError("文件不存在")
	}

	data, err := os.ReadFile(absPath)
	if err != nil {
		return nil, "", errs.NewBizError("读取文件失败", err)
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
		return errs.NewBizError("文件不存在")
	}

	return os.Remove(absPath)
}

func (s *AttachmentService) MoveFromTemp(billID int, billNumber string, billType model.BillType, attachmentDTOs []model.AttachmentDTO) ([]*model.Attachment, error) {
	ctx := context.Background()
	existingRelations, _ := s.billAttachRepo.FindByBillIDAndBillType(ctx, billID, billType)
	removedIDs := make(map[int]bool)
	for _, rel := range *existingRelations {
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
					return nil, errs.NewBizError("移动文件失败", err)
				}

				newRelativePath := filepath.Join(billNumber, fileName)
				attachment := &model.Attachment{
					Name:         dto.Name,
					Type:         dto.Type,
					RelativePath: newRelativePath,
				}

				if err := s.attachRepo.Create(ctx, attachment); err != nil {
					return nil, err
				}

				relation := &model.BillAttachRelation{
					BillID:   billID,
					BillType: billType,
					AttachID: attachment.ID,
				}
				if err := s.billAttachRepo.Create(ctx, relation); err != nil {
					return nil, err
				}

				attachments = append(attachments, attachment)
			}
		} else {
			delete(removedIDs, dto.ID)
		}
	}

	for _, rel := range *existingRelations {
		if removedIDs[rel.AttachID] {
			if err := s.billAttachRepo.DeleteByID(ctx, rel.ID); err != nil {
				return nil, err
			}
		}
	}

	attachResult, err := s.attachRepo.FindByBill(ctx, billID, billType)
	if err != nil {
		return nil, err
	}
	if attachResult == nil || len(*attachResult) == 0 {
		return []*model.Attachment{}, nil
	}
	attachmentSlice := make([]*model.Attachment, len(*attachResult))
	for i, a := range *attachResult {
		attachmentSlice[i] = &a
	}
	return attachmentSlice, nil
}
