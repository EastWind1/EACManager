package user

import (
	"backend-go/internal/security"
	"backend-go/pkg/database"
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"context"
	"errors"
	"sort"

	"gorm.io/gorm"
)

type Repository struct {
	*database.BaseRepository[User]
}

func NewUserRepository(db *gorm.DB) *Repository {
	return &Repository{
		database.NewBaseRepository[User](db),
	}
}

func (r *Repository) FindByUsername(ctx context.Context, username string) (*User, error) {
	user, err := gorm.G[User](r.Ctx(ctx)).Where("username = ?", username).Take(ctx)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, errs.NewBizError("用户名或密码错误")
		}
		return nil, errs.Wrap(err)
	}
	return &user, nil
}

func (r *Repository) FindAllEnabled(ctx context.Context, pageable *result.QueryParam) (*result.PageResult[User], error) {
	return r.Page(ctx, r.Chain(ctx).Where("disabled = ?", false), pageable)
}

func (r *Repository) Create(ctx context.Context, data *User) error {
	if err := gorm.G[User](r.Ctx(ctx)).Create(ctx, data); err != nil {
		// 用户名唯一索引是最终的判重手段；并发创建时靠它兜底，不再依赖「先查后插」
		if database.IsDuplicateKey(err) {
			return errs.NewBizError("用户名已存在")
		}
		return errs.Wrap(err)
	}
	return nil
}

// UpdateProfile 更新用户资料与密码（password 为 nil 表示不修改密码）
//
// 不整行 Save：Save 会把整行旧快照回写，并发写入的其它字段（如禁用状态）会被覆盖。
// 写字路径用 *User，才能让内嵌 Audit 的钩子拿到可寻址的值。
func (r *Repository) UpdateProfile(ctx context.Context, user *User, password *string) error {
	g := gorm.G[*User](r.Ctx(ctx)).Select("name", "phone", "email")
	if password != nil {
		user.Password = *password
		g = gorm.G[*User](r.Ctx(ctx)).Select("name", "phone", "email", "password")
	}
	affected, err := g.Where("id = ?", user.ID).Updates(ctx, user)
	if err != nil {
		return errs.Wrap(err)
	}
	if affected == 0 {
		return errs.NewBizError("数据已被更改，稍后重试")
	}
	return nil
}

// Disable 禁用用户（只更新一列），返回是否命中了用户
func (r *Repository) Disable(ctx context.Context, username string) (bool, error) {
	affected, err := gorm.G[*User](r.Ctx(ctx)).Select("disabled").
		Where("username = ?", username).
		Updates(ctx, &User{Disabled: true})
	if err != nil {
		return false, errs.Wrap(err)
	}
	return affected > 0, nil
}

// FindRolesByUserIDs 批量取用户角色，供用户列表一次装配
//
// 两条泛型查询（关联 + 角色）后在内存里分组，避免 join 投影。
func (r *Repository) FindRolesByUserIDs(ctx context.Context, userIDs []uint) (map[uint][]security.Role, error) {
	res := make(map[uint][]security.Role, len(userIDs))
	if len(userIDs) == 0 {
		return res, nil
	}
	relations, err := gorm.G[security.UserRole](r.Ctx(ctx)).
		Where("user_id IN ?", userIDs).Order("user_id, id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	roleIDs := make([]uint, 0, len(relations))
	for _, relation := range relations {
		roleIDs = append(roleIDs, relation.RoleID)
	}
	roles, err := gorm.G[security.Role](r.Ctx(ctx)).
		Where("id IN ? AND disabled = false", roleIDs).Order("sort_order, id").Find(ctx)
	if err != nil {
		return nil, errs.Wrap(err)
	}
	rolesByID := make(map[uint]security.Role, len(roles))
	for _, role := range roles {
		rolesByID[role.ID] = role
	}

	rolesByUser := make(map[uint][]security.Role, len(userIDs))
	for _, relation := range relations {
		if role, ok := rolesByID[relation.RoleID]; ok {
			rolesByUser[relation.UserID] = append(rolesByUser[relation.UserID], role)
		}
	}
	for userID, userRoles := range rolesByUser {
		sort.SliceStable(userRoles, func(i, j int) bool {
			if userRoles[i].SortOrder == userRoles[j].SortOrder {
				return userRoles[i].ID < userRoles[j].ID
			}
			return userRoles[i].SortOrder < userRoles[j].SortOrder
		})
		res[userID] = userRoles
	}
	return res, nil
}
