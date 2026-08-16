package result

// CountByStateRow 状态计数行
type CountByStateRow struct {
	State int `json:"state"`
	Count int `json:"count"`
}

// CountByStateResult 状态计数结果
type CountByStateResult map[string]int

// YearMonthSumAmount 年月金额聚合行
type YearMonthSumAmount struct {
	Year   int     `json:"year"`
	Month  int     `json:"month"`
	Amount float64 `json:"amount"`
}

// MonthSumAmount 月度金额
type MonthSumAmount struct {
	Month  string  `json:"month"`
	Amount float64 `json:"amount"`
}
