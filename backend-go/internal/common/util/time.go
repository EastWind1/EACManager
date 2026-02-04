package util

import (
	"backend-go/internal/common/errs"
	"regexp"
	"strings"
	"time"
)

// ParseDateTime 转换常见日期字符串
func ParseDateTime(input string) (*time.Time, errs.StackError) {
	processed := strings.TrimSpace(input)
	if processed == "" {
		return nil, errs.NewBizError("输入日期字符串为空")
	}

	processed = strings.ReplaceAll(processed, "年", "-")
	processed = strings.ReplaceAll(processed, "月", "-")
	processed = strings.ReplaceAll(processed, "日", " ")
	processed = strings.ReplaceAll(processed, "时", ":")
	processed = strings.ReplaceAll(processed, "分", ":")
	processed = strings.ReplaceAll(processed, "秒", "")

	processed = regexp.MustCompile(`-+`).ReplaceAllString(processed, "-")
	processed = regexp.MustCompile(`:+`).ReplaceAllString(processed, ":")
	processed = strings.TrimSpace(processed)
	formats := []string{
		"2006-01-02 15:04:05",
		"2006-01-02 15:04",
		"2006-1-2 15:04:05",
		"2006-1-2 15:04",
		"2006-01-02",
		"2006-1-2",
		"2006/01/02 15:04:05",
		"2006/1/2 15:04:05",
		"2006/01/02",
		"2006/1/2",
	}

	var t time.Time
	var err error
	for _, format := range formats {
		t, err = time.Parse(format, processed)
		if err == nil {
			return &t, nil
		}
	}
	return nil, errs.NewBizError("处理时间失败")
}
