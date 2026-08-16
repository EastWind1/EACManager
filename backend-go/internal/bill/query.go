package bill

import (
	"backend-go/pkg/result"
	"time"
)

type ServiceBillQueryParam struct {
	Number             string  `json:"number"`
	States             []State `json:"states"`
	ProjectName        string  `json:"projectName"`
	OrderStartDate     *string `json:"orderStartDate"`
	OrderEndDate       *string `json:"orderEndDate"`
	ProcessedStartDate *string `form:"processedStartDate"`
	ProcessedEndDate   *string `json:"processedEndDate"`
	result.QueryParam
}

type ProcessedParam struct {
	Ids           []uint     `json:"ids"`
	ProcessedDate *time.Time `json:"processedDate"`
}

type FinishParam struct {
	Ids          []uint     `json:"ids"`
	FinishedDate *time.Time `json:"finishedDate"`
}
