<template>
  <div class="forbidden-page">
    <PageHeader title="403 无权限" subtitle="您的账号无权访问此页面。" />
    <el-card shadow="hover" class="forbidden-card">
      <el-result icon="warning" title="访问被拒绝" sub-title="如需开通权限，请联系系统管理员。">
        <template #extra>
          <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'

const router = useRouter()
</script>

<style scoped>
.forbidden-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.forbidden-card {
  border-radius: 18px;
}
</style>
