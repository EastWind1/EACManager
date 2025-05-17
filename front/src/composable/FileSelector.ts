import { useUIStore } from '@/store/UIStore.ts'

/**
 * 文件选择器
 * @param accept 接受类型
 * @param multiple 是否多选
 */
export function useFileSelector(accept: string, multiple: boolean): Promise<FileList> {
  const { warning } = useUIStore()
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = accept
  input.multiple = multiple

  const promise = new Promise<FileList>((resolve, reject) => {
    input.onchange = () => {
      if (!input.files || !input.files.length) {
        reject()
        return
      }
      for (const file of input.files) {
        if (file.size > 1024 * 1024 * 50) {
          warning('文件大小不能超过50M')
          reject()
          return
        }
      }
      resolve(input.files)
    }
  }).finally(() => {
    input.remove()
  })
  input.click()
  return promise
}
