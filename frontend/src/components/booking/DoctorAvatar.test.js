import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DoctorAvatar from './DoctorAvatar.vue'
import { avatarTraits } from './doctorAvatarTraits'

const signature = (id) => Object.values(avatarTraits(id)).join('|')

describe('avatarTraits', () => {
  it('cùng mã bác sĩ luôn cho cùng ảnh', () => {
    expect(avatarTraits(7)).toEqual(avatarTraits(7))
    expect(avatarTraits('7')).toEqual(avatarTraits(7))
  })

  it('mã khác nhau cho ảnh khác nhau (100 mã đầu không trùng)', () => {
    const all = Array.from({ length: 100 }, (_, i) => signature(i + 1))
    expect(new Set(all).size).toBe(100)
  })

  it('hai mã liền nhau khác cả màu nền', () => {
    expect(avatarTraits(1).background).not.toBe(avatarTraits(2).background)
  })

  it('mã không phải số hoặc rỗng vẫn ra ảnh hợp lệ', () => {
    for (const id of ['abc', null, undefined]) {
      const t = avatarTraits(id)
      expect(t.background).toMatch(/^#/)
      expect(['short', 'side', 'bun']).toContain(t.hairStyle)
    }
  })
})

describe('DoctorAvatar', () => {
  it('là hình trang trí: ẩn khỏi trình đọc màn hình, không nhận focus', () => {
    const svg = mount(DoctorAvatar, { props: { doctorId: 1 } }).find('svg')
    expect(svg.attributes('aria-hidden')).toBe('true')
    expect(svg.attributes('focusable')).toBe('false')
    expect(svg.attributes('data-traits')).toBe(signature(1))
  })

  it('mỗi ảnh có clipPath riêng để nhiều ảnh trên một trang không dẫm lên nhau', () => {
    const wrapper = mount({
      components: { DoctorAvatar },
      template: '<div><DoctorAvatar :doctor-id="1" /><DoctorAvatar :doctor-id="2" /></div>'
    })
    const ids = wrapper.findAll('clipPath').map((node) => node.attributes('id'))
    expect(new Set(ids).size).toBe(2)
  })
})

describe('DoctorAvatar có ảnh tải lên', () => {
  it('hiện ảnh khi có src, alt rỗng vì tên bác sĩ đã ở bên cạnh', () => {
    const wrapper = mount(DoctorAvatar, { props: { doctorId: 3, src: '/api/v1/images/9' } })
    const img = wrapper.find('[data-test="doctor-avatar-photo"]')
    expect(img.attributes('src')).toBe('/api/v1/images/9')
    expect(img.attributes('alt')).toBe('')
    expect(wrapper.find('svg').exists()).toBe(false)
  })

  it('ảnh lỗi thì quay về ảnh minh họa SVG', async () => {
    const wrapper = mount(DoctorAvatar, { props: { doctorId: 3, src: '/api/v1/images/404' } })
    await wrapper.find('img').trigger('error')
    expect(wrapper.find('[data-test="doctor-avatar"]').exists()).toBe(true)
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
