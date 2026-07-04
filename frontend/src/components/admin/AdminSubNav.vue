<script setup lang="ts">
import { useRoute } from 'vue-router'

const tabs = [
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/roles', label: '角色管理' },
  { path: '/admin/coze', label: 'Coze 配置' }
]

const route = useRoute()

function isActive(path: string) {
  return route.path === path || route.path.startsWith(`${path}/`)
}
</script>

<template>
  <nav class="admin-subnav">
    <router-link
      v-for="tab in tabs"
      :key="tab.path"
      :to="tab.path"
      class="admin-subnav__item"
      :class="{ 'admin-subnav__item--active': isActive(tab.path) }"
    >
      {{ tab.label }}
    </router-link>
  </nav>
</template>

<style scoped>
.admin-subnav {
  display: flex;
  gap: 8px;
  margin: -4px 0 0 0;
  padding-bottom: 4px;
}

.admin-subnav__item {
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: #64748b;
  text-decoration: none;
  background: #f8fafc;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}

.admin-subnav__item:hover {
  color: #4f46e5;
  background: rgba(99, 102, 241, 0.06);
}

.admin-subnav__item--active {
  color: #4f46e5;
  font-weight: 600;
  background: rgba(99, 102, 241, 0.1);
  border-color: rgba(99, 102, 241, 0.2);
}
</style>
