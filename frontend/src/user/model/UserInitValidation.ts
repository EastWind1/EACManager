/**
 * 首次管理员初始化校验规则。
 * 与后端 UserService.validateInitialization / user.validateInit 保持一致。
 */
const USERNAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]{3,31}$/
const PHONE_PATTERN = /^1[3-9]\d{9}$/
const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/

export interface UserInitForm {
  username: string
  password: string
  passwordAgain: string
  name: string
  phone: string
  email: string
}

export const USER_INIT_DEFAULT: UserInitForm = {
  username: '',
  password: '',
  passwordAgain: '',
  name: '',
  phone: '',
  email: '',
}

export type ValidationRule = (value: string) => true | string

export const usernameRule: ValidationRule = (value) =>
  USERNAME_PATTERN.test(value.trim()) ||
  '用户名必须为 4-32 位，以字母开头，仅包含字母、数字和下划线'

export const passwordRule: ValidationRule = (value) =>
  (value.length >= 8 &&
    value.length <= 72 &&
    !/\s/.test(value) &&
    /[A-Za-z]/.test(value) &&
    /\d/.test(value)) ||
  '密码必须为 8-72 位，且同时包含字母和数字，不能包含空白字符'

export const nameRule: ValidationRule = (value) =>
  (value.trim().length > 0 && value.trim().length <= 64) ||
  '姓名不能为空且不能超过 64 个字符'

export const phoneRule: ValidationRule = (value) =>
  value.trim() === '' || PHONE_PATTERN.test(value.trim()) || '手机号格式不正确'

export const emailRule: ValidationRule = (value) => {
  const email = value.trim()
  return email === '' || (email.length <= 128 && EMAIL_PATTERN.test(email)) || '邮箱格式不正确'
}

export function passwordAgainRule(password: string): ValidationRule {
  return (value) => value === password || '两次输入的密码不一致'
}
