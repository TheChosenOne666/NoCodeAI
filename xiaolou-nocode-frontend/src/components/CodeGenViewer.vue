<template>
  <div class="code-gen-viewer">
    <div class="viewer-layout">
      <!-- 左侧文件树 -->
      <div class="file-tree-panel">
        <div class="panel-header">
          <span>文件</span>
          <span v-if="fileCount > 0" class="file-count">{{ fileCount }}</span>
        </div>
        <div class="file-tree-scroll">
          <FileTree
            :nodes="treeNodes"
            :active-path="activePath"
            :highlight-path="highlightPath"
            @select="handleFileSelect"
          />
        </div>
      </div>

      <!-- 右侧代码编辑区 -->
      <div class="code-panel">
        <div class="code-tabs">
          <div
            v-for="path in openTabs"
            :key="path"
            class="code-tab"
            :class="{ active: activePath === path }"
            @click="handleFileSelect(path)"
          >
            <FileOutlined class="tab-icon" />
            <span class="tab-label" :title="path">{{ getFileName(path) }}</span>
            <CloseOutlined class="tab-close" @click.stop="closeTab(path)" />
          </div>
        </div>

        <div class="code-editor-wrapper">
          <VueMonacoEditor
            v-if="activePath && activeModel"
            v-model:value="activeModel.content"
            :language="activeModel.language"
            :options="editorOptions"
            :theme="editorTheme"
            class="monaco-editor"
            @mount="handleEditorMount"
          />
          <div v-else class="editor-placeholder">
            <CodeOutlined class="placeholder-icon" />
            <p>正在生成代码...</p>
          </div>
        </div>

        <!-- 构建进度面板 -->
        <div v-if="isBuilding || buildMessages.length > 0" class="build-panel">
          <div class="build-header">
            <LoadingOutlined v-if="isBuilding" class="build-spinner" />
            <CheckCircleOutlined v-else class="build-success-icon" />
            <span>{{ isBuilding ? '正在构建项目...' : '构建完成' }}</span>
          </div>
          <div ref="buildLogRef" class="build-log">
            <div v-for="(msg, index) in buildMessages" :key="index" class="build-log-line">
              {{ msg }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, shallowRef } from 'vue'
import VueMonacoEditor from '@guolao/vue-monaco-editor'
import {
  FileOutlined,
  CloseOutlined,
  CodeOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons-vue'
import FileTree, { type FileTreeNode } from './FileTree.vue'

export interface CodeGenStreamEvent {
  type: string
  path?: string
  language?: string
  content?: string
  message?: string
  url?: string
}

interface FileModel {
  path: string
  content: string
  language: string
}

const props = defineProps<{
  isBuilding?: boolean
}>()

const files = ref<Record<string, FileModel>>({})
const openTabs = ref<string[]>([])
const activePath = ref('')
const highlightPath = ref('')
const buildMessages = ref<string[]>([])
const buildLogRef = ref<HTMLElement>()

interface MonacoEditorInstance {
  revealLineInCenter: (line: number) => void
}

const editorInstance = shallowRef<MonacoEditorInstance | null>(null)

const editorTheme = 'vs'

const editorOptions = {
  readOnly: true,
  automaticLayout: true,
  minimap: { enabled: false },
  scrollBeyondLastLine: false,
  fontSize: 13,
  lineNumbers: 'on',
  roundedSelection: false,
  renderLineHighlight: 'line',
  matchBrackets: 'near',
  wordWrap: 'on',
  scrollbar: {
    horizontal: 'hidden',
    vertical: 'auto',
  },
}

const fileCount = computed(() => Object.keys(files.value).length)

const activeModel = computed(() => {
  if (!activePath.value) return null
  return files.value[activePath.value] || null
})

const treeNodes = computed<FileTreeNode[]>(() => {
  const root: FileTreeNode = { name: 'root', path: '', type: 'folder', children: [] }
  Object.keys(files.value)
    .sort()
    .forEach((path) => {
      const parts = path.split('/').filter(Boolean)
      let current = root
      parts.forEach((part, index) => {
        const isLast = index === parts.length - 1
        const currentPath = parts.slice(0, index + 1).join('/')
        const existing = current.children?.find((n) => n.path === currentPath)
        if (existing) {
          current = existing
        } else {
          const newNode: FileTreeNode = {
            name: part,
            path: currentPath,
            type: isLast ? 'file' : 'folder',
            expanded: true,
            children: isLast ? undefined : [],
          }
          current.children?.push(newNode)
          current = newNode
        }
      })
    })
  return root.children || []
})

const getFileName = (path: string) => {
  return path.split('/').pop() || path
}

const handleFileSelect = (path: string) => {
  const model = files.value[path]
  if (!model) return
  activePath.value = path
  if (!openTabs.value.includes(path)) {
    openTabs.value.push(path)
  }
}

const closeTab = (path: string) => {
  const index = openTabs.value.indexOf(path)
  if (index === -1) return
  openTabs.value.splice(index, 1)
  if (activePath.value === path) {
    activePath.value = openTabs.value[Math.max(0, index - 1)] || ''
  }
}

const handleEditorMount = (editor: MonacoEditorInstance) => {
  editorInstance.value = editor
}

const scrollToEnd = () => {
  nextTick(() => {
    const model = activeModel.value
    if (!model || !editorInstance.value) return
    const lines = model.content.split('\n').length
    editorInstance.value.revealLineInCenter(lines)
  })
}

const scrollBuildLogToBottom = () => {
  nextTick(() => {
    if (buildLogRef.value) {
      buildLogRef.value.scrollTop = buildLogRef.value.scrollHeight
    }
  })
}

const handleStreamEvent = (event: CodeGenStreamEvent) => {
  switch (event.type) {
    case 'file-start': {
      if (!event.path) return
      highlightPath.value = event.path
      break
    }
    case 'code-chunk': {
      if (!event.path || event.content === undefined) return
      const existing = files.value[event.path]
      if (existing) {
        existing.content = event.content
      } else {
        files.value[event.path] = {
          path: event.path,
          content: event.content,
          language: event.language || detectLanguage(event.path),
        }
      }
      if (activePath.value === event.path) {
        scrollToEnd()
      }
      break
    }
    case 'file-end': {
      if (!event.path) return
      handleFileSelect(event.path)
      highlightPath.value = ''
      break
    }
    case 'build-start': {
      buildMessages.value = []
      break
    }
    case 'build-progress': {
      if (event.message) {
        buildMessages.value.push(event.message)
        scrollBuildLogToBottom()
      }
      break
    }
    case 'build-end': {
      scrollBuildLogToBottom()
      break
    }
    case 'preview-ready':
    case 'error':
      // 由父组件处理
      break
  }
}

const reset = () => {
  files.value = {}
  openTabs.value = []
  activePath.value = ''
  highlightPath.value = ''
  buildMessages.value = []
  editorInstance.value = null
}

/**
 * 加载已有文件列表（仅构建文件树，不填充内容）
 */
const loadFiles = (paths: string[]) => {
  paths.forEach((path) => {
    if (!files.value[path]) {
      files.value[path] = {
        path,
        content: '',
        language: detectLanguage(path),
      }
    }
  })
}

const detectLanguage = (path: string) => {
  const ext = path.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = {
    vue: 'vue',
    ts: 'typescript',
    tsx: 'typescript',
    js: 'javascript',
    jsx: 'javascript',
    html: 'html',
    htm: 'html',
    css: 'css',
    scss: 'scss',
    sass: 'scss',
    less: 'less',
    json: 'json',
    md: 'markdown',
    yml: 'yaml',
    yaml: 'yaml',
    xml: 'xml',
  }
  return map[ext] || 'plaintext'
}

watch(
  () => props.isBuilding,
  (newVal) => {
    if (newVal) {
      scrollBuildLogToBottom()
    }
  }
)

defineExpose({
  handleStreamEvent,
  reset,
  loadFiles,
  buildMessages,
})
</script>

<style scoped>
.code-gen-viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: #333333;
}

.viewer-layout {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.file-tree-panel {
  width: 220px;
  min-width: 180px;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}

.panel-header {
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: #666666;
  border-bottom: 1px solid #e8e8e8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.file-count {
  color: #999999;
  font-weight: normal;
}

.file-tree-scroll {
  flex: 1;
  overflow: auto;
  padding: 4px 0;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.file-tree-scroll::-webkit-scrollbar {
  display: none;
}

.code-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.code-tabs {
  height: 36px;
  display: flex;
  align-items: center;
  background: #f0f2f5;
  border-bottom: 1px solid #e8e8e8;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.code-tabs::-webkit-scrollbar {
  display: none;
}

.code-tab {
  height: 35px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: #f0f2f5;
  border-right: 1px solid #e8e8e8;
  cursor: pointer;
  font-size: 12px;
  color: #666666;
  transition: background 0.15s;
  min-width: 100px;
  max-width: 160px;
}

.code-tab:hover {
  background: #e8ecf1;
}

.code-tab.active {
  background: #ffffff;
  color: #333333;
  border-top: 2px solid #1677ff;
}

.tab-icon {
  font-size: 12px;
  margin-right: 6px;
  color: #999999;
}

.tab-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  font-size: 10px;
  margin-left: 8px;
  color: #999999;
  padding: 2px;
  border-radius: 3px;
}

.tab-close:hover {
  background: #e8e8e8;
  color: #333333;
}

.code-editor-wrapper {
  flex: 1;
  overflow: hidden;
  position: relative;
}

.monaco-editor {
  width: 100%;
  height: 100%;
}

.editor-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999999;
}

.placeholder-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.build-panel {
  height: 140px;
  min-height: 100px;
  border-top: 1px solid #e8e8e8;
  background: #fafafa;
  display: flex;
  flex-direction: column;
}

.build-header {
  height: 32px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  font-size: 12px;
  color: #666666;
  border-bottom: 1px solid #e8e8e8;
  background: #f5f5f5;
}

.build-spinner {
  margin-right: 8px;
  animation: spin 1s linear infinite;
}

.build-success-icon {
  margin-right: 8px;
  color: #52c41a;
}

.build-log {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #333333;
}

.build-log-line {
  white-space: pre-wrap;
  word-break: break-all;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
