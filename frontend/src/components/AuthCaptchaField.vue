<template>
  <div class="captcha-row">
    <el-input
      :model-value="modelValue"
      placeholder="请输入验证码"
      maxlength="6"
      size="large"
      class="captcha-input"
      :prefix-icon="Key"
      @update:model-value="$emit('update:modelValue', $event)"
      @keyup.enter="$emit('enter')"
    />
    <div v-if="captchaImg" class="captcha-img-wrap" title="点击刷新" @click="$emit('refresh')">
      <img :src="captchaImg" class="captcha-img" alt="验证码" />
    </div>
    <el-skeleton v-else :rows="1" animated class="captcha-skeleton" />
  </div>
</template>

<script setup lang="ts">
import { Key } from '@element-plus/icons-vue'

defineProps<{
  modelValue: string
  captchaImg: string
}>()

defineEmits<{
  'update:modelValue': [value: string]
  refresh: []
  enter: []
}>()
</script>

<style scoped>
.captcha-row {
  display: flex;
  gap: 16px;
  align-items: center;
  width: 100%;
}

.captcha-input {
  flex: 1;
  min-width: 0;
}

.captcha-img-wrap {
  flex-shrink: 0;
  height: 50px;
  border-radius: 9999px;
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: opacity 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.captcha-img-wrap:hover {
  opacity: 0.92;
  transform: scale(1.02);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.16);
}

.captcha-img {
  display: block;
  height: 50px;
  width: auto;
}

.captcha-skeleton {
  flex-shrink: 0;
  width: 130px;
  height: 50px;
  border-radius: 9999px;
}
</style>
