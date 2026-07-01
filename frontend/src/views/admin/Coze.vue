<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Setting, CircleCheckFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import AdminSubNav from '@/components/admin/AdminSubNav.vue'
import { getCozeConfig, saveCozeConfig, testCozeChatHealth, testCozeWorkflowHealth } from '@/api/coze'
import type { CozeConfig, CozeHealthCheckItem, CozeHealthResult } from '@/types'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const tokenEditing = ref(false)
const healthResult = ref<CozeHealthResult | null>(null)

function healthItemType(status?: string) {
  if (status === 'ok') return 'success'
  if (status === 'pending') return 'info'
  if (status === 'skipped') return 'info'
  return 'error'
}

function resolveOverallStatus(
  chat?: CozeHealthResult['chat'],
  workflow?: CozeHealthResult['workflow']
) {
  const chatStatus = chat?.status ?? 'error'
  const workflowStatus = workflow?.status ?? 'error'
  if (chatStatus === 'pending' || workflowStatus === 'pending') {
    return 'partial'
  }
  if (chatStatus !== 'ok') return 'error'
  if (workflowStatus === 'error') return 'partial'
  if (workflowStatus === 'ok') return 'ok'
  return 'partial'
}

function formatTestError(error: unknown) {
  if (error instanceof Error && error.message.includes('timeout')) {
    return '请求超时，请确认前端 dev 服务已重启且后端正常运行'
  }
  if (error instanceof Error) return error.message
  return '请求失败'
}

function createFailedHealthItem(message: string): CozeHealthCheckItem {
  return { status: 'error', message }
}

function buildHealthMessage(
  chat?: CozeHealthResult['chat'],
  workflow?: CozeHealthResult['workflow']
) {
  return `Bot 对话：${chat?.status ?? 'unknown'}；排产工作流：${workflow?.status ?? 'unknown'}`
}

function overallHealthType(result: CozeHealthResult) {
  if (result.status === 'ok') return 'success'
  if (result.status === 'partial' || result.status === 'mock') return 'warning'
  return 'error'
}

const meta = reactive({
  hasApiToken: false,
  apiTokenMasked: '',
  configured: false,
  updateTime: ''
})

const form = reactive({
  apiToken: '',
  botId: '',
  apiUrl: 'https://api.coze.cn/v3',
  workflowId: '',
  welcomeMessage: '',
  enabled: true
})

async function loadConfig() {
  loading.value = true
  try {
    const data: CozeConfig = await getCozeConfig()
    form.botId = data.botId ?? ''
    form.apiUrl = data.apiUrl || 'https://api.coze.cn/v3'
    form.workflowId = data.workflowId ?? ''
    form.welcomeMessage = data.welcomeMessage ?? ''
    form.enabled = data.enabled ?? true
    form.apiToken = ''
    tokenEditing.value = false

    meta.hasApiToken = Boolean(data.hasApiToken)
    meta.apiTokenMasked = data.apiTokenMasked ?? ''
    meta.configured = Boolean(data.configured)
    meta.updateTime = data.updateTime ? String(data.updateTime) : ''
  } catch {
    ElMessage.error('加载 Coze 配置失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  if (!form.botId.trim()) {
    ElMessage.warning('请填写 Bot ID')
    return
  }
  if (!form.apiUrl.trim()) {
    ElMessage.warning('请填写 API 地址')
    return
  }
  if (!meta.hasApiToken && !form.apiToken.trim()) {
    ElMessage.warning('请填写 API Token')
    return
  }

  saving.value = true
  try {
    await saveCozeConfig({
      apiToken: form.apiToken.trim() || undefined,
      botId: form.botId.trim(),
      apiUrl: form.apiUrl.trim(),
      workflowId: form.workflowId.trim() || undefined,
      welcomeMessage: form.welcomeMessage.trim() || undefined,
      enabled: form.enabled
    })
    ElMessage.success('Coze 配置已保存')
    healthResult.value = null
    await loadConfig()
  } catch {
    ElMessage.error('保存失败，请检查填写内容')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  healthResult.value = {
    configured: meta.configured,
    enabled: form.enabled,
    apiUrl: form.apiUrl,
    status: 'partial',
    message: '测试进行中…',
    chat: { status: 'pending', message: 'Bot 对话测试中…' },
    workflow: { status: 'pending', message: '排产工作流测试中，约需 30～90 秒…' }
  }

  const chatTask: Promise<CozeHealthCheckItem> = testCozeChatHealth()
    .then((chat) => {
      if (healthResult.value) {
        healthResult.value.chat = chat
        healthResult.value.message = buildHealthMessage(chat, healthResult.value.workflow)
      }
      return chat
    })
    .catch((error) => {
      console.error('[Coze] Bot 测试失败', error)
      const failed = createFailedHealthItem(`Bot 对话测试失败：${formatTestError(error)}`)
      if (healthResult.value) {
        healthResult.value.chat = failed
        healthResult.value.message = buildHealthMessage(failed, healthResult.value.workflow)
      }
      return failed
    })

  const workflowTask: Promise<CozeHealthCheckItem> = testCozeWorkflowHealth()
    .then((workflow) => {
      if (healthResult.value) {
        healthResult.value.workflow = workflow
        healthResult.value.message = buildHealthMessage(healthResult.value.chat, workflow)
      }
      return workflow
    })
    .catch((error) => {
      console.error('[Coze] 工作流测试失败', error)
      const failed = createFailedHealthItem(`排产工作流测试失败：${formatTestError(error)}`)
      if (healthResult.value) {
        healthResult.value.workflow = failed
        healthResult.value.message = buildHealthMessage(healthResult.value.chat, failed)
      }
      return failed
    })

  try {
    const [chat, workflow] = await Promise.all([chatTask, workflowTask])
    if (!healthResult.value) return
    healthResult.value.status = resolveOverallStatus(chat, workflow)
    healthResult.value.message = buildHealthMessage(chat, workflow)

    if (healthResult.value.status === 'ok') {
      ElMessage.success('Bot 与工作流检测均通过')
    } else if (chat?.status === 'ok' || workflow?.status === 'ok') {
      ElMessage.warning('部分测试通过，请查看下方详细结果')
    } else {
      ElMessage.error('连接测试失败，请查看下方详细结果')
    }
  } finally {
    testing.value = false
  }
}

function startTokenEdit() {
  tokenEditing.value = true
  form.apiToken = ''
}

function onTokenBlur() {
  if (meta.hasApiToken && !form.apiToken.trim()) {
    tokenEditing.value = false
  }
}

function workflowResultDescription(workflow: NonNullable<CozeHealthResult['workflow']>) {
  const parts = [workflow.message]
  if (workflow.summary) {
    parts.push(
      `返回条目：优先级 ${workflow.summary.priorities} 条，瓶颈 ${workflow.summary.bottlenecks} 条，派工 ${workflow.summary.dispatches} 条`
    )
  }
  return parts.join('；')
}

onMounted(loadConfig)
</script>

<template>
  <div class="view-page" v-loading="loading">
    <PageHeader title="系统配置" subtitle="管理系统对接 Coze Bot 及相关工作流设置。" />
    <AdminSubNav />

    <div class="config-container">
      <!-- Top Connection Status Banner -->
      <div class="status-banner" :class="meta.configured ? 'status-banner--ready' : 'status-banner--demo'">
        <div class="status-banner__left">
          <el-icon class="status-icon">
            <CircleCheckFilled v-if="meta.configured" />
            <CircleCloseFilled v-else />
          </el-icon>
          <div class="status-info">
            <div class="status-title">
              {{ meta.configured ? 'Coze AI 服务已启用' : '演示模式（AI 功能未配置）' }}
            </div>
            <div class="status-desc">
              {{ meta.configured ? `配置已生效，最近同步时间：${meta.updateTime || '刚刚'}` : '请输入您的 Bot ID 及 API Token 以启用智能排产与 AI 客服功能。' }}
            </div>
          </div>
        </div>
        <div class="status-banner__right" v-if="healthResult">
          <el-tag :type="overallHealthType(healthResult) === 'success' ? 'success' : overallHealthType(healthResult) === 'warning' ? 'warning' : 'danger'" effect="dark" class="result-tag">
            综合结果: {{ healthResult.status === 'ok' ? '全部通过' : healthResult.status === 'partial' ? '部分通过' : healthResult.status === 'mock' ? '未配置' : '失败' }}
          </el-tag>
        </div>
      </div>

      <el-form
        :model="form"
        label-position="top"
        class="custom-config-form"
      >
        <!-- Card 1: API 连接 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-card-header">
              <el-icon><Connection /></el-icon>
              <span>API 连接配置</span>
            </div>
          </template>

          <el-form-item label="API Token (Access Token)" required>
            <div class="token-field">
              <el-input
                v-if="meta.hasApiToken && !tokenEditing"
                :model-value="meta.apiTokenMasked"
                readonly
                class="custom-input custom-input--token-saved"
                @focus="startTokenEdit"
              >
                <template #suffix>
                  <el-button link class="token-change-btn" @click="startTokenEdit">更换 Token</el-button>
                </template>
              </el-input>
              <el-input
                v-else
                v-model="form.apiToken"
                type="password"
                show-password
                clearable
                autocomplete="new-password"
                :placeholder="meta.hasApiToken ? '请输入新 Access Token' : '请输入 Coze 个人访问令牌 (Personal Access Token)'"
                class="custom-input"
                @blur="onTokenBlur"
              />
            </div>
          </el-form-item>

          <div class="form-grid-2">
            <el-form-item label="Bot ID" required>
              <el-input
                v-model="form.botId"
                placeholder="请输入对接的 Coze Bot ID"
                clearable
                class="custom-input"
              />
            </el-form-item>
            <el-form-item label="API 服务地址" required>
              <el-input
                v-model="form.apiUrl"
                placeholder="https://api.coze.cn/v3"
                clearable
                class="custom-input"
              />
            </el-form-item>
          </div>
        </el-card>

        <!-- Card 2: 业务配置 -->
        <el-card shadow="hover" class="section-card">
          <template #header>
            <div class="section-card-header">
              <el-icon><Setting /></el-icon>
              <span>业务对接与 AI 设置</span>
            </div>
          </template>

          <div class="form-grid-2">
            <el-form-item label="智能排产工作流 ID">
              <el-input
                v-model="form.workflowId"
                placeholder="填写后，AI 排产模块将调用该 Coze 工作流进行运算"
                clearable
                class="custom-input"
              />
            </el-form-item>
          </div>

          <el-form-item label="智能助理欢迎语">
            <el-input
              v-model="form.welcomeMessage"
              type="textarea"
              :rows="3"
              placeholder="请输入用户首次打开 AI 助手聊天面板时展示的问候欢迎语..."
              class="custom-textarea"
            />
          </el-form-item>

          <div class="switch-row-card">
            <div class="switch-row-card__info">
              <span class="switch-row-card__title">启用 AI 增强功能</span>
              <span class="switch-row-card__desc">关闭后系统中的 AI 排产和智能客服助手将回退为演示/本地常规逻辑。</span>
            </div>
            <el-switch v-model="form.enabled" class="custom-switch" />
          </div>
        </el-card>

        <!-- Form Actions -->
        <div class="form-actions-row">
          <el-button type="primary" :loading="saving" class="btn-submit" @click="handleSave">
            保存配置
          </el-button>
          <el-button :loading="testing" class="btn-test" @click="handleTest">
            测试 Bot / 工作流
          </el-button>
          <span v-if="testing" class="test-hint">Bot 约 10 秒内出结果，工作流约 30～90 秒；两项并行测试中…</span>
        </div>
      </el-form>

      <!-- Testing alert results -->
      <div v-if="healthResult" class="health-result-panel">
        <el-alert
          :type="overallHealthType(healthResult)"
          :title="healthResult.message"
          show-icon
          :closable="false"
          class="health-result-alert"
        />
        <el-alert
          v-if="healthResult.chat"
          :type="healthItemType(healthResult.chat.status)"
          :title="`Bot 对话：${healthResult.chat.status === 'ok' ? '成功' : healthResult.chat.status === 'pending' ? '测试中' : healthResult.chat.status === 'skipped' ? '跳过' : '失败'}`"
          :description="healthResult.chat.message"
          show-icon
          :closable="false"
          class="health-result-alert"
        />
        <el-alert
          v-if="healthResult.workflow"
          :type="healthItemType(healthResult.workflow.status)"
          :title="`排产工作流：${healthResult.workflow.status === 'ok' ? '成功' : healthResult.workflow.status === 'pending' ? '测试中' : healthResult.workflow.status === 'skipped' ? '跳过' : '失败'}`"
          :description="workflowResultDescription(healthResult.workflow)"
          show-icon
          :closable="false"
          class="health-result-alert"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.view-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.config-container {
  width: 100%;
  padding: 16px 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* Status Banner Styles */
.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-radius: 12px;
  border: 1px solid;
  transition: all 0.3s ease;
}

.status-banner--ready {
  background: rgba(16, 185, 129, 0.05);
  border-color: rgba(16, 185, 129, 0.2);
}

.status-banner--demo {
  background: rgba(245, 158, 11, 0.05);
  border-color: rgba(245, 158, 11, 0.2);
}

.status-banner__left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-icon {
  font-size: 24px;
}

.status-banner--ready .status-icon {
  color: #10b981;
}

.status-banner--demo .status-icon {
  color: #f59e0b;
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.status-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.status-desc {
  font-size: 12px;
  color: #64748b;
}

.result-tag {
  border-radius: 8px !important;
  font-weight: 600;
}

/* Form Styles */
.custom-config-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.section-card {
  border-radius: 16px !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.02) !important;
  border: 1px solid #e2e8f0 !important;
}

.section-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.section-card-header .el-icon {
  color: #4f46e5;
  font-size: 18px;
}

.custom-config-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #1e293b;
  padding-bottom: 8px !important;
  font-size: 13px;
}

.form-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  width: 100%;
}

/* Styled Inputs */
.custom-input :deep(.el-input__wrapper) {
  border-radius: 20px !important;
  padding: 6px 18px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  transition: all 0.3s ease !important;
}

.custom-textarea :deep(.el-textarea__inner) {
  border-radius: 16px !important;
  padding: 12px 18px !important;
  box-shadow: 0 0 0 1px #e2e8f0 inset !important;
  background-color: #f8fafc !important;
  transition: all 0.3s ease !important;
}

.custom-input :deep(.el-input__wrapper.is-focus),
.custom-textarea :deep(.el-textarea__inner:focus) {
  background-color: #fff !important;
  box-shadow: 0 0 0 1px #4f46e5 inset, 0 0 0 3px rgba(79, 70, 229, 0.15) !important;
}

.custom-input :deep(.el-input__inner) {
  font-size: 13px;
}

.token-field {
  width: 100%;
}

.token-change-btn {
  font-size: 12px !important;
  font-weight: 600 !important;
  color: #4f46e5 !important;
}

/* Switch card styles */
.switch-row-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  margin-top: 12px;
}

.switch-row-card__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.switch-row-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.switch-row-card__desc {
  font-size: 12px;
  color: #64748b;
}

/* Actions Row */
.form-actions-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
}

.btn-submit {
  border-radius: 20px !important;
  padding: 10px 24px !important;
  height: auto !important;
  background: linear-gradient(135deg, #4f46e5 0%, #3b82f6 100%) !important;
  border: none !important;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2) !important;
  font-weight: 600;
  font-size: 14px !important;
  transition: all 0.2s ease !important;
}

.btn-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.3) !important;
}

.btn-test {
  border-radius: 20px !important;
  padding: 10px 24px !important;
  height: auto !important;
  border: 1px solid #e2e8f0 !important;
  font-weight: 600;
  font-size: 14px !important;
  transition: all 0.2s ease !important;
}

.btn-test:hover {
  background-color: #f8fafc !important;
  border-color: #cbd5e1 !important;
}

.test-hint {
  font-size: 12px;
  color: #64748b;
}

.health-result-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.health-result-alert {
  border-radius: 12px !important;
}
</style>

