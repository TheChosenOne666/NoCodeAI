<template>
  <div id="appChatPage">
    <!-- 顶部栏 -->
    <div class="header-bar">
      <div class="header-left">
        <h1 class="app-name">{{ appInfo?.appName || '网站生成器' }}</h1>
        <a-tag v-if="appInfo?.codeGenType" color="blue" class="code-gen-type-tag">
          {{ formatCodeGenType(appInfo.codeGenType) }}
        </a-tag>
      </div>
      <div class="header-right">
        <a-button type="default" @click="showAppDetail">
          <template #icon>
            <InfoCircleOutlined />
          </template>
          应用详情
        </a-button>
        <a-button
            type="primary"
            ghost
            @click="downloadCode"
            :loading="downloading"
            :disabled="!isOwner"
        >
          <template #icon>
            <DownloadOutlined />
          </template>
          下载代码
        </a-button>
        <a-button type="primary" @click="deployApp" :loading="deploying">
          <template #icon>
            <CloudUploadOutlined />
          </template>
          部署
        </a-button>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="main-content">
      <!-- 左侧对话区域 -->
      <div class="chat-section">
        <!-- 消息区域 -->
        <div class="messages-container" ref="messagesContainer">
          <!-- 加载更多按钮 -->
          <div v-if="hasMoreHistory" class="load-more-container">
            <a-button type="link" @click="loadMoreHistory" :loading="loadingHistory" size="small">
              加载更多历史消息
            </a-button>
          </div>
          <div v-for="(message, index) in messages" :key="index" class="message-item">
            <div v-if="message.type === 'user'" class="user-message">
              <div class="message-content">{{ message.content }}</div>
              <div class="message-avatar">
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
              </div>
            </div>
            <div v-else class="ai-message">
              <div class="message-avatar">
                <a-avatar :src="aiAvatar" />
              </div>
              <div class="message-content">
                <MarkdownRenderer v-if="message.content" :content="message.content" />
                <div v-if="message.loading" class="loading-indicator">
                  <a-spin size="small" />
                  <span>AI `  正在思考...</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 选中元素信息展示 -->
        <a-alert
            v-if="selectedElementInfo"
            class="selected-element-alert"
            type="info"
            closable
            @close="clearSelectedElement"
        >
          <template #message>
            <div class="selected-element-info">
              <div class="element-header">
                <span class="element-tag">
                  选中元素：{{ selectedElementInfo.tagName.toLowerCase() }}
                </span>
                <span v-if="selectedElementInfo.id" class="element-id">
                  #{{ selectedElementInfo.id }}
                </span>
                <span v-if="selectedElementInfo.className" class="element-class">
                  .{{ selectedElementInfo.className.split(' ').join('.') }}
                </span>
              </div>
              <div class="element-details">
                <div v-if="selectedElementInfo.textContent" class="element-item">
                  内容: {{ selectedElementInfo.textContent.substring(0, 50) }}
                  {{ selectedElementInfo.textContent.length > 50 ? '...' : '' }}
                </div>
                <div v-if="selectedElementInfo.pagePath" class="element-item">
                  页面路径: {{ selectedElementInfo.pagePath }}
                </div>
                <div class="element-item">
                  选择器:
                  <code class="element-selector-code">{{ selectedElementInfo.selector }}</code>
                </div>
              </div>
            </div>
          </template>
        </a-alert>

        <!-- 用户消息输入框 -->
        <div class="input-container">
          <div class="input-wrapper">
            <a-tooltip v-if="!isOwner" title="无法在别人的作品下对话哦~" placement="top">
              <a-textarea
                  v-model:value="userInput"
                  :placeholder="getInputPlaceholder()"
                  :rows="4"
                  :maxlength="1000"
                  @keydown.enter.prevent="sendMessage"
                  :disabled="isGenerating || !isOwner"
              />
            </a-tooltip>
            <a-textarea
                v-else
                v-model:value="userInput"
                :placeholder="getInputPlaceholder()"
                :rows="4"
                :maxlength="1000"
                @keydown.enter.prevent="sendMessage"
                :disabled="isGenerating"
            />
            <div class="input-actions">
              <a-button
                  v-if="!isGenerating"
                  type="primary"
                  @click="sendMessage"
                  :disabled="!isOwner"
              >
                <template #icon>
                  <SendOutlined />
                </template>
              </a-button>
              <a-button
                  v-else
                  type="primary"
                  danger
                  @click="stopGeneration"
              >
                <template #icon>
                  <StopOutlined />
                </template>
              </a-button>
            </div>
          </div>
        </div>
      </div>
      <!-- 右侧网页展示区域 -->
      <div class="preview-section">
        <div class="preview-header">
          <template v-if="isVueProject && vuePreviewReady">
            <div class="vue-preview-tabs">
              <div
                  class="vue-preview-tab"
                  :class="{ active: activeVueTab === 'preview' }"
                  @click="activeVueTab = 'preview'"
              >
                <EyeOutlined />
                <span>应用预览</span>
              </div>
              <div
                  class="vue-preview-tab"
                  :class="{ active: activeVueTab === 'code' }"
                  @click="activeVueTab = 'code'"
              >
                <CodeOutlined />
                <span>代码</span>
              </div>
            </div>
          </template>
          <template v-else>
            <h3>生成后的网页展示</h3>
          </template>
          <div class="preview-actions">
            <a-button
                v-if="isOwner && previewUrl"
                type="link"
                :danger="isEditMode"
                @click="toggleEditMode"
                :class="{ 'edit-mode-active': isEditMode }"
                style="padding: 0; height: auto; margin-right: 12px"
            >
              <template #icon>
                <EditOutlined />
              </template>
              {{ isEditMode ? '退出编辑' : '编辑模式' }}
            </a-button>
            <a-button v-if="previewUrl" type="link" @click="openInNewTab">
              <template #icon>
                <ExportOutlined />
              </template>
              新窗口打开
            </a-button>
          </div>
        </div>
        <div class="preview-content">
          <template v-if="isVueProject">
            <!-- Vue 项目：代码实时预览，生成阶段始终渲染以接收事件 -->
            <CodeGenViewer
                v-if="isGenerating || vuePreviewReady"
                v-show="!vuePreviewReady || activeVueTab === 'code'"
                ref="codeGenViewerRef"
                :is-building="isCodeGenBuilding"
            />
            <!-- Vue 项目：生成中动画，覆盖在代码实时预览上方 -->
            <GeneratingAnimation
                v-if="isGenerating && !hasReceivedFirstFileEvent"
                class="generating-overlay"
            />
            <!-- Vue 项目：预览 iframe -->
            <iframe
                v-if="vuePreviewReady && activeVueTab === 'preview'"
                :src="vuePreviewUrl"
                class="preview-iframe"
                frameborder="0"
                @load="onIframeLoad"
            ></iframe>
            <!-- Vue 项目：初始占位 -->
            <div v-if="!isGenerating && !vuePreviewReady" class="preview-placeholder">
              <div class="placeholder-icon">🌐</div>
              <p>网站文件生成完成后将在这里展示</p>
            </div>
          </template>
          <template v-else>
            <div v-if="!previewUrl && !isGenerating" class="preview-placeholder">
              <div class="placeholder-icon">🌐</div>
              <p>网站文件生成完成后将在这里展示</p>
            </div>
            <div v-else-if="isGenerating" class="preview-loading">
              <a-spin size="large" />
              <p>正在生成网站...</p>
            </div>
            <iframe
                v-else
                :src="previewUrl"
                class="preview-iframe"
                frameborder="0"
                @load="onIframeLoad"
            ></iframe>
          </template>
        </div>
      </div>
    </div>

    <!-- 应用详情弹窗 -->
    <AppDetailModal
        v-model:open="appDetailVisible"
        :app="appInfo"
        :show-actions="isOwner || isAdmin"
        @edit="editApp"
        @delete="deleteApp"
    />

    <!-- 部署成功弹窗 -->
    <DeploySuccessModal
        v-model:open="deployModalVisible"
        :deploy-url="deployUrl"
        @open-site="openDeployedSite"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onUnmounted, computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  getAppVoById,
  deployApp as deployAppApi,
  deleteApp as deleteAppApi,
} from '@/api/appController'
import { listAppChatHistory } from '@/api/chatHistoryController'
import { CodeGenTypeEnum, formatCodeGenType } from '@/utils/codeGenTypes'
import request from '@/request'

import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import DeploySuccessModal from '@/components/DeploySuccessModal.vue'
import GeneratingAnimation from '@/components/GeneratingAnimation.vue'
import { type CodeGenStreamEvent } from '@/components/CodeGenViewer.vue'
import aiAvatar from '@/assets/aiAvatar.png'
import { API_BASE_URL, getStaticPreviewUrl } from '@/config/env'
import { VisualEditor, type ElementInfo } from '@/utils/visualEditor'

import {
  CloudUploadOutlined,
  SendOutlined,
  StopOutlined,
  ExportOutlined,
  InfoCircleOutlined,
  DownloadOutlined,
  EditOutlined,
  EyeOutlined,
  CodeOutlined,
} from '@ant-design/icons-vue'

const CodeGenViewer = defineAsyncComponent(() => import('@/components/CodeGenViewer.vue'))

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()

// 应用信息
const appInfo = ref<API.AppVO>()
const appId = ref<any>()

// 对话相关
interface Message {
  type: 'user' | 'ai'
  content: string
  loading?: boolean
  createTime?: string
}

const messages = ref<Message[]>([])
const userInput = ref('')
const isGenerating = ref(false)
const currentRequestId = ref('')
const chatEventSource = ref<EventSource | null>(null)
const messagesContainer = ref<HTMLElement>()

// 对话历史相关
const loadingHistory = ref(false)
const hasMoreHistory = ref(false)
const lastCreateTime = ref<string>()
const historyLoaded = ref(false)

// 预览相关
const previewUrl = ref('')
const previewReady = ref(false)

// Vue 项目代码实时展示相关
const codeGenViewerRef = ref<InstanceType<typeof CodeGenViewer> | null>(null)
const codeGenStreamSource = ref<EventSource | null>(null)
const hasReceivedFirstFileEvent = ref(false)
const vuePreviewReady = ref(false)
const vuePreviewUrl = ref('')
const activeVueTab = ref<'preview' | 'code'>('preview')
const isCodeGenBuilding = ref(false)

const isVueProject = computed(() => appInfo.value?.codeGenType === CodeGenTypeEnum.VUE_PROJECT)

// 部署相关
const deploying = ref(false)
const deployModalVisible = ref(false)
const deployUrl = ref('')

// 下载相关
const downloading = ref(false)

// 可视化编辑相关
const isEditMode = ref(false)
const selectedElementInfo = ref<ElementInfo | null>(null)
const visualEditor = new VisualEditor({
  onElementSelected: (elementInfo: ElementInfo) => {
    selectedElementInfo.value = elementInfo
  },
})

// 权限相关
const isOwner = computed(() => {
  return appInfo.value?.userId === loginUserStore.loginUser.id
})

const isAdmin = computed(() => {
  return loginUserStore.loginUser.userRole === 'admin'
})

// 应用详情相关
const appDetailVisible = ref(false)

// 显示应用详情
const showAppDetail = () => {
  appDetailVisible.value = true
}

// 加载对话历史
const loadChatHistory = async (isLoadMore = false) => {
  if (!appId.value || loadingHistory.value) return
  loadingHistory.value = true
  try {
    const params: API.listAppChatHistoryParams = {
      appId: appId.value as any, // 保持为字符串类型，避免大数精度丢失
      pageSize: 10,
    }
    console.log('加载对话历史参数:', params)
    // 如果是加载更多，传递最后一条消息的创建时间作为游标
    if (isLoadMore && lastCreateTime.value) {
      params.lastCreateTime = lastCreateTime.value
    }
    const res = await listAppChatHistory(params)
    console.log('加载对话历史响应:', res)
    if (res.data.code === 0 && res.data.data) {
      const chatHistories = res.data.data.records || []
      console.log('对话历史记录:', chatHistories)
      if (chatHistories.length > 0) {
        // 将对话历史转换为消息格式，并按时间正序排列（老消息在前）
        const historyMessages: Message[] = chatHistories
            .map((chat) => ({
              type: (chat.messageType === 'user' ? 'user' : 'ai') as 'user' | 'ai',
              content: chat.message || '',
              createTime: chat.createTime,
            }))
            .reverse() // 反转数组，让老消息在前
        if (isLoadMore) {
          // 加载更多时，将历史消息添加到开头
          messages.value.unshift(...historyMessages)
        } else {
          // 初始加载，直接设置消息列表
          messages.value = historyMessages
        }
        // 更新游标
        lastCreateTime.value = chatHistories[chatHistories.length - 1]?.createTime
        // 检查是否还有更多历史
        hasMoreHistory.value = chatHistories.length === 10
      } else {
        hasMoreHistory.value = false
        console.log('没有找到对话历史记录')
      }
      historyLoaded.value = true
      // 滚动到最新消息位置
      nextTick(() => {
        scrollToBottom()
      })
    } else {
      console.log('加载对话历史失败，响应码:', res.data.code, '消息:', res.data.message)
    }
  } catch (error) {
    console.error('加载对话历史失败：', error)
    message.error('加载对话历史失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载更多历史消息
const loadMoreHistory = async () => {
  await loadChatHistory(true)
}

// 加载已生成的项目文件树
const loadProjectFiles = async () => {
  if (!appId.value || !isVueProject.value) return
  try {
    const res = await request.get(`/app/${appId.value}/files`)
    if (res.data.code === 0 && Array.isArray(res.data.data)) {
      const files = res.data.data as string[]
      if (files.length > 0) {
        codeGenViewerRef.value?.loadFiles(files)
        console.log('已加载已有文件树，文件数:', files.length)
      }
    }
  } catch (error) {
    console.error('加载项目文件树失败:', error)
  }
}

// 获取应用信息
const fetchAppInfo = async () => {
  const id = route.params.id as string
  if (!id) {
    message.error('应用ID不存在')
    router.push('/')
    return
  }

  appId.value = id

  try {
    const res = await getAppVoById({ id: id as unknown as number })
    if (res.data.code === 0 && res.data.data) {
      appInfo.value = res.data.data
      console.log('应用信息:', appInfo.value)

      // 先加载对话历史
      await loadChatHistory()
      console.log('加载对话历史后，消息数量:', messages.value.length)

      // 加载已生成的文件树（如果有）
      await loadProjectFiles()

      // 如果有对话记录，直接展示对应的网站
      if (messages.value.length > 0) {
        await updatePreview()
        console.log('已更新预览URL:', previewUrl.value)
      } else if (appInfo.value.deployKey) {
        // 如果没有对话历史但应用已部署过，也显示预览
        await updatePreview()
        console.log('应用已部署，已更新预览URL:', previewUrl.value)
      } else {
        // 检查是否有初始提示词（从路由参数获取）
        const initialPrompt = route.query.initialPrompt as string
        if (initialPrompt && isOwner.value) {
          console.log('检测到初始提示词，自动发送:', initialPrompt)
          await sendInitialMessage(initialPrompt)
        }
      }
    } else {
      message.error('获取应用信息失败')
      router.push('/')
    }
  } catch (error) {
    console.error('获取应用信息失败：', error)
    message.error('获取应用信息失败')
    router.push('/')
  }
}

// 发送初始消息
const sendInitialMessage = async (prompt: string) => {
  // 添加用户消息
  messages.value.push({
    type: 'user',
    content: prompt,
  })

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  isGenerating.value = true
  const requestId = crypto.randomUUID()
  currentRequestId.value = requestId
  if (isVueProject.value) {
    startVueProjectStreamDetail(prompt, requestId)
  }
  await generateCode(prompt, aiMessageIndex, requestId)
}

// 发送消息
const sendMessage = async () => {
  const rawInput = userInput.value.trim()
  if (!rawInput || isGenerating.value) {
    return
  }

  // 立即锁定输入并清空，防止重复发送
  isGenerating.value = true
  userInput.value = ''
  await nextTick()

  let message = rawInput
  // 如果有选中的元素，将元素信息添加到提示词中
  if (selectedElementInfo.value) {
    let elementContext = `\n\n选中元素信息：`
    if (selectedElementInfo.value.pagePath) {
      elementContext += `\n- 页面路径: ${selectedElementInfo.value.pagePath}`
    }
    elementContext += `\n- 标签: ${selectedElementInfo.value.tagName.toLowerCase()}\n- 选择器: ${selectedElementInfo.value.selector}`
    if (selectedElementInfo.value.textContent) {
      elementContext += `\n- 当前内容: ${selectedElementInfo.value.textContent.substring(0, 100)}`
    }
    message += elementContext
  }

  // 添加用户消息（包含元素信息）
  messages.value.push({
    type: 'user',
    content: message,
  })

  // 发送消息后，清除选中元素并退出编辑模式
  if (selectedElementInfo.value) {
    clearSelectedElement()
    if (isEditMode.value) {
      toggleEditMode()
    }
  }

  // 添加AI消息占位符
  const aiMessageIndex = messages.value.length
  messages.value.push({
    type: 'ai',
    content: '',
    loading: true,
  })

  await nextTick()
  scrollToBottom()

  // 开始生成
  const requestId = crypto.randomUUID()
  currentRequestId.value = requestId
  if (isVueProject.value) {
    startVueProjectStreamDetail(message, requestId)
  }
  await generateCode(message, aiMessageIndex, requestId)
}

// 停止生成
const stopGeneration = async () => {
  if (!isGenerating.value) {
    return
  }

  // 关闭 SSE 连接
  closeCodeGenStream()
  if (chatEventSource.value) {
    chatEventSource.value.close()
    chatEventSource.value = null
  }

  // 通知后端停止当前 requestId 的生成会话
  if (currentRequestId.value) {
    try {
      const baseURL = request.defaults.baseURL || API_BASE_URL
      await fetch(`${baseURL}/app/gen/stop?requestId=${encodeURIComponent(currentRequestId.value)}`, {
        method: 'POST',
        credentials: 'include',
      })
    } catch (error) {
      console.error('停止生成请求失败:', error)
    }
  }

  isGenerating.value = false
}

// 生成代码 - 使用 EventSource 处理流式响应
const generateCode = async (userMessage: string, aiMessageIndex: number, requestId?: string) => {
  const trimmedMessage = userMessage?.trim() || ''
  if (!trimmedMessage) {
    messages.value[aiMessageIndex].content = '抱歉，消息内容不能为空，请重新输入。'
    messages.value[aiMessageIndex].loading = false
    isGenerating.value = false
    return
  }

  let eventSource: EventSource | null = null
  let streamCompleted = false

  try {
    // 获取 axios 配置的 baseURL
    const baseURL = request.defaults.baseURL || API_BASE_URL

    // 手动编码 URL 参数，避免特殊字符导致参数解析异常
    const queryParams = new URLSearchParams()
    queryParams.set('appId', String(appId.value || ''))
    queryParams.set('message', trimmedMessage)
    if (requestId) {
      queryParams.set('requestId', requestId)
    }

    const url = `${baseURL}/app/chat/gen/code?${queryParams.toString()}`
    console.log('生成代码请求 URL:', url.substring(0, 200) + (url.length > 200 ? '...' : ''))

    // 创建 EventSource 连接
    eventSource = new EventSource(url, {
      withCredentials: true,
    })
    chatEventSource.value = eventSource

    let fullContent = ''

    // 处理接收到的消息
    eventSource.onmessage = function (event) {
      if (streamCompleted) return

      try {
        // 解析JSON包装的数据（后端字段名为 data）
        const parsed = JSON.parse(event.data)
        const content = parsed.data

        // 拼接内容
        if (content !== undefined && content !== null) {
          fullContent += content
          messages.value[aiMessageIndex].content = fullContent
          messages.value[aiMessageIndex].loading = false
          scrollToBottom()
        }
      } catch (error) {
        console.error('解析消息失败:', error)
        handleError(error, aiMessageIndex)
      }
    }

    // 处理done事件
    eventSource.addEventListener('done', function () {
      if (streamCompleted) return

      streamCompleted = true
      // Vue 项目的生成状态由代码实时展示流事件控制
      if (!isVueProject.value) {
        isGenerating.value = false
      }
      eventSource?.close()
      chatEventSource.value = null

      // 延迟更新预览，确保后端已完成处理
      setTimeout(async () => {
        await fetchAppInfo()
        await updatePreview()
      }, 1000)
    })

    // 处理business-error事件（后端限流等错误）
    eventSource.addEventListener('business-error', function (event: MessageEvent) {
      if (streamCompleted) return

      try {
        const errorData = JSON.parse(event.data)
        console.error('SSE业务错误事件:', errorData)

        // 显示具体的错误信息
        const errorMessage = errorData.message || '生成过程中出现错误'
        messages.value[aiMessageIndex].content = `❌ ${errorMessage}`
        messages.value[aiMessageIndex].loading = false
        message.error(errorMessage)

        streamCompleted = true
        isGenerating.value = false
        eventSource?.close()
      } catch (parseError) {
        console.error('解析错误事件失败:', parseError, '原始数据:', event.data)
        handleError(new Error('服务器返回错误'), aiMessageIndex)
      }
    })

    // 处理错误
    eventSource.onerror = function () {
      if (streamCompleted || !isGenerating.value) return
      // EventSource 触发 onerror 即视为连接异常（正常结束由 done 事件处理，不会触发 onerror）
      eventSource?.close()
      chatEventSource.value = null
      handleError(new Error('SSE连接错误'), aiMessageIndex)
    }
  } catch (error) {
    console.error('创建 EventSource 失败：', error)
    handleError(error, aiMessageIndex)
  }
}

// 错误处理函数
const handleError = (error: unknown, aiMessageIndex: number) => {
  console.error('生成代码失败：', error)
  messages.value[aiMessageIndex].content = '抱歉，生成过程中出现了错误，请重试。'
  messages.value[aiMessageIndex].loading = false
  message.error('生成失败，请重试')
  isGenerating.value = false
}

// 启动 Vue 项目代码实时展示 SSE
const startVueProjectStreamDetail = (userMessage: string, requestId?: string) => {
  const trimmedMessage = userMessage?.trim() || ''
  if (!trimmedMessage) {
    console.warn('拒绝启动空的 Vue 项目代码实时展示流')
    return
  }

  // 关闭旧的连接
  closeCodeGenStream()

  // 重置 Vue 项目展示状态
  hasReceivedFirstFileEvent.value = false
  vuePreviewReady.value = false
  vuePreviewUrl.value = ''
  activeVueTab.value = 'preview'
  isCodeGenBuilding.value = false
  codeGenViewerRef.value?.reset()

  try {
    const baseURL = request.defaults.baseURL || API_BASE_URL
    const params = new URLSearchParams()
    params.set('message', trimmedMessage)
    if (requestId) {
      params.set('requestId', requestId)
    }
    const url = `${baseURL}/app/gen/stream/${appId.value}?${params.toString()}`

    const eventSource = new EventSource(url, {
      withCredentials: true,
    })
    codeGenStreamSource.value = eventSource

    eventSource.onmessage = (event) => {
      try {
        const streamEvent: CodeGenStreamEvent = JSON.parse(event.data)
        handleCodeGenStreamEvent(streamEvent)
      } catch (error) {
        console.error('解析代码实时展示事件失败:', error, event.data)
      }
    }

    eventSource.onerror = () => {
      console.error('Vue 项目代码实时展示 SSE 连接错误')
      eventSource.close()
      codeGenStreamSource.value = null
      isCodeGenBuilding.value = false
    }
  } catch (error) {
    console.error('创建 Vue 项目代码实时展示 SSE 失败:', error)
  }
}

// 处理 Vue 项目代码实时展示事件
const handleCodeGenStreamEvent = (streamEvent: CodeGenStreamEvent) => {
  // 转发给 CodeGenViewer 处理文件/代码事件
  codeGenViewerRef.value?.handleStreamEvent(streamEvent)

  switch (streamEvent.type) {
    case 'file-start':
      if (!hasReceivedFirstFileEvent.value) {
        hasReceivedFirstFileEvent.value = true
      }
      break
    case 'build-start':
      isCodeGenBuilding.value = true
      break
    case 'build-end':
      isCodeGenBuilding.value = false
      break
    case 'preview-ready':
      if (streamEvent.url) {
        // 将相对路径转为完整 URL，并加时间戳避免缓存
        const previewFullUrl = streamEvent.url.startsWith('http')
          ? streamEvent.url
          : `${API_BASE_URL}${streamEvent.url.replace(/^\/api/, '')}`
        const previewUrlWithCache = `${previewFullUrl}?t=${Date.now()}`
        vuePreviewUrl.value = previewUrlWithCache
        vuePreviewReady.value = true
        activeVueTab.value = 'preview'
        // 同时更新普通预览 URL，保持一致
        previewUrl.value = previewFullUrl
        previewReady.value = true
      }
      // 构建完成并可以预览，释放生成状态
      isGenerating.value = false
      break
    case 'error':
      isCodeGenBuilding.value = false
      isGenerating.value = false
      message.error(streamEvent.message || '生成失败')
      break
  }
}

// 关闭 Vue 项目代码实时展示 SSE
const closeCodeGenStream = () => {
  if (codeGenStreamSource.value) {
    codeGenStreamSource.value.close()
    codeGenStreamSource.value = null
  }
}

// 更新预览
const updatePreview = async () => {
  if (!appId.value) {
    return
  }
  const codeGenType = appInfo.value?.codeGenType || CodeGenTypeEnum.HTML
  const newPreviewUrl = getStaticPreviewUrl(codeGenType, appId.value)
  previewUrl.value = newPreviewUrl
  previewReady.value = true

  // Vue 项目需要同时设置右侧预览状态
  if (codeGenType === CodeGenTypeEnum.VUE_PROJECT) {
    // 先确认 dist/index.html 真的存在，避免显示 404
    try {
      const res = await fetch(newPreviewUrl, {
        method: 'HEAD',
        credentials: 'include',
      })
      if (res.ok) {
        vuePreviewUrl.value = `${newPreviewUrl}?t=${Date.now()}`
        vuePreviewReady.value = true
      }
    } catch (error) {
      console.log('Vue 预览文件尚未生成:', error)
    }
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// 下载代码
const downloadCode = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }
  downloading.value = true
  try {
    const API_BASE_URL = request.defaults.baseURL || ''
    const url = `${API_BASE_URL}/app/download/${appId.value}`
    const response = await fetch(url, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`下载失败: ${response.status}`)
    }
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition')
    const fileName = contentDisposition?.match(/filename="(.+)"/)?.[1] || `app-${appId.value}.zip`
    // 下载文件
    const blob = await response.blob()
    const downloadUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    link.click()
    // 清理
    URL.revokeObjectURL(downloadUrl)
    message.success('代码下载成功')
  } catch (error) {
    console.error('下载失败：', error)
    message.error('下载失败，请重试')
  } finally {
    downloading.value = false
  }
}

// 部署应用
const deployApp = async () => {
  if (!appId.value) {
    message.error('应用ID不存在')
    return
  }

  deploying.value = true
  try {
    const res = await deployAppApi({
      appId: appId.value as unknown as number,
    })

    if (res.data.code === 0 && res.data.data) {
      deployUrl.value = res.data.data
      deployModalVisible.value = true
      message.success('部署成功')
    } else {
      message.error('部署失败：' + res.data.message)
    }
  } catch (error) {
    console.error('部署失败：', error)
    message.error('部署失败，请重试')
  } finally {
    deploying.value = false
  }
}

// 在新窗口打开预览
const openInNewTab = () => {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

// 打开部署的网站
const openDeployedSite = () => {
  if (deployUrl.value) {
    window.open(deployUrl.value, '_blank')
  }
}

// iframe加载完成
const onIframeLoad = () => {
  previewReady.value = true
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (iframe) {
    visualEditor.init(iframe)
    visualEditor.onIframeLoad()
  }
}

// 编辑应用
const editApp = () => {
  if (appInfo.value?.id) {
    router.push(`/app/edit/${appInfo.value.id}`)
  }
}

// 删除应用
const deleteApp = async () => {
  if (!appInfo.value?.id) return

  try {
    const res = await deleteAppApi({ id: appInfo.value.id })
    if (res.data.code === 0) {
      message.success('删除成功')
      appDetailVisible.value = false
      router.push('/')
    } else {
      message.error('删除失败：' + res.data.message)
    }
  } catch (error) {
    console.error('删除失败：', error)
    message.error('删除失败')
  }
}

// 可视化编辑相关函数
const toggleEditMode = () => {
  // 检查 iframe 是否已经加载
  const iframe = document.querySelector('.preview-iframe') as HTMLIFrameElement
  if (!iframe) {
    message.warning('请等待页面加载完成')
    return
  }
  // 确保 visualEditor 已初始化
  if (!previewReady.value) {
    message.warning('请等待页面加载完成')
    return
  }
  const newEditMode = visualEditor.toggleEditMode()
  isEditMode.value = newEditMode
}

const clearSelectedElement = () => {
  selectedElementInfo.value = null
  visualEditor.clearSelection()
}

const getInputPlaceholder = () => {
  if (selectedElementInfo.value) {
    return `正在编辑 ${selectedElementInfo.value.tagName.toLowerCase()} 元素，描述您想要的修改...`
  }
  return '请描述你想生成的网站，越详细效果越好哦'
}

// 页面离开/关闭时通知后端停止生成
const stopGenOnLeave = () => {
  if (!currentRequestId.value || !isGenerating.value) return
  const baseURL = request.defaults.baseURL || ''
  const url = `${baseURL}/app/gen/stop?requestId=${encodeURIComponent(currentRequestId.value)}`
  if (navigator.sendBeacon) {
    navigator.sendBeacon(url)
  } else {
    try {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', url, false)
      xhr.send()
    } catch (e) {
      console.error('页面离开时停止生成失败:', e)
    }
  }
}

const handleVisibilityChange = () => {
  // 页面隐藏超过 30 秒且仍在生成，则主动停止（可选的兜底策略）
  if (document.hidden && isGenerating.value) {
    console.log('页面隐藏，继续保持生成监听')
  }
}

// 页面加载时获取应用信息
onMounted(() => {
  fetchAppInfo()

  // 监听 iframe 消息
  window.addEventListener('message', (event) => {
    visualEditor.handleIframeMessage(event)
  })

  // 页面关闭/刷新/隐藏前停止生成
  window.addEventListener('beforeunload', stopGenOnLeave)
  window.addEventListener('pagehide', stopGenOnLeave)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

// 清理资源
onUnmounted(() => {
  closeCodeGenStream()
  stopGenOnLeave()
  window.removeEventListener('beforeunload', stopGenOnLeave)
  window.removeEventListener('pagehide', stopGenOnLeave)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
#appChatPage {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: #fdfdfd;
}

/* 顶部栏 */
.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.code-gen-type-tag {
  font-size: 12px;
}

.app-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-right {
  display: flex;
  gap: 12px;
}

/* 主要内容区域 */
.main-content {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
}

/* 左侧对话区域 */
.chat-section {
  flex: 2;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.messages-container {
  flex: 0.9;
  padding: 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
}

.message-item {
  margin-bottom: 12px;
}

.user-message {
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
}

.ai-message {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  gap: 8px;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 12px;
  line-height: 1.5;
  word-wrap: break-word;
}

.user-message .message-content {
  background: #1890ff;
  color: white;
}

.ai-message .message-content {
  background: #f5f5f5;
  color: #1a1a1a;
  padding: 8px 12px;
}

.message-avatar {
  flex-shrink: 0;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #666;
}

/* 加载更多按钮 */
.load-more-container {
  text-align: center;
  padding: 8px 0;
  margin-bottom: 16px;
}

/* 输入区域 */
.input-container {
  padding: 16px;
  background: white;
}

.input-wrapper {
  position: relative;
}

.input-wrapper .ant-input {
  padding-right: 50px;
}

.input-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}

/* 右侧预览区域 */
.preview-section {
  flex: 3;
  display: flex;
  flex-direction: column;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e8e8e8;
}

.preview-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.vue-preview-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
}

.vue-preview-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  transition: all 0.2s;
}

.vue-preview-tab:hover {
  background: #f5f5f5;
}

.vue-preview-tab.active {
  background: #1890ff;
  color: white;
}

.generating-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  z-index: 10;
}

.preview-actions {
  display: flex;
  gap: 8px;
}

.preview-content {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #666;
}

.preview-loading p {
  margin-top: 16px;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.selected-element-alert {
  margin: 0 16px;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .main-content {
    flex-direction: column;
  }

  .chat-section,
  .preview-section {
    flex: none;
    height: 50vh;
  }
}

@media (max-width: 768px) {
  .header-bar {
    padding: 12px 16px;
  }

  .app-name {
    font-size: 16px;
  }

  .main-content {
    padding: 8px;
    gap: 8px;
  }

  .message-content {
    max-width: 85%;
  }

  /* 选中元素信息样式 */
  .selected-element-alert {
    margin: 0 16px;
  }

  .selected-element-info {
    line-height: 1.4;
  }

  .element-header {
    margin-bottom: 8px;
  }

  .element-details {
    margin-top: 8px;
  }

  .element-item {
    margin-bottom: 4px;
    font-size: 13px;
  }

  .element-item:last-child {
    margin-bottom: 0;
  }

  .element-tag {
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 14px;
    font-weight: 600;
    color: #007bff;
  }

  .element-id {
    color: #28a745;
    margin-left: 4px;
  }

  .element-class {
    color: #ffc107;
    margin-left: 4px;
  }

  .element-selector-code {
    font-family: 'Monaco', 'Menlo', monospace;
    background: #f6f8fa;
    padding: 2px 4px;
    border-radius: 3px;
    font-size: 12px;
    color: #d73a49;
    border: 1px solid #e1e4e8;
  }

  /* 编辑模式按钮样式 */
  .edit-mode-active {
    background-color: #52c41a !important;
    border-color: #52c41a !important;
    color: white !important;
  }

  .edit-mode-active:hover {
    background-color: #73d13d !important;
    border-color: #73d13d !important;
  }
}
</style>
