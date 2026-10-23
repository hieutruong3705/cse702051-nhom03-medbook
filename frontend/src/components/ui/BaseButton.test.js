import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BaseButton from './BaseButton.vue'

describe('BaseButton', () => {
  it('emits click when enabled', async () => {
    const wrapper = mount(BaseButton, {
      slots: { default: 'Lưu' }
    })

    await wrapper.trigger('click')

    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('does not emit click while loading', async () => {
    const wrapper = mount(BaseButton, {
      props: { loading: true },
      slots: { default: 'Đang lưu' }
    })
    const preventDefault = vi.fn()

    await wrapper.trigger('click', { preventDefault })

    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('renders icon and loading indicator predictably', () => {
    const wrapper = mount(BaseButton, {
      props: { icon: 'fa-solid fa-plus', loading: true },
      slots: { default: 'Thêm' }
    })

    expect(wrapper.find('.fa-spinner').exists()).toBe(true)
    expect(wrapper.text()).toContain('Thêm')
  })
})
