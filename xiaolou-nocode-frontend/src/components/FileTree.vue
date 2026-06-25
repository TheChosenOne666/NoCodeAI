<template>
  <div class="file-tree">
    <div
      v-for="node in treeNodes"
      :key="node.path"
      class="file-tree-node"
    >
      <div
        class="file-tree-item"
        :class="{
          'is-folder': node.type === 'folder',
          'is-file': node.type === 'file',
          'is-active': activePath === node.path,
          'is-highlight': highlightPath === node.path
        }"
        :style="{ paddingLeft: `${level * 16 + 8}px` }"
        @click="handleNodeClick(node)"
      >
        <span v-if="node.type === 'folder'" class="folder-toggle">
          <CaretRightOutlined v-if="!node.expanded" />
          <CaretDownOutlined v-else />
        </span>
        <span v-else class="file-icon-placeholder"></span>
        <span class="item-icon">
          <FolderOutlined v-if="node.type === 'folder'" />
          <FileOutlined v-else />
        </span>
        <span class="item-label" :title="node.name">{{ node.name }}</span>
      </div>
      <FileTree
        v-if="node.type === 'folder' && node.expanded"
        :nodes="node.children || []"
        :level="level + 1"
        :active-path="activePath"
        :highlight-path="highlightPath"
        @select="handleChildSelect"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  FolderOutlined,
  FileOutlined,
  CaretRightOutlined,
  CaretDownOutlined,
} from '@ant-design/icons-vue'

export interface FileTreeNode {
  name: string
  path: string
  type: 'file' | 'folder'
  expanded?: boolean
  children?: FileTreeNode[]
}

interface Props {
  nodes: FileTreeNode[]
  level?: number
  activePath?: string
  highlightPath?: string
}

const props = withDefaults(defineProps<Props>(), {
  level: 0,
  activePath: '',
  highlightPath: '',
})

const emit = defineEmits<{
  (e: 'select', path: string): void
}>()

const treeNodes = computed(() => props.nodes)

const handleNodeClick = (node: FileTreeNode) => {
  if (node.type === 'folder') {
    node.expanded = !node.expanded
  }
  emit('select', node.path)
}

const handleChildSelect = (path: string) => {
  emit('select', path)
}
</script>

<style scoped>
.file-tree {
  font-size: 13px;
  user-select: none;
}

.file-tree-node {
  line-height: 1.4;
}

.file-tree-item {
  display: flex;
  align-items: center;
  height: 28px;
  cursor: pointer;
  color: #333333;
  transition: background 0.15s;
}

.file-tree-item:hover {
  background: #e8ecf1;
}

.file-tree-item.is-active {
  background: #e6f4ff;
  color: #1677ff;
}

.file-tree-item.is-highlight {
  animation: highlight-pulse 1s ease;
}

.folder-toggle {
  width: 14px;
  height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #999999;
  margin-right: 2px;
}

.file-icon-placeholder {
  width: 14px;
  margin-right: 2px;
}

.item-icon {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 6px;
  font-size: 14px;
  color: #999999;
}

.file-tree-item.is-active .item-icon {
  color: #1677ff;
}

.item-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes highlight-pulse {
  0% {
    background: transparent;
  }
  50% {
    background: #bae0ff;
  }
  100% {
    background: transparent;
  }
}
</style>
