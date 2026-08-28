export interface DocgenRequest {
  format: DocgenFormat
  filename: string
  data: Record<string, any>
}

export type DocgenFormat = 'word' | 'excel' | 'ppt' | 'image' | 'md' | 'pdf'

export const FORMAT_OPTIONS: { value: DocgenFormat; label: string; ext: string; icon: string }[] = [
  { value: 'word',  label: 'Word',  ext: '.docx', icon: '📄' },
  { value: 'excel', label: 'Excel', ext: '.xlsx', icon: '📊' },
  { value: 'ppt',   label: 'PPT',   ext: '.pptx', icon: '📑' },
  { value: 'image', label: '图片',  ext: '.png',  icon: '🖼️' },
  { value: 'md',    label: 'Markdown', ext: '.md', icon: '📝' },
  { value: 'pdf',   label: 'PDF',   ext: '.pdf',  icon: '📕' },
]

export const FORMAT_MAP = Object.fromEntries(
  FORMAT_OPTIONS.map(f => [f.value, f])
) as Record<DocgenFormat, typeof FORMAT_OPTIONS[0]>

/** 默认数据模板 */
export const DEFAULT_DATA_TEMPLATES: Record<DocgenFormat, Record<string, any>> = {
  word: {
    title: '示例文档',
    paragraphs: ['这是自动生成的 Word 文档。'],
    table: { headers: ['项目', '状态'], rows: [['模块A', '已完成']] },
  },
  excel: {
    mock: true,
    columns: ['姓名', '年龄', '部门', '薪资', '手机号', '邮箱', '入职日期', '状态'],
    row_count: 20,
    sheet_name: 'Sheet1',
    rows: [['姓名', '年龄', '部门'], ['张三', 28, '技术部']],
  },
  ppt: {
    title: '演示文稿',
    subtitle: '自动生成',
    slides: [{ title: '第一页', items: ['内容1', '内容2'] }],
  },
  image: {
    chart_type: 'bar',
    title: '柱状图示例',
    categories: ['1月', '2月', '3月'],
    values: [100, 150, 130],
  },
  md: {
    title: '示例文档',
    date: new Date().toISOString().slice(0, 10),
    author: '系统',
    sections: [
      { heading: '第一章', content: '这是第一章的内容。' },
    ],
  },
  pdf: {
    title: '示例文档',
    content: ['这是第一段。', '这是第二段。'],
  },
}