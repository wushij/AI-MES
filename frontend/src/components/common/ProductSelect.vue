<template>
  <el-select
    :model-value="modelValue"
    filterable
    clearable
    :placeholder="placeholder"
    class="full-width"
    @update:model-value="onChange"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="`${item.productName} (${item.productCode})`"
      :value="item.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getProductOptions } from '@/api/products'

const props = withDefaults(defineProps<{
  modelValue?: number | string | null
  placeholder?: string
}>(), {
  placeholder: '选择产品'
})

const emit = defineEmits<{
  'update:modelValue': [value: number | string | null | undefined]
  change: [payload: { productId?: number | string; productName: string; unit?: string }]
}>()

const options = ref<Array<{ id: number | string; productCode: string; productName: string; unit: string }>>([])

async function loadOptions() {
  try {
    options.value = await getProductOptions()
  } catch (error) {
    console.error(error)
    options.value = []
  }
}

function onChange(value: number | string | null | undefined) {
  emit('update:modelValue', value ?? undefined)
  const selected = options.value.find((item) => String(item.id) === String(value))
  emit('change', {
    productId: value ?? undefined,
    productName: selected?.productName ?? '',
    unit: selected?.unit
  })
}

onMounted(loadOptions)
</script>

<style scoped>
.full-width {
  width: 100%;
}
</style>
