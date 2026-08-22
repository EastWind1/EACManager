package pers.eastwind.billmanager.reimburse.service;

import org.springframework.stereotype.Service;
import pers.eastwind.billmanager.attach.service.AttachMapRule;
import pers.eastwind.billmanager.reimburse.model.ReimburseDetailDTO;
import pers.eastwind.billmanager.reimburse.model.ReimbursementDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 报销单映射规则 — 仅支持标准税务发票
 * <p>
 * 解析规则:
 * <ol>
 *   <li>找到"项目名称"表头行，表头之后、"合  计"之前为明细行</li>
 *   <li>每行按空白分割：第 1 项为名称，倒数第 3 项(金额) + 倒数第 1 项(税额) = 明细金额</li>
 *   <li>"价税合计"行中提取"小写）"之后的金额作为主表总金额</li>
 *   <li>"备注"之后的文本作为备注</li>
 * </ol>
 */
@Service
public class ReimburseMapRule implements AttachMapRule<ReimbursementDTO> {
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private boolean canMapTexts(List<String> texts) {
        for (String t : texts) {
            if (t.contains("发票") || t.contains("价税合计")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ReimbursementDTO mapFromTexts(List<String> texts) {
        if (!canMapTexts(texts)) {
            return null;
        }

        ReimbursementDTO dto = new ReimbursementDTO();
        dto.setSummary("发票报销");
        dto.setDetails(new ArrayList<>());

        int index = 0;

        // 解析表头
        int headerIdx = -1;
        for (int i = 0; i < texts.size(); i++) {
            String cur = texts.get(i);
            if (cur.contains("开票日期")) {
                String dateStr = cur.substring(5);
                dto.setReimburseDate(AttachMapRule.parseDateString(dateStr));
            }
            if (cur.contains("项目名称")) {
                headerIdx = i;
                index = i;
                break;
            }
        }

        // 解析明细行（表头之后、合计之前）
        if (headerIdx >= 0) {
            for (int i = headerIdx + 1; i < texts.size(); i++) {
                String line = texts.get(i).trim();
                if (line.isEmpty() || (line.contains("合") && line.contains("计"))) {
                    index = i;
                    break;
                }
                String[] tokens = SPACE.split(line);
                if (tokens.length < 4) {
                    continue;
                }
                ReimburseDetailDTO detail = new ReimburseDetailDTO();
                detail.setName(tokens[0]);
                // 倒数第 3 项（金额）+ 最后一项（税额）
                BigDecimal amount = BigDecimal.ZERO;
                try {
                    amount = amount.add(new BigDecimal(tokens[tokens.length - 3])).add(new BigDecimal(tokens[tokens.length - 1]));
                } catch (NumberFormatException ignored) {
                }
                detail.setAmount(amount);
                if (detail.getName() != null && !detail.getName().isEmpty()) {
                    dto.getDetails().add(detail);
                }
            }
        }

        // 解析总额
        for (; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text.contains("价税合计")) {
                int idx = text.indexOf("小写）");
                if (idx >= 0) {
                    String s = text.substring(idx + "小写）".length()).trim();
                    s = s.replaceAll("[¥￥]", "");
                    try {
                        dto.setTotalAmount(new BigDecimal(s));
                    } catch (NumberFormatException ignored) {
                    }
                }
                break;
            }
        }


        // 提取备注
        int remarkStart = -1;
        for (int i = index; i < texts.size(); i++) {
            String line = texts.get(i).trim();
            if ("备".equals(line) && i + 1 < texts.size() && "注".equals(texts.get(i + 1).trim())) {
                remarkStart = i + 2;
                break;
            }
        }
        if (remarkStart >= 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = remarkStart; i < texts.size(); i++) {
                String s = texts.get(i).trim();
                if (!s.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(s);
                }
            }
            dto.setRemark(sb.toString());
        }

        return dto;
    }

    @Override
    public ReimbursementDTO mapFromGrid(List<List<String>> rows) {
        return null;
    }
}
