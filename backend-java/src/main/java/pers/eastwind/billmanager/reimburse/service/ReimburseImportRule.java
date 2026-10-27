package pers.eastwind.billmanager.reimburse.service;

import pers.eastwind.billmanager.common.util.DateParseUtil;
import pers.eastwind.billmanager.reimburse.model.ReimburseDTO;
import pers.eastwind.billmanager.reimburse.model.ReimburseDetailDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 报销单映射规则，仅支持标准税务发票。
 * 明细取“项目名称”表头之后至“合  计”之前，每行按空白拆词，第 1 项为名称，倒数第 3 项（金额）
 * 加最后一项（税额）为明细金额；“价税合计”中“小写）”之后的金额为总金额，“备 注”之后为备注。
 */
public class ReimburseImportRule {
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private boolean canMapTexts(List<String> texts) {
        for (String t : texts) {
            if (t.contains("发票") || t.contains("价税合计")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从文本块映射，返回 null 表示未命中本规则。
     */
    public ReimburseDTO mapFromTexts(List<String> texts) {
        if (!canMapTexts(texts)) {
            return null;
        }

        ReimburseDTO dto = new ReimburseDTO();
        dto.setSummary("发票报销");
        dto.setDetails(new ArrayList<>());

        int index = 0;

        int headerIdx = -1;
        for (int i = 0; i < texts.size(); i++) {
            String cur = texts.get(i);
            if (cur.contains("开票日期")) {
                String dateStr = cur.substring(5);
                dto.setReimburseDate(DateParseUtil.parseDateString(dateStr));
            }
            if (cur.contains("项目名称")) {
                headerIdx = i;
                index = i;
                break;
            }
        }

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
}
