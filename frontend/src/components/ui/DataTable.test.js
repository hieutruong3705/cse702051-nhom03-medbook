import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import DataTable from './DataTable.vue'

const columns = [
  { key: 'name', label: 'Tên' },
  { key: 'status', label: 'Trạng thái' }
]

describe('DataTable', () => {
  it('renders rows', () => {
    const wrapper = mount(DataTable, {
      props: {
        columns,
        rows: [{ id: 1, name: 'Khám tổng quát', status: 'ACTIVE' }]
      }
    })

    expect(wrapper.text()).toContain('Khám tổng quát')
    expect(wrapper.text()).toContain('ACTIVE')
  })

  it('renders loading state', () => {
    const wrapper = mount(DataTable, {
      props: { columns, loading: true, skeletonRows: 2 }
    })

    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
  })

  it('renders empty state', () => {
    const wrapper = mount(DataTable, {
      props: { columns, rows: [], emptyText: 'Trống' }
    })

    expect(wrapper.text()).toContain('Trống')
  })

  it('renders error state', () => {
    const wrapper = mount(DataTable, {
      props: { columns, error: 'Không tải được dữ liệu' }
    })

    expect(wrapper.text()).toContain('Không tải được dữ liệu')
  })
})
