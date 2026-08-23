package attach

import (
	"backend-go/config"
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"context"
	"fmt"
	"math/rand"
	"mime/multipart"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/gofiber/fiber/v3/log"
)

// Service 附件服务
type Service struct {
	attachRepo     *Repository
	billAttachRepo *BillAttachRelRepo
	rootPath       string
	tempPath       string
	cfg            *config.AttachmentConfig
	tempFiles      sync.Map
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
	log.Infof("创建临时文件夹 %v", tempPath)

	return &Service{
		attachRepo:     attachRepo,
		billAttachRepo: billAttachRepo,
		rootPath:       rootPath,
		tempPath:       tempPath,
		cfg:            cfg,
		tempFiles:      sync.Map{},
	}
}

// CreateTempFile 创建临时文件
func (s *Service) CreateTempFile(prefix string, suffix string) (string, error) {
	file, err := os.CreateTemp(s.tempPath, prefix+"*"+suffix)
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	defer file.Close()
	return file.Name(), nil
}

// CreateTempDir 创建临时文件
func (s *Service) CreateTempDir(prefix string) (string, error) {
	dir, err := os.MkdirTemp(s.tempPath, prefix+"*")
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	return dir, nil
}

// GetAbsolutePathById 根据 ID 获取绝对路径
func (s *Service) GetAbsolutePathById(ctx context.Context, id int) (string, error) {
	if id == 0 {
		return "", errs.NewBizError("ID 为空")
	}
	var absolutePath string
	if id < 0 {
		val, ok := s.tempFiles.Load(id)
		if !ok {
			return "", errs.NewBizError("附件不存在")
		}
		absolutePath = val.(string)
		if !strings.HasPrefix(absolutePath, s.tempPath) {
			return "", errs.NewBizError("路径不合法")
		}
		return absolutePath, nil
	}

	attach, err := s.attachRepo.FindByID(ctx, id)
	if err != nil {
		return "", errs.Wrap(err)
	}
	if attach == nil {
		return "", errs.NewBizError("附件不存在")
	}
	absolutePath = filepath.Join(s.rootPath, attach.RelativePath)
	absolutePath = filepath.Clean(absolutePath)
	if !strings.HasPrefix(absolutePath, s.rootPath) {
		return "", errs.NewBizError("路径不合法")
	}
	return absolutePath, nil
}

// GetAbsolutePathByRela 根据相对路径获取绝对路径
func (s *Service) GetAbsolutePathByRela(relativePath string) (string, error) {
	if relativePath == "" {
		return "", errs.NewBizError("相对路径为空")
	}
	var absolutePath string
	absolutePath = filepath.Join(s.rootPath, relativePath)
	absolutePath = filepath.Clean(absolutePath)
	if !strings.HasPrefix(absolutePath, s.rootPath) {
		return "", errs.NewBizError("路径不合法")
	}
	return absolutePath, nil
}

// GetRelativePath 获取相对路径
func (s *Service) GetRelativePath(absolutePath string) (string, error) {
	if absolutePath == "" {
		return "", errs.NewBizError("路径不能为空")
	}
	absolutePath = filepath.Clean(absolutePath)
	if !strings.HasPrefix(absolutePath, s.rootPath) {
		return "", errs.NewBizError("路径不合法")
	}
	relativePath, err := filepath.Rel(s.rootPath, absolutePath)
	if err != nil {
		return "", errs.Wrap(err)
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
		file, err := Upload(fileHeader, s.tempPath)
		if err != nil {
			return nil, err
		}
		id := -rand.Int()
		s.tempFiles.Store(id, file.Path)
		attachments = append(attachments, AttachmentDTO{
			ID:   id,
			Name: fileHeader.Filename,
			Type: file.Type,
		})
	}
	return attachments, nil
}

// GetResource 获取文件资源
func (s *Service) GetResource(ctx context.Context, dto *AttachmentDTO) (filename string, path string, err error) {
	if dto == nil {
		return "", "", errs.NewBizError("附件信息不能为空")
	}

	path, err = s.GetAbsolutePathById(ctx, dto.ID)
	if err != nil {
		return "", "", err
	}
	filename = dto.Name
	return
}

// GetByBill 获取业务单据附件
func (s *Service) GetByBill(ctx context.Context, billID uint, billType BillType) ([]Attachment, error) {
	attaches, err := s.attachRepo.FindByBill(ctx, billID, billType)
	if err != nil {
		return nil, err
	}
	return attaches, nil
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
			if dto.ID <= 0 {
				originPath, err := s.GetAbsolutePathById(ctx, dto.ID)
				addAttach := dto.TOEntity()
				addAttach.ID = 0
				if err != nil {
					return err
				}
				targetRelPath := filepath.Join(billType.String(), billNumber, fmt.Sprintf("%v-%v", time.Now().UnixMilli(), addAttach.Name))
				targetPath, err := s.GetAbsolutePathByRela(targetRelPath)
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
				delete(removedIDs, uint(dto.ID))
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
				targetPath, err := s.GetAbsolutePathByRela(rel.Attach.RelativePath)
				if err != nil {
					return err
				}
				ops = append(ops, FileOp{
					Type:   FileOpDelete,
					Target: targetPath,
				})
			}
		}
		if err = Exec(ops); err != nil {
			return err
		}
		return nil
	})
}

// CleanTempFiles  清理临时文件
func (s *Service) CleanTempFiles() {
	s.tempFiles.Range(func(k, v any) bool {
		path := v.(string)
		if !Exists(path) {
			s.tempFiles.Delete(k)
		}
		return true
	})
}
