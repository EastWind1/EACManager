package security

import (
	"backend-go/pkg/errs"
	"backend-go/pkg/result"
	"strconv"

	"github.com/gofiber/fiber/v3"
)

type Controller struct {
	srv *Service
}

func NewController(srv *Service) *Controller {
	return &Controller{srv: srv}
}

func (c *Controller) Permissions(ctx fiber.Ctx) error {
	res, err := c.srv.Permissions(ctx)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) Roles(ctx fiber.Ctx) error {
	res, err := c.srv.Roles(ctx)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) CreateRole(ctx fiber.Ctx) error {
	var dto RoleDTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}
	res, err := c.srv.CreateRole(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) UpdateRole(ctx fiber.Ctx) error {
	var dto RoleDTO
	if err := ctx.Bind().Body(&dto); err != nil {
		return err
	}
	res, err := c.srv.UpdateRole(ctx, &dto)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) DeleteRole(ctx fiber.Ctx) error {
	id, err := roleID(ctx)
	if err != nil {
		return err
	}
	if err := c.srv.DeleteRole(ctx, id); err != nil {
		return err
	}
	return nil
}

func (c *Controller) RolePermissions(ctx fiber.Ctx) error {
	id, err := roleID(ctx)
	if err != nil {
		return err
	}
	res, err := c.srv.RolePermissions(ctx, id)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func (c *Controller) UpdateRolePermissions(ctx fiber.Ctx) error {
	id, err := roleID(ctx)
	if err != nil {
		return err
	}
	var permissionIDs []uint
	if err := ctx.Bind().Body(&permissionIDs); err != nil {
		return err
	}
	res, err := c.srv.UpdateRolePermissions(ctx, id, permissionIDs)
	if err != nil {
		return err
	}
	result.SetResult(ctx, res)
	return nil
}

func roleID(ctx fiber.Ctx) (uint, error) {
	id, err := strconv.Atoi(ctx.Params("id"))
	if err != nil {
		return 0, errs.NewBizError("角色 id 不合法")
	}
	return uint(id), nil
}
