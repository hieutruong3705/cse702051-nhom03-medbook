import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SpecialtyBadge from '@/components/ui/SpecialtyBadge.vue'
import { DEFAULT_THEME, normalizeName, specialtyTheme } from './specialtyTheme'

describe('specialtyTheme', () => {
  it('ánh xạ mã chuyên khoa đã biết sang biểu tượng và màu riêng', () => {
    expect(specialtyTheme({ code: 'TM', name: 'Tim mạch' }).icon).toBe('fa-heart-pulse')
    expect(specialtyTheme({ code: 'nk' }).icon).toBe('fa-baby')
    expect(specialtyTheme('TMH').text).toBe('text-violet-800')
  })

  it('tra theo tên khi không có mã (DTO bác sĩ chỉ có specialtyName)', () => {
    expect(specialtyTheme({ specialtyName: 'Da liễu' }).icon).toBe('fa-hand-dots')
    expect(specialtyTheme('  Nội   tổng quát ').icon).toBe('fa-stethoscope')
  })

  it('mã hoặc tên lạ, giá trị rỗng dùng chủ đề mặc định', () => {
    expect(specialtyTheme({ code: 'XYZ', name: 'Chuyên khoa mới' })).toBe(DEFAULT_THEME)
    expect(specialtyTheme(null)).toBe(DEFAULT_THEME)
    expect(specialtyTheme('')).toBe(DEFAULT_THEME)
  })

  it('mỗi chuyên khoa mẫu có một biểu tượng khác nhau', () => {
    const icons = ['NTQ', 'NK', 'DL', 'TM', 'TMH'].map((code) => specialtyTheme(code).icon)
    expect(new Set(icons).size).toBe(icons.length)
  })

  it('bỏ dấu tiếng Việt khi chuẩn hóa tên', () => {
    expect(normalizeName('Răng Hàm Mặt')).toBe('rang ham mat')
    expect(normalizeName('Đa khoa')).toBe('da khoa')
  })
})

describe('SpecialtyBadge', () => {
  it('nhãn chữ có tên chuyên khoa, biểu tượng ẩn với trình đọc màn hình', () => {
    const wrapper = mount(SpecialtyBadge, { props: { specialty: { specialtyName: 'Tim mạch' } } })
    expect(wrapper.text()).toBe('Tim mạch')
    expect(wrapper.find('i').attributes('aria-hidden')).toBe('true')
    expect(wrapper.classes()).toContain('text-red-800')
  })

  it('ô biểu tượng là trang trí, ẩn khỏi cây trợ năng', () => {
    const wrapper = mount(SpecialtyBadge, { props: { specialty: { code: 'NK' }, variant: 'icon' } })
    expect(wrapper.attributes('aria-hidden')).toBe('true')
    expect(wrapper.find('i').classes()).toContain('fa-baby')
  })

  it('không có chuyên khoa thì ghi rõ', () => {
    expect(mount(SpecialtyBadge).text()).toBe('Chưa có chuyên khoa')
  })
})
