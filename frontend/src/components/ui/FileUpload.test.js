import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FileUpload from './FileUpload.vue'

const selectFile = async (wrapper, file) => {
  const input = wrapper.find('input[type="file"]')
  Object.defineProperty(input.element, 'files', {
    configurable: true,
    value: [file]
  })
  await input.trigger('change')
}

describe('FileUpload', () => {
  it('accepts PDF/JPG/PNG files up to 10 MB', async () => {
    const wrapper = mount(FileUpload, {
      props: { id: 'result-file' }
    })
    const file = new File(['content'], 'result.pdf', { type: 'application/pdf' })

    await selectFile(wrapper, file)

    expect(wrapper.emitted('update:file')?.[0]).toEqual([file])
  })

  it('rejects unsupported file types', async () => {
    const wrapper = mount(FileUpload, {
      props: { id: 'result-file' }
    })
    const file = new File(['content'], 'result.txt', { type: 'text/plain' })

    await selectFile(wrapper, file)

    expect(wrapper.emitted('invalid')?.[0]?.[0]).toContain('Chỉ hỗ trợ')
    expect(wrapper.emitted('update:file')?.[0]).toEqual([null])
  })

  it('rejects files larger than maxBytes', async () => {
    const wrapper = mount(FileUpload, {
      props: { id: 'result-file', maxBytes: 4 }
    })
    const file = new File(['too large'], 'result.png', { type: 'image/png' })

    await selectFile(wrapper, file)

    expect(wrapper.emitted('invalid')?.[0]?.[0]).toContain('vượt quá')
    expect(wrapper.emitted('update:file')?.[0]).toEqual([null])
  })
})
