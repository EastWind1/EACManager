package user

import (
	"backend-go/pkg/auth"
	errs2 "backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"

	"golang.org/x/crypto/bcrypt"
)

// Service 用户服务
type Service struct {
	// userRepo 用户仓库实例
	userRepo *Repository
	// jwtSvc JWT服务实例
	jwtSvc *JWTService
}

// NewService 创建用户服务实例
func NewService(userRepo *Repository, jwtSvc *JWTService) *Service {
	return &Service{
		userRepo: userRepo,
		jwtSvc:   jwtSvc,
	}
}

// Login 用户登录
func (s *Service) Login(ctx context.Context, username, password, subject string) (*LoginResult, error) {
	user, err := s.userRepo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if user.Disabled {
		return nil, errs2.NewBizError("用户已禁用")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return nil, errs2.NewBizError("用户名或密码错误")
	}

	token, err := s.jwtSvc.GenerateToken(username, subject)
	if err != nil {
		return nil, err
	}

	return &LoginResult{
		Token: token,
		User:  *user.ToDTO(),
	}, nil
}

// GetAll 获取用户列表
func (s *Service) GetAll(ctx context.Context, queryParam *result.QueryParam) (*result.PageResult[DTO], error) {
	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	currentRole := curUser.GetRole()
	var res *result.PageResult[DTO]
	if currentRole == auth.RoleAdmin {
		users, err := s.userRepo.FindAllEnabled(ctx, queryParam)
		if err != nil {
			return nil, err
		}
		res = result.NewDTOPageResult(users, ToDTOs)
	} else { // 非管理员只能查自己
		user, err := s.userRepo.FindByID(ctx, curUser.GetID())
		var users []DTO
		if err != nil {
			return nil, err
		}
		if user != nil {
			users = append(users, *user.ToDTO())
		}
		res = result.NewPageResult(users, len(users), 0, 1)
	}
	return res, nil
}

// Create 创建用户
func (s *Service) Create(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.Username == "" {
		return nil, errs2.NewBizError("用户名不能为空")
	}
	var res *DTO
	err := s.userRepo.Transaction(ctx, func(tx context.Context) error {
		exists, err := s.userRepo.ExistsByUsername(tx, dto.Username)
		if err != nil {
			return err
		}
		if exists {
			return errs2.NewBizError("用户名已存在")
		}

		if dto.Password == nil || *dto.Password == "" {
			return errs2.NewBizError("密码不能为空")
		}
		passwordByte := []byte(*dto.Password)
		if len(passwordByte) > 72 {
			return errs2.NewBizError("密码过长")
		}
		hashedPassword, e := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
		if e != nil {
			return errs2.Wrap(e)
		}

		newUser := dto.ToEntity()
		newUser.Password = string(hashedPassword)

		if newUser.Authority == "" {
			newUser.Authority = auth.RoleUser
		}

		if err = s.userRepo.Create(tx, newUser); err != nil {
			return err
		}
		res = newUser.ToDTO()

		return nil
	})
	if err != nil {
		return nil, err
	}

	return res, nil
}

// Update 更新用户
func (s *Service) Update(ctx context.Context, dto *DTO) (*DTO, error) {
	if dto.ID == 0 {
		return nil, errs2.NewBizError("id 不能为空")
	}
	curUser, err := auth.GetCurrentUser(ctx)
	if err != nil {
		return nil, err
	}
	if curUser.GetRole() != auth.RoleAdmin && curUser.GetID() != dto.ID {
		return nil, errs2.NewAuthError("无权限修改其他用户信息")
	}
	var res *DTO
	err = s.userRepo.Transaction(ctx, func(tx context.Context) error {
		user, err := s.userRepo.FindByID(tx, dto.ID)
		if err != nil {
			return err
		}
		if user == nil {
			return errs2.NewBizError("用户不存在")
		}
		if user.Username != dto.Username {
			return errs2.NewBizError("用户名不能修改")
		}
		if dto.Password != nil {
			if *dto.Password == "" {
				return errs2.NewBizError("密码不能为空")
			}
			passwordByte := []byte(*dto.Password)
			if len(passwordByte) > 72 {
				return errs2.NewBizError("密码过长")
			}
			hashedPassword, err := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
			if err != nil {
				return errs2.Wrap(err)
			}

			user.Password = string(hashedPassword)
		}

		user.Name = dto.Name
		user.Phone = dto.Phone
		user.Email = dto.Email
		user.Authority = dto.Authority

		if err = s.userRepo.Updates(tx, user); err != nil {
			return err
		}
		res = user.ToDTO()
		return nil
	})

	if err != nil {
		return nil, err
	}
	return res, nil
}

// Disable 禁用用户
func (s *Service) Disable(ctx context.Context, username string) error {
	err := s.userRepo.Transaction(ctx, func(tx context.Context) error {
		user, err := s.userRepo.FindByUsername(tx, username)
		if err != nil {
			return err
		}

		user.Disabled = true
		return s.userRepo.Updates(tx, user)
	})
	return err
}

// FindByUsername 加载用户信息
func (s *Service) FindByUsername(ctx context.Context, username string) (*User, error) {
	return s.userRepo.FindByUsername(ctx, username)
}
