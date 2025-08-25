/**
 * Crypto
 */
export class CryptoTool {
  /**
   * SHA256
   * @param str 源
   * @param salt 盐
   */
  static async SHA256(str: string, salt?: string) {
    if (salt) {
      str = str + ':' + salt
    }
    const array = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(str))
    return Array.from(new Uint8Array(array))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
  }
}
