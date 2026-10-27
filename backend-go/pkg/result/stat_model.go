package result

type CountByStateRow struct {
	State int `json:"state"`
	Count int `json:"count"`
}

type CountByStateResult map[string]int

type YearMonthSumAmount struct {
	Year   int     `json:"year"`
	Month  int     `json:"month"`
	Amount float64 `json:"amount"`
}

type MonthSumAmount struct {
	Month  string  `json:"month"`
	Amount float64 `json:"amount"`
}
