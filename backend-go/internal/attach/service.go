package attach

import (
	"backend-go/config"
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"context"
	"fmt"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/gofiber/fiber/v3/log"
)

// Service 附件服务
type Service struct {
	attachRepo     *Repository
	billAttachRepo *BillAttachRelRepo
	rootPath       string
	tempPath       string
	cache          cache.Cache
	cfg            *config.AttachmentConfig
}

// tempPrefix 临时文件夹前缀
const tempPrefix = "eac-"

func NewService(
	cfg *config.AttachmentConfig,
	cache cache.Cache,
	attachRepo *Repository,
	billAttachRepo *BillAttachRelRepo,
) *Service {
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
	RegisterTempFile(cache, tempPath)
	log.Infof("创建临时文件夹 %v", tempPath)

	return &Service{
		attachRepo:     attachRepo,
		billAttachRepo: billAttachRepo,
		rootPath:       rootPath,
		tempPath:       tempPath,
		cache:          cache,
		cfg:            cfg,
	}
}

// CreateTempFile 创建临时文件
func (s *Service) CreateTempFile(cache cache.Cache, prefix string, suffix string) (string, error) {
	file, err := os.CreateTemp(s.tempPath, prefix+"*"+suffix)
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	RegisterTempFile(cache, file.Name())
	defer file.Close()
	return file.Name(), nil
}

// CreateTempDir 创建临时文件
func (s *Service) CreateTempDir(cache cache.Cache, prefix string) (string, error) {
	dir, err := os.MkdirTemp(s.tempPath, prefix+"*")
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	RegisterTempFile(cache, dir)
	return dir, nil
}

// ValidAbsolutePath 校验绝对路径
func (s *Service) validAbsolutePath(absolutePath string, isTemp bool) error {
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
func (s *Service) GetAbsolutePath(relativePath string, isTemp bool) (string, error) {
	if relativePath == "" {
		return "", errs.NewBizError("路径不能为空")
	}
	if after, ok := strings.CutPrefix(relativePath, "/"); ok {
		relativePath = after
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
func (s *Service) GetRelativePath(absolutePath string, isTemp bool) (string, error) {
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
		return "", errs.NewFileOpError("", "", err)
	}
	return relativePath, nil
}

// UploadTemps 上传临时文件
func (s *Service) UploadTemps(fileHeaders []*multipart.FileHeader) ([]AttachmentDTO, error) {
	var attachments []AttachmentDTO
	if len(fileHeaders) == 0 {
		return nil, errs.NewBizError("文件不能为空")
	}
	for _, fileHeader := range fileHeaders {
		file, err := Upload(s.cache, fileHeader, s.tempPath)
		if err != nil {
			return nil, err
		}
		relativePath, err := s.GetRelativePath(file.Path, true)
		if err != nil {
			return nil, err
		}
		attachments = append(attachments, AttachmentDTO{
			Name:         fileHeader.Filename,
			Type:         file.Type,
			Temp:         true,
			RelativePath: relativePath,
		})
	}
	return attachments, nil
}

// GetResource 获取文件资源
func (s *Service) GetResource(ctx context.Context, dto *AttachmentDTO) (filename string, path string, err error) {
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
		var attachment *Attachment
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
func (s *Service) GetByBill(ctx context.Context, billID uint, billType BillType) ([]AttachmentDTO, error) {
	attaches, err := s.attachRepo.FindByBill(ctx, billID, billType)
	if err != nil {
		return nil, err
	}
	res := make([]AttachmentDTO, len(attaches))
	for i, d := range attaches {
		res[i] = *d.ToDTO()
	}
	return res, nil
}

// UpdateRelativeAttach 根据目标附件集合更新业务单据关联附件
func (s *Service) UpdateRelativeAttach(ctx context.Context, billID uint, billNumber string, billType BillType, attachmentDTOs []AttachmentDTO) error {
	oldRelations, err := s.billAttachRepo.FindByBillIDAndBillType(ctx, billID, billType)
	if err != nil {
		return err
	}
	// 待删除
	removedIDs := make(map[uint]bool)
	for _, rel := range oldRelations {
		removedIDs[rel.AttachId] = true
	}
	var ops []FileOp
	return s.billAttachRepo.Transaction(ctx, func(tx context.Context) error {
		for _, dto := range attachmentDTOs {
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

				billAttachRel := &BillAttachRelation{
					BillId:   billID,
					BillType: billType,
					AttachId: addAttach.ID,
					Attach:   *addAttach,
				}
				if err = s.billAttachRepo.Create(tx, billAttachRel); err != nil {
					return err
				}

				ops = append(ops, FileOp{
					Type:   FileOpMove,
					Origin: originPath,
					Target: targetPath,
				})
			} else {
				delete(removedIDs, dto.ID)
			}
		}

		for _, rel := range oldRelations {
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
				ops = append(ops, FileOp{
					Type:   FileOpDelete,
					Target: targetPath,
				})
			}
		}
		if err = Exec(s.cache, ops); err != nil {
			return err
		}
		return nil
	})
}
