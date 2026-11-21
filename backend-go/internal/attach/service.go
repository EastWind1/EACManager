package attach

import (
	"backend-go/config"
	"backend-go/internal/security"
	"backend-go/pkg/errs"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
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

// tempDirName 与业务 namespace 隔离（namespace 不允许包含点）。
const tempDirName = ".tmp"

// transientRetention 游离附件保留时长，超过后由 CleanTransientFiles 回收
const transientRetention = 7 * 24 * time.Hour

var namespacePattern = regexp.MustCompile(`^[a-zA-Z0-9_-]+$`)

func newService(
	cfg *config.AttachConfig,
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
	tempPath := filepath.Join(rootPath, tempDirName)
	if err := os.MkdirAll(tempPath, 0755); err != nil {
		log.Fatalf("创建临时目录失败: %v - %v", tempPath, err)
	}
	log.Infof("临时文件夹 %v", tempPath)

	return &Service{
		attachRepo: attachRepo,
		rootPath:   rootPath,
		tempPath:   tempPath,
		cfg:        cfg,
	}
}

// cleanTempDir 回收临时目录中超过一天的文件与空目录，与 Java 端一致。
func cleanTempDir(dir string) {
	const retention = 24 * time.Hour
	cutoff := time.Now().Add(-retention)
	var dirs []string
	_ = filepath.Walk(dir, func(path string, info os.FileInfo, err error) error {
		if err != nil || path == dir {
			return nil
		}
		if info.IsDir() {
			dirs = append(dirs, path)
			return nil
		}
		if info.Mode().IsRegular() && info.ModTime().Before(cutoff) {
			DeleteQuietly(path)
		}
		return nil
	})
	// 自底向上删除空目录
	for i := len(dirs) - 1; i >= 0; i-- {
		if entries, err := os.ReadDir(dirs[i]); err == nil && len(entries) == 0 {
			_ = os.Remove(dirs[i])
		}
	}
}

// CleanTempFiles 回收超过一天的临时文件与目录。
func (s *Service) CleanTempFiles() {
	cleanTempDir(s.tempPath)
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

// AttachFile 已完成访问校验的附件 DTO 与磁盘路径。
type AttachFile struct {
	Attachment *DTO
	Path       string
}

// GetPaths 批量读取附件，返回 DTO 与磁盘路径。
func (s *Service) GetPaths(ctx context.Context, ids []uint) (map[uint]AttachFile, error) {
	unique := uniqueIDs(ids)
	if len(unique) == 0 {
		return map[uint]AttachFile{}, nil
	}
	attachments, err := s.attachRepo.FindAll(ctx, "id IN ?", unique)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	if len(attachments) != len(unique) {
		return nil, errs.NewBizError("附件不存在")
	}
	files := make(map[uint]AttachFile, len(attachments))
	for i := range attachments {
		attachment := &attachments[i]
		if err = assertCanAccess(ctx, attachment); err != nil {
			return nil, err
		}
		path, err := s.getAbsolutePathByRela(attachment.RelativePath)
		if err != nil {
			return nil, err
		}
		if !Exists(path) {
			return nil, errs.NewBizError("文件不存在")
		}
		files[attachment.ID] = AttachFile{Attachment: attachment.ToDTO(), Path: path}
	}
	return files, nil
}

// GetPath 读取单个附件。
func (s *Service) GetPath(ctx context.Context, id uint) (*AttachFile, error) {
	if id == 0 {
		return nil, errs.NewBizError("附件 ID 不合法")
	}
	files, err := s.GetPaths(ctx, []uint{id})
	if err != nil {
		return nil, err
	}
	file, ok := files[id]
	if !ok {
		return nil, errs.NewBizError("附件不存在")
	}
	return &file, nil
}

// CopyTo 把附件复制到目标目录，源路径解析与重名处理都在附件模块内。
func (s *Service) CopyTo(ctx context.Context, ids []uint, targetDir string) error {
	files, err := s.GetPaths(ctx, ids)
	if err != nil {
		return err
	}
	if len(files) == 0 {
		return nil
	}
	used := make(map[string]bool, len(files))
	ops := make([]FileOp, 0, len(files))
	for _, file := range files {
		target, err := uniqueTargetPath(targetDir, used, file.Attachment.Name)
		if err != nil {
			return err
		}
		ops = append(ops, FileOp{Type: FileOpCopy, Origin: file.Path, Target: target})
	}
	return Exec(ops)
}

// ZipDirectory 打包临时目录，压缩包放在附件临时目录下。
func (s *Service) ZipDirectory(sourceDir string) (string, error) {
	return Zip(sourceDir, filepath.Join(s.tempPath, filepath.Base(sourceDir)+".zip"))
}

func uniqueTargetPath(dir string, used map[string]bool, name string) (string, error) {
	base := strings.TrimSpace(name)
	if base == "" {
		base = "attachment"
	}
	candidate := base
	for repeat := 1; ; repeat++ {
		if !used[candidate] {
			target, err := SafeJoin(dir, candidate)
			if err != nil {
				return "", err
			}
			if !Exists(target) {
				used[candidate] = true
				return target, nil
			}
		}
		candidate = fmt.Sprintf("%d-%s", repeat, base)
	}
}

func (s *Service) getAbsolutePathByRela(relativePath string) (string, error) {
	if relativePath == "" {
		return "", errs.NewBizError("相对路径为空")
	}
	return SafeJoin(s.rootPath, relativePath)
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
	if err := ValidName(fileName); err != nil {
		return nil, err
	}

	// 落盘文件名使用服务端生成的 hash；原始文件名只作为 Attach.Name 留在内存/数据库
	sum := sha256.Sum256([]byte(uuid.New().String()))
	relativePath := namespace + "/" + hex.EncodeToString(sum[:])
	targetPath, err := s.getAbsolutePathByRela(relativePath)
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
	unique := uniqueIDs(ids)
	if len(unique) == 0 {
		return nil
	}
	// 只能提交自己刚上传、且仍处于瞬时态的附件，避免把别人的临时附件绑到自己的单据
	attachments, err := s.attachRepo.FindAll(ctx, "id IN ?", unique)
	if err != nil {
		return err
	}
	if len(attachments) != len(unique) {
		return errs.NewBizError("附件不存在")
	}
	for i := range attachments {
		if attachments[i].Status != StatusTransient {
			return errs.NewBizError("附件不存在或已被提交")
		}
		if err := assertCanAccess(ctx, &attachments[i]); err != nil {
			return err
		}
	}
	affected, err := s.attachRepo.PersistByIds(ctx, unique)
	if err != nil {
		return err
	}
	if affected != int64(len(unique)) {
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
		targetPath, err := s.getAbsolutePathByRela(attachment.RelativePath)
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
	readable, err := s.GetPath(ctx, dto.ID)
	if err != nil {
		return "", "", err
	}
	filename = readable.Attachment.Name
	if filename == "" {
		filename = dto.Name
	}
	return filename, readable.Path, nil
}

// assertCanAccess 未绑定单据的瞬时附件只允许上传者本人读取：
// 否则任何拥有附件下载权限的人都能按自增 ID 遍历他人刚上传的文件。
func assertCanAccess(ctx context.Context, attachment *Attach) error {
	if attachment.Status != StatusTransient {
		return nil
	}
	user, err := security.GetCurrentUser(ctx)
	if err != nil {
		return errs.NewUnauthError("未登录")
	}
	if attachment.CreatedByID != 0 && attachment.CreatedByID != user.ID {
		return errs.NewAuthError("无权访问该附件")
	}
	return nil
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
