package attach

import (
	"backend-go/config"
	"backend-go/pkg/cache"
	"backend-go/pkg/errs"
	"context"
	"mime/multipart"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
	"uuid"

	"github.com/gofiber/fiber/v3/log"
)

// Service 附件以「namespace/唯一id」为 key 存储，一经写入不再移动。
// 上传即落库并标记为瞬时态，被单据提交时置为持久态，游离附件按状态回收。
type Service struct {
	attachRepo *Repository
	rootPath   string
	tempPath   string
	cfg        *config.AttachConfig
}

const tempPrefix = "eac-"

// transientRetention 游离附件保留时长，超过后由 CleanTransientFiles 回收
const transientRetention = 7 * 24 * time.Hour

var namespacePattern = regexp.MustCompile(`^[a-zA-Z0-9_-]+$`)

func NewService(
	cfg *config.AttachConfig,
	cache cache.Cache,
	attachRepo *Repository,
) *Service {
	appPath, err := os.Getwd()
	if err != nil {
		log.Fatal(err)
	}
	rootPath := cfg.Path
	if !filepath.IsAbs(rootPath) {
		rootPath = filepath.Join(appPath, rootPath)
	}
	rootPath = filepath.Clean(rootPath)
	if err := os.MkdirAll(rootPath, 0755); err != nil {
		log.Fatalf("创建附件根目录失败: %v - %v", rootPath, err)
	}
	tempPath, err := os.MkdirTemp(os.TempDir(), tempPrefix+"*")
	if err != nil {
		log.Fatalf("创建临时目录失败: %v - %v", tempPath, err)
	}
	log.Infof("创建临时文件夹 %v", tempPath)

	return &Service{
		attachRepo: attachRepo,
		rootPath:   rootPath,
		tempPath:   tempPath,
		cfg:        cfg,
	}
}

func (s *Service) CreateTempFile(prefix string, suffix string) (string, error) {
	file, err := os.CreateTemp(s.tempPath, prefix+"*"+suffix)
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	defer file.Close()
	return file.Name(), nil
}

func (s *Service) CreateTempDir(prefix string) (string, error) {
	dir, err := os.MkdirTemp(s.tempPath, prefix+"*")
	if err != nil {
		return "", errs.NewFileOpError("", s.tempPath, err)
	}
	return dir, nil
}

func (s *Service) GetAbsolutePathById(ctx context.Context, id int) (string, error) {
	if id <= 0 {
		return "", errs.NewBizError("附件 ID 不合法")
	}
	attachment, err := s.attachRepo.FindByID(ctx, uint(id))
	if err != nil {
		return "", errs.Wrap(err)
	}
	if attachment == nil {
		return "", errs.NewBizError("附件不存在")
	}
	return s.GetAbsolutePathByRela(attachment.RelativePath)
}

func (s *Service) GetAbsolutePathByRela(relativePath string) (string, error) {
	if relativePath == "" {
		return "", errs.NewBizError("相对路径为空")
	}
	absolutePath := filepath.Clean(filepath.Join(s.rootPath, relativePath))
	if !strings.HasPrefix(absolutePath, s.rootPath) {
		return "", errs.NewBizError("路径不合法")
	}
	return absolutePath, nil
}

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

func (s *Service) Upload(ctx context.Context, namespace string, fileHeaders []*multipart.FileHeader) ([]DTO, error) {
	if !namespacePattern.MatchString(namespace) {
		return nil, errs.NewBizError("附件命名空间不合法")
	}
	if len(fileHeaders) == 0 {
		return nil, errs.NewBizError("文件不能为空")
	}

	attachments := make([]DTO, 0, len(fileHeaders))
	for _, fileHeader := range fileHeaders {
		attachment, err := s.saveOne(ctx, namespace, fileHeader)
		if err != nil {
			return nil, err
		}
		attachments = append(attachments, *attachment.ToDTO())
	}
	return attachments, nil
}

func (s *Service) saveOne(ctx context.Context, namespace string, fileHeader *multipart.FileHeader) (*Attach, error) {
	fileName := fileHeader.Filename
	if fileName == "" {
		return nil, errs.NewBizError("文件名不能为空")
	}

	relativePath := namespace + "/" + uuid.New().String() + "-" + fileName
	targetPath, err := s.GetAbsolutePathByRela(relativePath)
	if err != nil {
		return nil, err
	}
	if err = CreateParentDirs(targetPath); err != nil {
		return nil, err
	}
	fileType, err := Save(fileHeader, targetPath)
	if err != nil {
		return nil, err
	}

	attachment := &Attach{
		Name:         fileName,
		Type:         fileType,
		RelativePath: relativePath,
		Status:       StatusTransient,
	}
	if err = s.attachRepo.Create(ctx, attachment); err != nil {
		DeleteQuietly(targetPath)
		return nil, err
	}
	return attachment, nil
}

// Persist 把瞬时态附件置为持久态；任一 id 不合法则整体报错。
func (s *Service) Persist(ctx context.Context, ids []uint) error {
	if len(ids) == 0 {
		return nil
	}
	affected, err := s.attachRepo.PersistByIds(ctx, ids)
	if err != nil {
		return err
	}
	if affected != int64(len(ids)) {
		return errs.NewBizError("附件不存在或已被提交")
	}
	return nil
}

// Delete 删除附件及其物理文件
func (s *Service) Delete(ctx context.Context, ids []uint) error {
	unique := uniqueIDs(ids)
	if len(unique) == 0 {
		return nil
	}
	// 删除前先取路径：行删掉之后就拿不到文件位置了
	attachments, err := s.attachRepo.FindAll(ctx, "id IN ?", unique)
	if err != nil {
		return err
	}
	if len(attachments) != len(unique) {
		return errs.NewBizError("附件不存在")
	}
	if err = s.attachRepo.DeleteByIDs(ctx, unique); err != nil {
		return err
	}
	return s.deleteFiles(attachments)
}

// CleanTransientFiles 回收过期的游离附件
//
// 单条条件 DELETE 直接清库（不再先查全部行再逐个删），文件路径另取一次；
// 受影响行数为 0 说明没有可回收的记录，直接返回。
func (s *Service) CleanTransientFiles(ctx context.Context) error {
	cutoff := time.Now().Add(-transientRetention)
	expired, err := s.attachRepo.FindByStatusBefore(ctx, StatusTransient, cutoff)
	if err != nil {
		return err
	}
	if len(expired) == 0 {
		return nil
	}
	ids := make([]uint, 0, len(expired))
	for _, attachment := range expired {
		ids = append(ids, attachment.ID)
	}
	if err = s.attachRepo.DeleteByIDs(ctx, ids); err != nil {
		return err
	}
	if err = s.deleteFiles(expired); err != nil {
		log.Errorf("回收游离附件文件失败: %v", err)
	}
	log.Infof("回收游离附件 %v 个", len(ids))
	return nil
}

// deleteFiles 删除附件对应的物理文件（文件缺失时跳过）
func (s *Service) deleteFiles(attachments []Attach) error {
	ops := make([]FileOp, 0, len(attachments))
	for _, attachment := range attachments {
		targetPath, err := s.GetAbsolutePathByRela(attachment.RelativePath)
		if err != nil {
			continue
		}
		if !Exists(targetPath) {
			continue
		}
		ops = append(ops, FileOp{Type: FileOpDelete, Target: targetPath})
	}
	return Exec(ops)
}

func (s *Service) GetResource(ctx context.Context, dto *DTO) (filename string, path string, err error) {
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

func uniqueIDs(ids []uint) []uint {
	seen := make(map[uint]bool, len(ids))
	unique := make([]uint, 0, len(ids))
	for _, id := range ids {
		if id == 0 || seen[id] {
			continue
		}
		seen[id] = true
		unique = append(unique, id)
	}
	return unique
}
