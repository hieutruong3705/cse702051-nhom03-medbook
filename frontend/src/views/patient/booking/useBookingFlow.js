import { computed, reactive, ref } from 'vue'
import { appointmentsApi } from '@/api/appointments'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { isPastDate } from './bookingUtils'

export const BOOKING_STEPS = [
  { id: 1, label: 'Bác sĩ' },
  { id: 2, label: 'Ngày khám' },
  { id: 3, label: 'Khung giờ' },
  { id: 4, label: 'Dịch vụ' },
  { id: 5, label: 'Xác nhận' }
]

export const DONE_STEP = 6
export const NOTES_MAX = 2000
export const SLOT_TAKEN_MESSAGE = 'Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác.'

/**
 * Trạng thái và quy tắc của luồng đặt lịch nhiều bước.
 *
 * - Quay lại các bước trước không làm mất lựa chọn đã có (bác sĩ, ngày, dịch vụ, ghi chú).
 * - Đổi bác sĩ hoặc ngày thì khung giờ đã chọn bị bỏ (không còn hợp lệ).
 * - Gửi lặp bị chặn: khi đang gửi hoặc đã đặt xong, {@link submit} không làm gì.
 * - Lỗi 409 (khung giờ vừa bị người khác đặt): quay về bước chọn giờ, bỏ khung giờ cũ, yêu cầu tải lại danh
 *   sách slot, GIỮ NGUYÊN các lựa chọn còn lại.
 */
export function useBookingFlow() {
  const step = ref(1)
  const furthest = ref(1)
  const doctor = ref(null)
  const date = ref('')
  const slot = ref(null)
  const service = ref(null)
  const notes = ref('')

  const submitting = ref(false)
  const submitError = ref('')
  const conflictMessage = ref('')
  const fieldErrors = reactive({})
  const confirmed = ref(null)
  const slotsRefreshKey = ref(0)

  const notesError = computed(() => (notes.value.length > NOTES_MAX ? `Ghi chú tối đa ${NOTES_MAX} ký tự` : ''))

  function stepProblem(target) {
    if (target === 1) return doctor.value ? '' : 'Vui lòng chọn bác sĩ'
    if (target === 2) {
      if (!date.value) return 'Vui lòng chọn ngày khám'
      return isPastDate(date.value) ? 'Ngày khám phải từ hôm nay trở đi' : ''
    }
    if (target === 3) return slot.value ? '' : 'Vui lòng chọn khung giờ'
    if (target === 4) {
      if (!service.value) return 'Vui lòng chọn dịch vụ khám'
      return notesError.value
    }
    return ''
  }

  const currentProblem = computed(() => stepProblem(step.value))
  const canContinue = computed(() => step.value <= 5 && !currentProblem.value)

  function clearMessages() {
    conflictMessage.value = ''
    submitError.value = ''
    Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  }

  function goTo(target) {
    if (target < 1 || target > 5 || target > furthest.value) return
    step.value = target
  }

  function next() {
    if (!canContinue.value || step.value >= 5) return false
    clearMessages()
    step.value += 1
    furthest.value = Math.max(furthest.value, step.value)
    return true
  }

  function back() {
    if (step.value <= 1 || step.value > 5) return
    clearMessages()
    step.value -= 1
  }

  function selectDoctor(next) {
    if (doctor.value?.id !== next?.id) slot.value = null
    doctor.value = next
  }

  function setDate(value) {
    if (date.value !== value) slot.value = null
    date.value = value
  }

  function selectSlot(next) {
    slot.value = next
    conflictMessage.value = ''
  }

  function restart() {
    step.value = 1
    furthest.value = 1
    doctor.value = null
    date.value = ''
    slot.value = null
    service.value = null
    notes.value = ''
    confirmed.value = null
    clearMessages()
  }

  function backToSlots(message) {
    conflictMessage.value = message
    slot.value = null
    slotsRefreshKey.value += 1
    step.value = 3
  }

  async function submit() {
    if (submitting.value || confirmed.value) return null
    const problem = [1, 2, 3, 4].map(stepProblem).find(Boolean)
    if (problem) {
      submitError.value = problem
      return null
    }

    clearMessages()
    submitting.value = true
    try {
      confirmed.value = await appointmentsApi.create({
        slotId: slot.value.id,
        serviceId: service.value.id,
        notes: notes.value.trim() || undefined
      })
      step.value = DONE_STEP
      return confirmed.value
    } catch (err) {
      if (err?.status === 409) {
        backToSlots(err.message || SLOT_TAKEN_MESSAGE)
      } else if (err?.status === 404) {
        backToSlots('Khung giờ không còn tồn tại. Vui lòng chọn lại.')
      } else if (err?.status === 400 && err.details && Object.keys(err.details).length) {
        Object.assign(fieldErrors, err.details)
        submitError.value = 'Vui lòng kiểm tra lại thông tin đặt lịch.'
        if (err.details.slotId) step.value = 3
        else if (err.details.serviceId || err.details.notes) step.value = 4
      } else {
        submitError.value = apiErrorMessage(err)
      }
      return null
    } finally {
      submitting.value = false
    }
  }

  return {
    step,
    furthest,
    doctor,
    date,
    slot,
    service,
    notes,
    notesError,
    submitting,
    submitError,
    conflictMessage,
    fieldErrors,
    confirmed,
    slotsRefreshKey,
    currentProblem,
    canContinue,
    goTo,
    next,
    back,
    selectDoctor,
    setDate,
    selectSlot,
    restart,
    submit
  }
}
