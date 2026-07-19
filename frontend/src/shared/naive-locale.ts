import { dateZhCN, zhCN } from 'naive-ui'
import type { NLocale } from 'naive-ui'

/** 应用中文 locale；下拉类控件空值时不显示「请选择 / Please Select」占位文案 */
export const appLocale: NLocale = {
  ...zhCN,
  Cascader: {
    ...zhCN.Cascader,
    placeholder: '',
  },
  Select: {
    ...zhCN.Select,
    placeholder: '',
  },
}

export const appDateLocale = dateZhCN
