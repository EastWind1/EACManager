package service

import (
	"backend-go/config"
	"backend-go/internal/attach/files"
	"backend-go/internal/attach/hook"
	"backend-go/internal/attach/model"
	"backend-go/internal/attach/repository"
	"backend-go/internal/common/cache"
	"backend-go/internal/common/errs"
	"context"
	"fmt"
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
	cache          cache.Cache
	cfg            *config.AttachmentConfig
}

// tempPrefix 临时文件夹前缀
const tempPrefix = "eac-"

func NewAttachmentService(
	cfg *config.AttachmentConfig,
	cache cache.Cache,
	attachRepo *repository.AttachmentRepository,
	billAttachRepo *repository.BillAttachRelationRepository,
) *AttachmentService {
	appPath, err := os.Getwd()
	if err != nil {
		log.Fatal(err)
	}
	rootPath := filepath.Clean(filepath.Join(appPath, cfg.Path))
	if err := os.MkdirAll(rootPath, 0755); err != nil {
		log.Fatalf("创建附件根目录失败: %v - %v", rootPath, err)
	}
	tempPath, err := os.MkdirTemp(os.TempDir(), tempPrefix+"*")
	if err != nil {
		log.Fatalf("创建临时目录失败: %v - %v", tempPath, err)
	}
	hook.RegisterTempFile(cache, tempPath)
	log.Infof("创建临时文件夹 %v", tempPath)

	return &AttachmentService{
		attachRepo:     attachRepo,
		billAttachRepo: billAttachRepo,
		rootPath:       rootPath,
		tempPath:       tempPath,
		cache:          cache,
		cfg:            cfg,
	}
}

// CreateTempFile 创建临时文件
func (s *AttachmentService) CreateTempFile(cache cache.Cache, prefix string, suffix string) (string, error) {
	file, err := os.CreateTemp(s.tempPath, prefix+"*"+suffix)
	if err != nil {
		return "", err
	}
	hook.RegisterTempFile(cache, file.Name())
	defer file.Close()
	return file.Name(), nil
}

// CreateTempDir 创建临时文件
func (s *AttachmentService) CreateTempDir(cache cache.Cache, prefix string) (string, error) {
	dir, err := os.MkdirTemp(s.tempPath, prefix+"*")
	if err != nil {
		return "", err
	}
	hook.RegisterTempFile(cache, dir)
	return dir, nil
}

// ValidAbsolutePath 校验绝对路径
func (s *AttachmentService) validAbsolutePath(absolutePath string, isTemp bool) error {
	absolutePath = filepath.Clean(absolutePath)
	absolutePath, err := filepath.Abs(absolutePath)
	if err != nil {
		return errs.NewBizError("非法路径")
	}
	if isTemp {
		if !strings.HasPrefix(absolutePath, s.tempPath) {
			return errs.NewBizError("非法路径")
		}
	} else {
		if !strings.HasPrefix(absolutePath, s.rootPath) {
			return errs.NewBizError("非法路径")
		}
	}
	return nil
}

// GetAbsolutePath 获取绝对路径
func (s *AttachmentService) GetAbsolutePath(relativePath string, isTemp bool) (string, error) {
	if relativePath == "" {
		return "", errs.NewBizError("路径不能为空")
	}
	if strings.HasPrefix(relativePath, "/") {
		relativePath = strings.TrimPrefix(relativePath, "/")
	}
	var absolutePath string
	if isTemp {
		absolutePath = filepath.Join(s.tempPath, relativePath)
	} else {
		absolutePath = filepath.Join(s.rootPath, relativePath)
	}
	if err := s.validAbsolutePath(absolutePath, isTemp); err != nil {
		return "", err
	}
	return absolutePath, nil
}

// GetRelativePath 获取相对路径
func (s *AttachmentService) GetRelativePath(absolutePath string, isTemp bool) (string, error) {
	if absolutePath == "" {
		return "", errs.NewBizError("路径不能为空")
	}
	if err := s.validAbsolutePath(absolutePath, isTemp); err != nil {
		return "", err
	}
	var relativePath string
	var err error
	if isTemp {
		relativePath, err = filepath.Rel(s.tempPath, absolutePath)
	} else {
		relativePath, err = filepath.Rel(s.rootPath, absolutePath)
	}
	if err != nil {
		return "", err
	}
	return relativePath, nil
}

// UploadTemps 上传临时文件
func (s *AttachmentService) UploadTemps(fileHeaders *[]*multipart.FileHeader) (*[]model.AttachmentDTO, error) {
	var attachments []model.AttachmentDTO
	if fileHeaders == nil || len(*fileHeaders) == 0 {
		return nil, errs.NewBizError("文件不能为空")
	}
	for _, fileHeader := range *fileHeaders {
		file, err := files.Upload(s.cache, fileHeader, s.tempPath)
		if err != nil {
			return nil, err
		}
		relativePath, err := s.GetRelativePath(file.Path, true)
		if err != nil {
			return nil, err
		}
		attachments = append(attachments, model.AttachmentDTO{
			Name:         fileHeader.Filename,
			Type:         file.Type,
			Temp:         true,
			RelativePath: relativePath,
		})
	}
	return &attachments, nil
}

// GetResource 获取文件资源
func (s *AttachmentService) GetResource(ctx context.Context, dto *model.AttachmentDTO) (filename string, path string, err error) {
	if dto == nil {
		return "", "", errs.NewBizError("附件信息不能为空")
	}
	if dto.Temp {
		filename = dto.Name
		path, err = s.GetAbsolutePath(dto.RelativePath, true)
	} else {
		if dto.ID == 0 {
			err = errs.NewBizError("附件 ID 不能为空")
			return
		}
		var attachment *model.Attachment
		attachment, err = s.attachRepo.FindByID(ctx, dto.ID)
		if err != nil {
			return
		}
		if attachment == nil {
			err = errs.NewBizError("附件不存在")
			return
		}
		filename = attachment.Name
		path, err = s.GetAbsolutePath(attachment.RelativePath, false)
	}

	return
}

// GetByBill 获取业务单据附件
func (s *AttachmentService) GetByBill(ctx context.Context, billID uint, billType model.BillType) (*[]model.AttachmentDTO, error) {
	attaches, err := s.attachRepo.FindByBill(ctx, billID, billType)
	if err != nil {
		return nil, err
	}
	res := make([]model.AttachmentDTO, len(*attaches))
	for i, d := range *attaches {
		res[i] = *d.ToDTO()
	}
	return &res, nil
}

// UpdateRelativeAttach 根据目标附件集合更新业务单据关联附件
func (s *AttachmentService) UpdateRelativeAttach(ctx context.Context, billID uint, billNumber string, billType model.BillType, attachmentDTOs *[]model.AttachmentDTO) error {
	oldRelations, err := s.billAttachRepo.FindByBillIDAndBillType(ctx, billID, billType)
	if err != nil {
		return err
	}
	// 待删除
	removedIDs := make(map[uint]bool)
	for _, rel := range *oldRelations {
		removedIDs[rel.AttachId] = true
	}
	var ops []model.FileOp
	return s.billAttachRepo.Transaction(ctx, func(tx context.Context) error {
		if attachmentDTOs != nil {
			for _, dto := range *attachmentDTOs {
				// 新增
				if dto.ID == 0 {
					addAttach := dto.TOEntity()
					originPath, err := s.GetAbsolutePath(dto.RelativePath, dto.Temp)
					if err != nil {
						return err
					}
					targetRelPath := filepath.Join(billType.String(), billNumber, fmt.Sprintf("%v-%v", time.Now().UnixMilli(), addAttach.Name))
					targetPath, err := s.GetAbsolutePath(targetRelPath, false)
					if err != nil {
						return err
					}
					// 设置业务单据关联关系
					addAttach.RelativePath = targetRelPath

					billAttachRel := &model.BillAttachRelation{
						BillId:   billID,
						BillType: billType,
						AttachId: addAttach.ID,
						Attach:   *addAttach,
					}
					if err = s.billAttachRepo.Create(tx, billAttachRel); err != nil {
						return err
					}

					ops = append(ops, model.FileOp{
						Type:   model.FileOpMove,
						Origin: originPath,
						Target: targetPath,
					})
				} else {
					delete(removedIDs, dto.ID)
				}
			}
		}

		for _, rel := range *oldRelations {
			if removedIDs[rel.AttachId] {
				if err = s.attachRepo.DeleteByID(tx, rel.AttachId); err != nil {
					return err
				}
				if err = s.billAttachRepo.DeleteByID(tx, rel.ID); err != nil {
					return err
				}
				targetPath, err := s.GetAbsolutePath(rel.Attach.RelativePath, false)
				if err != nil {
					return err
				}
				ops = append(ops, model.FileOp{
					Type:   model.FileOpDelete,
					Target: targetPath,
				})
			}
		}
		if err = files.Exec(s.cache, &ops); err != nil {
			return err
		}
		return nil
	})
}
