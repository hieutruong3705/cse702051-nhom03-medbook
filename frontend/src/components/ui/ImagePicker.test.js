import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ImagePicker from './ImagePicker.vue'
import SpecialtyBadge from './SpecialtyBadge.vue'

const pick = async (wrapper, file) => {
  const input = wrapper.find('[data-test="image-input"]')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('ImagePicker', () => {
  it('nhận ảnh PNG dưới 1 MB và phát ra tệp', async () => {
    const wrapper = mount(ImagePicker, { props: { id: 'avatar' } })
    const file = new File([new Uint8Array(10)], 'a.png', { type: 'image/png' })
    await pick(wrapper, file)
    expect(wrapper.emitted('update:modelValue')[0][0]).toBe(file)
    expect(wrapper.emitted('update:removed')[0][0]).toBe(false)
  })

  it('từ chối tệp không phải ảnh và ảnh quá 1 MB, có thông báo lỗi', async () => {
    const wrapper = mount(ImagePicker, { props: { id: 'avatar' } })
    await pick(wrapper, new File(['<svg/>'], 'x.svg', { type: 'image/svg+xml' }))
    expect(wrapper.text()).toContain('Chỉ nhận ảnh JPEG, PNG hoặc WebP')
    await pick(wrapper, new File([new Uint8Array(1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' }))
    expect(wrapper.text()).toContain('vượt quá 1 MB')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('đang có ảnh thì hiện ảnh đó; bấm "Gỡ ảnh" báo removed = true', async () => {
    const wrapper = mount(ImagePicker, { props: { id: 'avatar', currentUrl: '/api/v1/images/5' } })
    expect(wrapper.find('[data-test="image-preview"]').attributes('src')).toBe('/api/v1/images/5')
    await wrapper.find('[data-test="image-remove"]').trigger('click')
    expect(wrapper.emitted('update:removed')[0][0]).toBe(true)
  })
})

describe('SpecialtyBadge có ảnh', () => {
  it('chuyên khoa có imageUrl thì hiện ảnh thay biểu tượng, lỗi ảnh thì về biểu tượng', async () => {
    const wrapper = mount(SpecialtyBadge, { props: { specialty: { code: 'TM', name: 'Tim mạch', imageUrl: '/api/v1/images/7' }, variant: 'icon' } })
    expect(wrapper.find('[data-test="specialty-image"]').attributes('src')).toBe('/api/v1/images/7')
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('[data-test="specialty-icon"]').exists()).toBe(true)
  })

  it('không có ảnh thì vẫn dùng biểu tượng theo mã', () => {
    const wrapper = mount(SpecialtyBadge, { props: { specialty: { code: 'TM', name: 'Tim mạch' }, variant: 'icon' } })
    expect(wrapper.find('[data-test="specialty-icon"]').exists()).toBe(true)
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
