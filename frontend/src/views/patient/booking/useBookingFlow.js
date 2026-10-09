import { computed, reactive, ref, watch } from 'vue'
import { appointmentsApi } from '@/api/appointments'
import { apiErrorMessage } from '@/utils/apiErrorMessage'
import { isPastDate } from './bookingUtils'
import { useSlotHold } from './useSlotHold'

export const BOOKING_STEPS = [
  { id: 1, label: 'Dịch vụ' },
  { id: 2, label: 'Bác sĩ' },
  { id: 3, label: 'Ngày khám' },
  { id: 4, label: 'Khung giờ' },
  { id: 5, label: 'Xác nhận' }
]

export const SERVICE_STEP = 1
export const SLOT_STEP = 4
export const CONFIRM_STEP = 5
export const DONE_STEP = 6
export const NOTES_MAX = 2000
export const SLOT_TAKEN_MESSAGE = 'Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác.'
export const HOLD_EXPIRED_MESSAGE = 'Thời gian giữ chỗ đã hết. Vui lòng chọn lại khung giờ.'

/**
 * Trạng thái và quy tắc của luồng đặt lịch nhiều bước: dịch vụ → bác sĩ → ngày → khung giờ → xác nhận.
 *
 * - Dịch vụ được chọn trước vì danh sách khung giờ phụ thuộc thời lượng của dịch vụ.
 * - Quay lại các bước trước không làm mất lựa chọn đã có (dịch vụ, bác sĩ, ngày, ghi chú).
 * - Đổi dịch vụ, bác sĩ hoặc ngày thì khung giờ đã chọn bị bỏ (không còn hợp lệ) và bản giữ chỗ được nhả.
 * - Chọn khung giờ là giữ chỗ có thời hạn ({@link useSlotHold}); xác nhận gửi kèm `holdId`. Không giữ được, hết
 *   thời gian giữ, hoặc máy chủ trả 409, 410, 422 khi xác nhận: quay về bước chọn giờ với thông điệp, bỏ khung
 *   giờ cũ, yêu cầu tải lại danh sách slot, GIỮ NGUYÊN các lựa chọn còn lại.
 * - Gửi lặp bị chặn: khi đang gửi hoặc đã đặt xong, {@link submit} không làm gì.
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

  const slotHold = useSlotHold()

  const notesError = computed(() => (notes.value.length > NOTES_MAX ? `Ghi chú tối đa ${NOTES_MAX} ký tự` : ''))

  function stepProblem(target) {
    if (target === 1) return service.value ? '' : 'Vui lòng chọn dịch vụ khám'
    if (target === 2) return doctor.value ? '' : 'Vui lòng chọn bác sĩ'
    if (target === 3) {
      if (!date.value) return 'Vui lòng chọn ngày khám'
      return isPastDate(date.value) ? 'Ngày khám phải từ hôm nay trở đi' : ''
    }
    if (target === 4) {
      if (!slot.value) return 'Vui lòng chọn khung giờ'
      return slotHold.holding.value ? 'Đang giữ chỗ khung giờ đã chọn' : ''
    }
    if (target === 5) return notesError.value
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

  function clearSlot() {
    slot.value = null
    slotHold.releaseHold()
  }

  function selectService(next) {
    if (service.value?.id !== next?.id) clearSlot()
    service.value = next
  }

  function selectDoctor(next) {
    if (doctor.value?.id !== next?.id) clearSlot()
    doctor.value = next
  }

  function setDate(value) {
    if (date.value !== value) clearSlot()
    date.value = value
  }

  function backToSlots(message) {
    conflictMessage.value = message
    clearSlot()
    slotsRefreshKey.value += 1
    step.value = SLOT_STEP
  }

  /** Chọn khung giờ và giữ chỗ. Trả `true` khi đã giữ được; không giữ được thì bỏ lựa chọn và báo lý do. */
  async function selectSlot(next) {
    slot.value = next
    conflictMessage.value = ''
    const held = await slotHold.holdSlot(next.id)
    if (slot.value?.id !== next.id) return false // người dùng đã chọn khung giờ khác hoặc đổi bác sĩ, ngày
    if (!held) {
      backToSlots(slotHold.holdError.value || SLOT_TAKEN_MESSAGE)
      return false
    }
    return true
  }

  /** Chọn một dòng "giờ sớm nhất": điền bác sĩ, ngày, khung giờ, giữ chỗ rồi tới thẳng bước xác nhận. */
  async function chooseEarliest(choice) {
    if (!service.value) return false
    clearMessages()
    doctor.value = choice.doctor
    date.value = choice.date
    const held = await selectSlot(choice.slot)
    if (!held) {
      furthest.value = Math.max(furthest.value, SLOT_STEP)
      return false
    }
    furthest.value = CONFIRM_STEP
    step.value = CONFIRM_STEP
    return true
  }

  function restart() {
    step.value = 1
    furthest.value = 1
    doctor.value = null
    date.value = ''
    clearSlot()
    service.value = null
    notes.value = ''
    confirmed.value = null
    clearMessages()
  }

  // Hết thời gian giữ chỗ: bỏ khung giờ, báo cho người dùng và tải lại danh sách
  watch(slotHold.expired, (isExpired) => {
    if (!isExpired || !slot.value || confirmed.value) return
    submitError.value = ''
    slot.value = null
    conflictMessage.value = HOLD_EXPIRED_MESSAGE
    slotsRefreshKey.value += 1
    if (step.value > SLOT_STEP) step.value = SLOT_STEP
  })

  async function submit() {
    if (submitting.value || confirmed.value) return null
    const problem = [1, 2, 3, 4, 5].map(stepProblem).find(Boolean)
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
        notes: notes.value.trim() || undefined,
        holdId: slotHold.hold.value?.holdId
      })
      slotHold.forgetHold() // máy chủ đã xóa bản giữ khi tạo lịch
      step.value = DONE_STEP
      return confirmed.value
    } catch (err) {
      if (err?.status === 409 || err?.status === 410 || err?.status === 422) {
        // 409 khung giờ đã mất; 410 bản giữ chỗ hết hạn; 422 dịch vụ dài hơn khung giờ
        backToSlots(err.message || SLOT_TAKEN_MESSAGE)
      } else if (err?.status === 404) {
        backToSlots('Khung giờ không còn tồn tại. Vui lòng chọn lại.')
      } else if (err?.status === 400 && err.details && Object.keys(err.details).length) {
        Object.assign(fieldErrors, err.details)
        submitError.value = 'Vui lòng kiểm tra lại thông tin đặt lịch.'
        if (err.details.slotId) step.value = SLOT_STEP
        else if (err.details.serviceId) step.value = SERVICE_STEP
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
    hold: slotHold.hold,
    holding: slotHold.holding,
    remainingSeconds: slotHold.remainingSeconds,
    goTo,
    next,
    back,
    selectService,
    selectDoctor,
    setDate,
    selectSlot,
    clearSlot,
    chooseEarliest,
    restart,
    submit
  }
}
