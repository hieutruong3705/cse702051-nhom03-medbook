import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FormModal from './FormModal.vue'

describe('FormModal', () => {
  it('closes when Escape is pressed', async () => {
    const wrapper = mount(FormModal, {
      attachTo: document.body,
      props: {
        modelValue: true,
        title: 'Modal thử'
      },
      slots: {
        default: '<p>Nội dung</p>'
      }
    })

    const dialog = document.body.querySelector('[role="dialog"]')
    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    wrapper.unmount()
  })
})
