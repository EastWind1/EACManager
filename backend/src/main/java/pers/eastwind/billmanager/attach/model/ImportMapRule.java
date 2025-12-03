package pers.eastwind.billmanager.attach.model;

/**
 * 映射规则
 *
 * @param keyword 关键词
 * @param regex   取值正则
 * @param field   属性名
 */
public record ImportMapRule(String keyword, String regex, String field) {

}