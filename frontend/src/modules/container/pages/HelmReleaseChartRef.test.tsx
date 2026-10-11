import { describe, expect, it } from 'vitest'
import { presentField } from '@/modules/container/pages/HelmReleaseDetailPage'

describe('helm release chartRef display', () => {
  it('shows a dash for an empty chartRef and keeps a real reference', () => {
    expect(presentField('')).toBe('—')
    expect(presentField('   ')).toBe('—')
    expect(presentField(null)).toBe('—')
    expect(presentField('oci://harbor.example/charts/nginx:1.2.0')).toBe('oci://harbor.example/charts/nginx:1.2.0')
  })
})
