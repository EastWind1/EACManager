package model

import (
	"backend-go/internal/common/result"
	"time"
)

type ServiceBillQueryParam struct {
	Number             string             `json:"number"`
	States             []ServiceBillState `json:"states"`
	ProjectName        string             `json:"projectName"`
	OrderStartDate     *string            `json:"orderStartDate"`
	OrderEndDate       *string            `json:"orderEndDate"`
	ProcessedStartDate *string            `form:"processedStartDate"`
	ProcessedEndDate   *string            `json:"processedEndDate"`
	result.QueryParam
}

type YearMonthSumAmount struct {
	Year   int
	Month  int
	Amount float64
}

type MonthSumAmount struct {
	Month  string  `json:"month"`
	Amount float64 `json:"amount"`
}

type CountByStateResult map[ServiceBillState]int

type ProcessedParam struct {
	Ids           []uint     `json:"ids"`
	ProcessedDate *time.Time `json:"processedDate"`
}

type FinishParam struct {
	Ids          []uint     `json:"ids"`
	FinishedDate *time.Time `json:"finishedDate"`
}
