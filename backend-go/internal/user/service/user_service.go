package service

import (
	"backend-go/internal/common/auth"
	"backend-go/internal/common/errs"
	"backend-go/internal/common/result"
	"backend-go/internal/user/model"
	"backend-go/internal/user/repository"
	"context"

	"golang.org/x/crypto/bcrypt"
)

// UserService 用户服务
type UserService struct {
	// userRepo 用户仓库实例
	userRepo *repository.UserRepository
	// jwtSvc JWT服务实例
	jwtSvc *JWTService
}

// NewUserService 创建用户服务实例
func NewUserService(userRepo *repository.UserRepository, jwtSvc *JWTService) *UserService {
	return &UserService{
		userRepo: userRepo,
		jwtSvc:   jwtSvc,
	}
}

// Login 用户登录
func (s *UserService) Login(ctx context.Context, username, password, subject string) (*model.LoginResult, error) {
	user, err := s.userRepo.FindByUsername(ctx, username)
	if err != nil {
		return nil, err
	}
	if !user.IsEnabled {
		return nil, errs.NewBizError("用户已禁用")
	}

	if err = bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(password)); err != nil {
		return nil, errs.NewBizError("用户名或密码错误")
	}

	token, err := s.jwtSvc.GenerateToken(username, subject)
	if err != nil {
		return nil, err
	}

	return &model.LoginResult{
		Token: token,
		User:  *user.ToDTO(),
	}, nil
}

// GetAll 获取用户列表
func (s *UserService) GetAll(ctx context.Context, queryParam *result.QueryParam) (*result.PageResult[model.UserDTO], error) {
	curUser := auth.GetCurrentUser(ctx)
	if curUser == nil {
		return nil, errs.NewUnauthError("未登录")
	}
	currentRole := curUser.GetRole()
	var res *result.PageResult[model.UserDTO]
	if currentRole == auth.RoleAdmin {
		users, err := s.userRepo.FindAllEnabled(ctx, queryParam)
		if err != nil {
			return nil, err
		}
		res = result.NewPageResultFromDB(users, model.ToDTOs)
	} else { // 非管理员只能查自己
		user, err := s.userRepo.FindByID(ctx, curUser.GetID())
		var users []model.UserDTO
		if err != nil {
			return nil, err
		}
		if user != nil {
			users = append(users, *user.ToDTO())
		}
		result.NewPageResult(&users, len(users), 0, 1)
	}
	return res, nil
}

// Create 创建用户
func (s *UserService) Create(ctx context.Context, dto *model.UserDTO) (*model.UserDTO, error) {
	if dto.Username == "" {
		return nil, errs.NewBizError("用户名不能为空")
	}
	var res *model.UserDTO
	err := s.userRepo.WithTransaction(func(r *repository.UserRepository) error {
		exists, err := r.ExistsByUsername(dto.Username)
		if err != nil {
			return err
		}
		if exists {
			return errs.NewBizError("用户名已存在")
		}

		if dto.Password == nil || *dto.Password == "" {
			return errs.NewBizError("密码不能为空")
		}
		passwordByte := []byte(*dto.Password)
		if len(passwordByte) > 72 {
			return errs.NewBizError("密码过长")
		}
		hashedPassword, err := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
		if err != nil {
			return err
		}

		newUser := dto.ToEntity()
		newUser.Password = string(hashedPassword)

		if newUser.Authority == "" {
			newUser.Authority = auth.RoleUser
		}

		if err = r.Create(ctx, newUser); err != nil {
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
func (s *UserService) Update(ctx context.Context, dto *model.UserDTO) (*model.UserDTO, error) {
	if dto.ID == 0 {
		return nil, errs.NewBizError("id 不能为空")
	}
	curUser := auth.GetCurrentUser(ctx)
	if curUser.GetRole() != auth.RoleAdmin && curUser.GetID() != dto.ID {
		return nil, errs.NewBizError("无权限修改其他用户信息")
	}
	var res *model.UserDTO
	err := s.userRepo.WithTransaction(func(r *repository.UserRepository) error {
		user, err := r.FindByID(ctx, dto.ID)
		if err != nil {
			return err
		}
		if user == nil {
			return errs.NewBizError("用户不存在")
		}
		if user.Username != dto.Username {
			return errs.NewBizError("用户名不能修改")
		}
		if dto.Password != nil {
			if *dto.Password == "" {
				return errs.NewBizError("密码不能为空")
			}
			passwordByte := []byte(*dto.Password)
			if len(passwordByte) > 72 {
				return errs.NewBizError("密码过长")
			}
			hashedPassword, err := bcrypt.GenerateFromPassword(passwordByte, bcrypt.DefaultCost)
			if err != nil {
				return err
			}

			user.Password = string(hashedPassword)
		}

		user.Name = dto.Name
		user.Phone = dto.Phone
		user.Email = dto.Email
		user.Authority = dto.Authority

		if err = r.Save(ctx, user); err != nil {
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
func (s *UserService) Disable(ctx context.Context, username string) error {
	err := s.userRepo.WithTransaction(func(r *repository.UserRepository) error {
		user, err := r.FindByUsername(ctx, username)
		if err != nil {
			return nil
		}

		user.IsEnabled = false
		return r.Save(ctx, user)
	})
	return err
}

// FindByUsername 加载用户信息
func (s *UserService) FindByUsername(ctx context.Context, username string) (*model.User, error) {
	return s.userRepo.FindByUsername(ctx, username)
}
