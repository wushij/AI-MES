<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '暂无数据',
    description: '当前页面已完成框架搭建，等待接入后端接口或业务数据。'
  }
)
</script>

<template>
  <el-empty :description="description">
    <template #image>
      <div class="empty-icon">AI</div>
    </template>
    <template #description>
      <div class="empty-description">
        <strong>{{ title }}</strong>
        <span>{{ description }}</span>
      </div>
    </template>
  </el-empty>
</template>

<style scoped>
.empty-icon {
  width: 72px;
  height: 72px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cd-primary-gradient);
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}

.empty-description {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.empty-description strong {
  font-size: 15px;
  color: var(--cd-text-primary);
}

.empty-description span {
  color: var(--cd-text-secondary);
}
</style>
