package validation

import (
	"backend-go/pkg/errs"
	"errors"
	"fmt"
	"reflect"
	"regexp"
	"strings"

	"github.com/go-playground/validator/v10"
)

var (
	validate   = validator.New(validator.WithRequiredStructEnabled())
	phoneRegex = regexp.MustCompile(`^1[3-9]\d{9}$`)
)

func init() {
	validate.RegisterTagNameFunc(func(field reflect.StructField) string {
		if label := field.Tag.Get("label"); label != "" {
			return label
		}
		return field.Name
	})
	_ = validate.RegisterValidation("notblank", func(fl validator.FieldLevel) bool {
		return strings.TrimSpace(fl.Field().String()) != ""
	})
	_ = validate.RegisterValidation("phone", func(fl validator.FieldLevel) bool {
		value := fl.Field().String()
		return value == "" || phoneRegex.MatchString(value)
	})
}

// Struct 校验结构体字段，返回首个可展示给用户的错误。
func Struct(value any) error {
	err := validate.Struct(value)
	if err == nil {
		return nil
	}
	var fieldErrors validator.ValidationErrors
	if !errors.As(err, &fieldErrors) || len(fieldErrors) == 0 {
		return errs.NewBizError("参数校验失败")
	}
	return errs.NewBizError(message(fieldErrors[0]))
}

func message(err validator.FieldError) string {
	label := err.Field()
	switch err.Tag() {
	case "required", "notblank":
		return label + "不能为空"
	case "email":
		return label + "格式不正确"
	case "phone":
		return label + "格式不正确"
	case "min":
		if err.Kind() == reflect.String {
			return fmt.Sprintf("%s长度不能少于%s", label, err.Param())
		}
		return fmt.Sprintf("%s不能小于%s", label, err.Param())
	case "max":
		if err.Kind() == reflect.String {
			return fmt.Sprintf("%s长度不能超过%s", label, err.Param())
		}
		return fmt.Sprintf("%s不能大于%s", label, err.Param())
	default:
		return label + "格式不正确"
	}
}
