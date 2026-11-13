package bill

import (
	"backend-go/pkg/result"
	"time"
)

type QueryParam struct {
	Number             string  `json:"number"`
	States             []State `json:"states"`
	ProjectName        string  `json:"projectName"`
	AssigneeID         *uint   `json:"assigneeId"`
	OrderStartDate     *string `json:"orderStartDate"`
	OrderEndDate       *string `json:"orderEndDate"`
	ProcessedStartDate *string `form:"processedStartDate"`
	ProcessedEndDate   *string `json:"processedEndDate"`
	result.QueryParam
}

type AssignParam struct {
	Ids         []uint `json:"ids"`
	AssigneeIDs []uint `json:"assigneeIds"`
}

type ProcessedParam struct {
	Ids           []uint     `json:"ids"`
	ProcessedDate *time.Time `json:"processedDate"`
}

type FinishParam struct {
	Ids          []uint     `json:"ids"`
	FinishedDate *time.Time `json:"finishedDate"`
}
