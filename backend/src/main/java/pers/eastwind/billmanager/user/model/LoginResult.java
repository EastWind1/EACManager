package pers.eastwind.billmanager.user.model;

/**
 * 登录结果
 *
 * @param token token
 * @param user  用户信息
 */
public record LoginResult(String token, UserDTO user) {
}
