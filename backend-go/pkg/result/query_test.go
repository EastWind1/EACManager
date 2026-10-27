package result

import "testing"

func TestOffsetIsZeroBased(t *testing.T) {
	cases := []struct{ index, size, offset int }{
		{0, 25, 0}, {1, 25, 25}, {2, 25, 50},
	}
	for _, c := range cases {
		i, s := c.index, c.size
		p := &QueryParam{PageIndex: &i, PageSize: &s}
		if got := p.GetOffset(); got != c.offset {
			t.Fatalf("pageIndex=%d size=%d: want offset %d, got %d", c.index, c.size, c.offset, got)
		}
	}
}

func TestNewPageResultWithoutPaging(t *testing.T) {
	p := NewPageResult([]int{1, 2, 3}, 3, 0, -1)
	if p.PageSize != 0 || p.TotalPages != 0 {
		t.Fatalf("unpaged result should have pageSize=0 totalPages=0, got %d/%d", p.PageSize, p.TotalPages)
	}
	if p.TotalCount != 3 {
		t.Fatalf("totalCount should be kept, got %d", p.TotalCount)
	}
}
