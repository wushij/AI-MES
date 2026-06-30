<template>
  <div class="page-shell">
    <el-card shadow="never">
      <template #header>工单详情</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工单号">WO-2024-028</el-descriptions-item>
        <el-descriptions-item label="产品">精密组件 C</el-descriptions-item>
        <el-descriptions-item label="班组">甲班</el-descriptions-item>
        <el-descriptions-item label="当前工序">装配</el-descriptions-item>
        <el-descriptions-item label="进度">65%</el-descriptions-item>
        <el-descriptions-item label="交期">2026-07-01</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>
