package util

import (
	"strings"
	"unicode"
)

// CamelToSnake 驼峰转下划线
func CamelToSnake(camelStr string) string {
	if camelStr == "" {
		return ""
	}
	camelStr = strings.ToLower(string(camelStr[0])) + camelStr[1:]
	snakeSlice := make([]rune, 0, len(camelStr)+4)

	for i, char := range camelStr {
		if unicode.IsUpper(char) {
			if i != 0 {
				snakeSlice = append(snakeSlice, '_')
			}
			snakeSlice = append(snakeSlice, unicode.ToLower(char))
		} else {
			snakeSlice = append(snakeSlice, char)
		}
	}
	return string(snakeSlice)
}
